package com.dynamicai.launcher.macro

import com.dynamicai.launcher.ui.theme.AppTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Шаблон кроку: {0}, {1} у template замінюються значеннями полів. */
private data class StepSpec(val label: String, val template: String, val params: List<Pair<String, String>> = emptyList(), val desc: String = "")

private val STEP_CATALOG: List<Pair<String, List<StepSpec>>> = listOf(
    "Мережа" to listOf(
        StepSpec("Wi-Fi увімкнути", "toggleWifi(state=\"on\")"),
        StepSpec("Wi-Fi вимкнути", "toggleWifi(state=\"off\")"),
        StepSpec("Bluetooth увімкнути", "toggleBluetooth(state=\"on\")"),
        StepSpec("Bluetooth вимкнути", "toggleBluetooth(state=\"off\")"),
        StepSpec("Підключити Bluetooth-пристрій", "connectBluetooth(name=\"{0}\")", listOf("Назва пристрою (порожньо — авто з налаштувань)" to "")),
        StepSpec("Є інтернет (вимкнути політ, увімкнути дані)", "ensureInternet()")
    ),
    "Додатки" to listOf(
        StepSpec("Відкрити додаток", "openApp(name=\"{0}\")", listOf("Назва додатка" to "")),
        StepSpec("Закрити / на головний", "closeApp()"),
        StepSpec("YouTube Music: грати", "playYouTubeMusic()"),
        StepSpec("Пошук на YouTube", "playYouTube(query=\"{0}\")", listOf("Запит" to "")),
        StepSpec("Відкрити посилання", "openUrl(url=\"{0}\")", listOf("URL" to "https://")),
        StepSpec("Маршрут", "navigateTo(destination=\"{0}\")", listOf("Куди" to "")),
        StepSpec("Камера", "openCamera(mode=\"photo\")"),
        StepSpec("HTTP-запит (GET)", "httpGet(url=\"{0}\")", listOf("URL" to "https://"))
    ),
    "Медіа" to listOf(
        StepSpec("Плеєр: Play", "mediaKey(key=\"play\")"),
        StepSpec("Плеєр: пауза", "mediaKey(key=\"pause\")"),
        StepSpec("Плеєр: наступний", "mediaKey(key=\"next\")"),
        StepSpec("Плеєр: попередній", "mediaKey(key=\"prev\")"),
        StepSpec("Гучність, %", "setVolume(level=\"{0}\")", listOf("Рівень 0–100" to "60"))
    ),
    "Пристрій" to listOf(
        StepSpec("Ліхтарик увімкнути", "toggleFlashlight(state=\"on\")"),
        StepSpec("Ліхтарик вимкнути", "toggleFlashlight(state=\"off\")"),
        StepSpec("Яскравість, %", "setBrightness(percent=\"{0}\")", listOf("Рівень 0–100" to "50")),
        StepSpec("Не турбувати: увімкнути", "setDnd(state=\"on\")"),
        StepSpec("Не турбувати: вимкнути", "setDnd(state=\"off\")"),
        StepSpec("Режим дзвінка", "setRingerMode(mode=\"{0}\")", listOf("normal, vibrate або silent" to "vibrate")),
        StepSpec("Мобільні дані: увімкнути", "setMobileData(state=\"on\")"),
        StepSpec("Мобільні дані: вимкнути", "setMobileData(state=\"off\")"),
        StepSpec("Режим польоту: увімкнути", "setAirplane(state=\"on\")"),
        StepSpec("Режим польоту: вимкнути", "setAirplane(state=\"off\")"),
        StepSpec("Вібрація", "vibrate(ms=\"{0}\")", listOf("Мілісекунд" to "400")),
        StepSpec("Сповіщення", "notify(title=\"{0}\", text=\"{1}\")", listOf("Заголовок" to "DynamicAI", "Текст" to "")),
        StepSpec("Озвучити текст", "speak(text=\"{0}\")", listOf("Текст (можна {time}, {battery}, {last})" to "")),
        StepSpec("Shell-команда (Shizuku)", "shell(cmd=\"{0}\")", listOf("Команда" to "")),
        StepSpec("Будильник", "setAlarm(hour=\"{0}\", minute=\"{1}\", message=\"{2}\")", listOf("Година" to "7", "Хвилини" to "0", "Підпис" to "Будильник")),
        StepSpec("Нотатка", "takeNote(text=\"{0}\")", listOf("Текст" to "")),
        StepSpec("Системна дія", "systemAction(action=\"{0}\")", listOf("home, back, recents, notifications, lock, screenshot" to "home")),
        StepSpec("Натиснути текст на екрані", "tapText(text=\"{0}\")", listOf("Текст кнопки" to "")),
        StepSpec("Ввести текст", "typeText(text=\"{0}\")", listOf("Текст" to ""))
    ),
    "Інфо" to listOf(
        StepSpec("Погода", "getWeather(city=\"{0}\")", listOf("Місто (порожньо — авто)" to "")),
        StepSpec("Новини", "getNews()"),
        StepSpec("Час і дата", "getTime()"),
        StepSpec("Заряд батареї", "getBattery()"),
        StepSpec("Пошук в інтернеті", "searchWeb(query=\"{0}\")", listOf("Запит" to ""))
    ),
    "Логіка" to listOf(
        StepSpec("⏱ Пауза (секунд)", "WAIT: {0}", listOf("Секунд" to "2")),
        StepSpec("⌛ Чекати умову", "WAIT_UNTIL: {0} {1}", listOf("Умова: ${MacroRunner.CONDITIONS}" to "online", "Макс. секунд" to "15")),
        StepSpec("❓ Якщо умова — виконати крок", "IF: {0} ? {1}", listOf("Умова: ${MacroRunner.CONDITIONS}" to "online", "Крок" to "playYouTubeMusic()")),
        StepSpec("❓ Якщо НЕ умова — виконати крок", "IF_NOT: {0} ? {1}", listOf("Умова: ${MacroRunner.CONDITIONS}" to "online", "Крок" to "ensureInternet()")),
        StepSpec("↔ Якщо умова — A, інакше — B", "IF: {0} ? {1} || {2}", listOf("Умова: ${MacroRunner.CONDITIONS}" to "charging", "Крок A" to "SAY: Заряджається", "Крок B" to "SAY: Не заряджається")),
        StepSpec("🔁 Повторити N разів", "REPEAT: {0} ? {1}", listOf("Разів" to "3", "Крок" to "vibrate(ms=\"300\")")),
        StepSpec("📦 Змінна (SET)", "SET: {0}={1}", listOf("Ім'я" to "city", "Значення" to "Київ")),
        StepSpec("💬 Повідомлення", "SAY: {0}", listOf("Текст (можна {time}, {battery}, {last}, {device})" to "")),
        StepSpec("Запустити інший макрос", "runMacro(name=\"{0}\")", listOf("Назва макросу" to ""))
    ),
    "Сценарії" to listOf(
        StepSpec("🚗 Музика в авто (усе разом)", "carMode()")
    )
)

private val TRIGGER_GROUPS: List<Pair<String, List<StepSpec>>> = listOf(
    "🔋 Живлення" to listOf(
        StepSpec("🔌 Зарядку підключено", "charging_start", desc = "Коли ви вставили зарядний кабель або поставили на бездротову зарядку"),
        StepSpec("🔌 Зарядку відключено", "charging_stop", desc = "Коли зарядку відключили"),
        StepSpec("🪫 Батарея нижче N %", "battery_low:{0}", listOf("Відсоток" to "20"), "Спрацює один раз, коли заряд опуститься нижче вказаного рівня")
    ),
    "📡 Зв'язок" to listOf(
        StepSpec("🔵 Bluetooth підключився", "bt_connected:{0}", listOf("Назва пристрою (порожньо — будь-який)" to ""), "Коли телефон з'єднався з Bluetooth-пристроєм: авто, колонка, навушники"),
        StepSpec("🔵 Bluetooth відключився", "bt_disconnected:{0}", listOf("Назва пристрою (порожньо — будь-який)" to ""), "Коли з'єднання з пристроєм обірвалось"),
        StepSpec("📶 Wi-Fi підключено", "wifi_connected", desc = "Коли телефон підключився до Wi-Fi"),
        StepSpec("📶 Wi-Fi відключено", "wifi_disconnected", desc = "Коли Wi-Fi зник або вимкнувся"),
        StepSpec("✈ Режим польоту увімкнено", "airplane_on", desc = "Коли ввімкнули режим польоту"),
        StepSpec("✈ Режим польоту вимкнено", "airplane_off", desc = "Коли вимкнули режим польоту")
    ),
    "📱 Пристрій" to listOf(
        StepSpec("🎧 Навушники підключено", "headset_plugged", desc = "Дротові або USB-навушники вставлено"),
        StepSpec("🎧 Навушники відключено", "headset_unplugged", desc = "Дротові або USB-навушники вийнято"),
        StepSpec("📱 Екран увімкнено", "screen_on", desc = "Коли засвітився екран"),
        StepSpec("📱 Екран вимкнено", "screen_off", desc = "Коли екран погас"),
        StepSpec("🔓 Телефон розблоковано", "unlock", desc = "Після розблокування екрана")
    ),
    "⏰ Час і система" to listOf(
        StepSpec("⏰ За часом", "time:{0}:{1}:{2}", listOf("Година" to "7", "Хвилини" to "30", "Дні 1=Пн…7=Нд (порожньо — щодня)" to "12345"), "Щодня або в обрані дні тижня в заданий час"),
        StepSpec("🚀 Запуск телефону", "boot", desc = "Після перезавантаження телефону")
    )
)

private fun describeTrigger(t: String): String {
    val kind = t.substringBefore(':')
    val arg = t.substringAfter(':', "")
    return when (kind) {
        "charging_start" -> "🔌 Зарядку підключено"
        "charging_stop" -> "🔌 Зарядку відключено"
        "battery_low" -> "🪫 Батарея нижче $arg%"
        "bt_connected" -> "🔵 Bluetooth підключився" + (if (arg.isNotBlank()) ": $arg" else "")
        "bt_disconnected" -> "🔵 Bluetooth відключився" + (if (arg.isNotBlank()) ": $arg" else "")
        "wifi_connected" -> "📶 Wi-Fi підключено"
        "wifi_disconnected" -> "📶 Wi-Fi відключено"
        "headset_plugged" -> "🎧 Навушники підключено"
        "headset_unplugged" -> "🎧 Навушники відключено"
        "screen_on" -> "📱 Екран увімкнено"
        "screen_off" -> "📱 Екран вимкнено"
        "unlock" -> "🔓 Розблоковано"
        "airplane_on" -> "✈ Політ увімкнено"
        "airplane_off" -> "✈ Політ вимкнено"
        "time" -> {
            val p = arg.split(':')
            "⏰ ${p.getOrNull(0)?.padStart(2, '0')}:${p.getOrNull(1)?.padStart(2, '0')}" +
                (p.getOrNull(2)?.takeIf { it.isNotBlank() && it != "1234567" }?.let { " (дні $it)" } ?: "")
        }
        "boot" -> "🚀 Запуск телефону"
        else -> t
    }
}

private fun describeStep(step: String): String {
    val s = step.trim().removePrefix("TOOL_CALL:").trim()
    return when {
        s.startsWith("WAIT_UNTIL:") -> "⌛ Чекати: " + s.removePrefix("WAIT_UNTIL:").trim()
        s.startsWith("WAIT:") -> "⏱ Пауза " + s.removePrefix("WAIT:").trim() + " с"
        s.startsWith("IF_NOT:") -> "❓ Якщо НЕ " + s.removePrefix("IF_NOT:").trim().replace("?", "→")
        s.startsWith("IF:") -> "❓ Якщо " + s.removePrefix("IF:").trim().replace("?", "→")
        s.startsWith("SAY:") -> "💬 " + s.removePrefix("SAY:").trim()
        s.startsWith("SET:") -> "📦 " + s.removePrefix("SET:").trim()
        s.startsWith("REPEAT:") -> "🔁 " + s.removePrefix("REPEAT:").trim().replace("?", "разів →")
        else -> "▶ $s"
    }
}

@Composable
fun MacroScreen(onClose: () -> Unit, carBtName: String = "", onCarChange: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val storage = remember { MacroStorage(ctx) }
    var macros by remember { mutableStateOf(storage.get()) }
    val scope = rememberCoroutineScope()
    var runningMacro by remember { mutableStateOf<String?>(null) }
    var progressText by remember { mutableStateOf("") }
    var runJob by remember { mutableStateOf<Job?>(null) }
    var resultLog by remember { mutableStateOf<String?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var editingMacro by remember { mutableStateOf<Macro?>(null) }
    var autoOn by remember { mutableStateOf(storage.autoEnabled()) }
    var showCarPicker by remember { mutableStateOf(false) }
    fun syncService() = com.dynamicai.launcher.macro.MacroTriggerService.sync(ctx.applicationContext)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AppTheme.colors.backdropStart, AppTheme.colors.backdropEnd)))
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⚡ Макроси", color = AppTheme.colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("+", color = AppTheme.colors.successLight, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(8.dp).clickable {
                            editingMacro = null
                            showEditor = true
                        })
                    Text("✕", color = AppTheme.colors.textSecondary, fontSize = 24.sp,
                        modifier = Modifier.padding(8.dp).clickable(onClick = onClose))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Макрос можна запускати голосом: скажіть його назву або фразу-тригер, або «запусти макрос …».",
                color = AppTheme.colors.textSecondary, fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppTheme.colors.textPrimary.copy(alpha = 0.05f))
                    .clickable { showCarPicker = true }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🚗", fontSize = 20.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Моє авто (Bluetooth)", color = AppTheme.colors.textPrimary, fontSize = 14.sp)
                    Text(if (carBtName.isBlank()) "Не вибрано — тап, щоб обрати пристрій" else carBtName,
                        color = if (carBtName.isBlank()) AppTheme.colors.warning else AppTheme.colors.successLight, fontSize = 12.sp)
                }
                Text("›", color = AppTheme.colors.textSecondary, fontSize = 20.sp)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                autoOn = !autoOn
                storage.setAutoEnabled(autoOn)
                syncService()
            }.padding(vertical = 4.dp)) {
                Text(if (autoOn) "☑" else "☐", color = AppTheme.colors.successLight, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text("Автозапуск за подіями (служба у фоні)", color = AppTheme.colors.textPrimary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))

            if (macros.isEmpty()) {
                Text("Натисни +, щоб створити свій перший макрос", color = AppTheme.colors.textSecondary, fontSize = 14.sp)
            }

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(macros, key = { it.id }) { macro ->
                    MacroCard(
                        macro = macro,
                        isRunning = runningMacro == macro.id,
                        progress = if (runningMacro == macro.id) progressText else "",
                        onRun = {
                            runJob = scope.launch {
                                runningMacro = macro.id
                                progressText = "Старт…"
                                val agent = com.dynamicai.launcher.llm.AgentEngine(ctx, null)
                                try {
                                    val log = MacroRunner(ctx, { agent.runTool(it) }).run(macro) { p ->
                                        progressText = "Крок ${p.index}/${p.total}: ${describeStep(p.step)}"
                                    }
                                    resultLog = "«${macro.name}»\n\n$log"
                                } catch (e: kotlinx.coroutines.CancellationException) {
                                    resultLog = "«${macro.name}» зупинено"
                                } finally {
                                    runningMacro = null
                                    progressText = ""
                                }
                            }
                        },
                        onStop = { runJob?.cancel() },
                        onToggleEnabled = {
                            storage.add(macro.copy(enabled = !macro.enabled))
                            macros = storage.get()
                            syncService()
                        },
                        onEdit = {
                            editingMacro = macro
                            showEditor = true
                        },
                        onDuplicate = {
                            storage.create(name = macro.name + " (копія)", steps = macro.steps, delayMs = macro.delayMs)
                                .let { storage.add(it.copy(icon = macro.icon, colorHex = macro.colorHex, stopOnError = macro.stopOnError)) }
                            macros = storage.get()
                        },
                        onDelete = {
                            storage.delete(macro.id)
                            macros = storage.get()
                            syncService()
                        }
                    )
                }
            }
        }

        if (showEditor) {
            MacroEditorScreen(
                macro = editingMacro,
                carBtName = carBtName,
                onDismiss = { showEditor = false },
                onSave = { saved ->
                    storage.add(saved)
                    macros = storage.get()
                    showEditor = false
                    syncService()
                }
            )
        }

        if (showCarPicker) {
            BtDevicePickerDialog(
                title = "🚗 Bluetooth-пристрій авто", current = carBtName, anyLabel = "Не вибрано",
                onPick = { onCarChange(it); showCarPicker = false },
                onDismiss = { showCarPicker = false }
            )
        }

        resultLog?.let { log ->
            AlertDialog(
                onDismissRequest = { resultLog = null },
                containerColor = AppTheme.colors.backdropStart,
                title = { Text("Результат", color = AppTheme.colors.textPrimary) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 400.dp)) {
                        Text(log, color = AppTheme.colors.textPrimary, fontSize = 13.sp)
                    }
                },
                confirmButton = { TextButton(onClick = { resultLog = null }) { Text("OK", color = AppTheme.colors.successLight) } }
            )
        }
    }
}

@Composable
private fun LabeledField(label: String, value: String, hint: String = "", onChange: (String) -> Unit) {
    Text(label, color = AppTheme.colors.textSecondary, fontSize = 12.sp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = TextStyle(color = AppTheme.colors.textPrimary, fontSize = 15.sp),
        cursorBrush = SolidColor(AppTheme.colors.successLight),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp)).background(AppTheme.colors.textPrimary.copy(alpha = 0.05f))
            .padding(12.dp),
        decorationBox = { inner ->
            if (value.isEmpty() && hint.isNotEmpty()) Text(hint, color = AppTheme.colors.textMuted, fontSize = 14.sp)
            inner()
        }
    )
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (selected) AppTheme.colors.successLight.copy(alpha = 0.3f) else AppTheme.colors.textPrimary.copy(alpha = 0.06f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) { Text(label, color = AppTheme.colors.textPrimary, fontSize = 12.sp) }
}

internal fun pairedBtNames(ctx: android.content.Context): List<String> = try {
    if (android.os.Build.VERSION.SDK_INT >= 31 &&
        ctx.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED
    ) emptyList()
    else com.dynamicai.launcher.system.BluetoothManager(ctx).getPairedDevices().map { it.name }.distinct()
} catch (_: Throwable) { emptyList() }

/** Вибір Bluetooth-пристрою (зі спарених). Порожній результат = «будь-який». */
@Composable
fun BtDevicePickerDialog(title: String, current: String, anyLabel: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var names by remember { mutableStateOf(pairedBtNames(ctx)) }
    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { names = pairedBtNames(ctx) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.colors.backdropStart,
        title = { Text(title, color = AppTheme.colors.textPrimary, fontSize = 16.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 380.dp)) {
                if (names.isEmpty()) {
                    Text("Спарених пристроїв не знайдено або не надано дозвіл Bluetooth. Спаруйте авто в налаштуваннях Android і дайте дозвіл.",
                        color = AppTheme.colors.textSecondary, fontSize = 13.sp)
                    TextButton(onClick = { permLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT) }) {
                        Text("Дозволити Bluetooth", color = AppTheme.colors.successLight)
                    }
                }
                names.forEach { n ->
                    Text((if (n == current) "✓ " else "") + n,
                        color = if (n == current) AppTheme.colors.successLight else AppTheme.colors.textPrimary, fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(n) }.padding(vertical = 10.dp))
                }
                Text(anyLabel, color = AppTheme.colors.textSecondary, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().clickable { onPick("") }.padding(vertical = 10.dp))
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрити", color = AppTheme.colors.textSecondary) } }
    )
}

/** Повноекранний вибір із групами; список завжди прокручується. */
@Composable
private fun PickerScreen(title: String, groups: List<Pair<String, List<StepSpec>>>, onPick: (StepSpec) -> Unit, onClose: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AppTheme.colors.backdropStart, AppTheme.colors.backdropEnd)))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("←", color = AppTheme.colors.textPrimary, fontSize = 24.sp, modifier = Modifier.clickable(onClick = onClose).padding(end = 16.dp))
                Text(title, color = AppTheme.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                groups.forEach { (group, specs) ->
                    item(key = "h_$group") {
                        Text(group, color = AppTheme.colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                    }
                    items(specs, key = { group + it.label }) { spec ->
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
                                .background(AppTheme.colors.textPrimary.copy(alpha = 0.05f))
                                .clickable { onPick(spec) }.padding(12.dp)
                        ) {
                            Text(spec.label, color = AppTheme.colors.textPrimary, fontSize = 14.sp)
                            val d = spec.desc.ifBlank { spec.template.replace("{0}", "…").replace("{1}", "…").replace("{2}", "…") }
                            Text(d, color = AppTheme.colors.textMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorSection(title: String, hint: String = "", content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(16.dp))
            .background(AppTheme.colors.textPrimary.copy(alpha = 0.05f))
            .border(0.5.dp, AppTheme.colors.glassBorder.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Text(title, color = AppTheme.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (hint.isNotBlank()) Text(hint, color = AppTheme.colors.textMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
        content()
    }
}

@Composable
fun MacroEditorScreen(
    macro: Macro?,
    carBtName: String,
    onDismiss: () -> Unit,
    onSave: (Macro) -> Unit
) {
    var name by remember { mutableStateOf(macro?.name ?: "") }
    var icon by remember { mutableStateOf(macro?.icon ?: "⚡") }
    var colorHex by remember { mutableStateOf(macro?.colorHex ?: 0xFF2196F3) }
    var trigger by remember { mutableStateOf(macro?.trigger ?: "") }
    var delayMs by remember { mutableStateOf(macro?.delayMs ?: 900L) }
    var stopOnError by remember { mutableStateOf(macro?.stopOnError ?: false) }
    var constraints by remember { mutableStateOf(macro?.constraints ?: "") }
    val triggers = remember { mutableStateListOf<String>().apply { addAll(macro?.triggers ?: emptyList()) } }
    val steps = remember { mutableStateListOf<String>().apply { addAll(macro?.steps ?: emptyList()) } }
    var showTriggerPicker by remember { mutableStateOf(false) }
    var showStepPicker by remember { mutableStateOf(false) }
    var pendingTrigger by remember { mutableStateOf<StepSpec?>(null) }
    var pendingSpec by remember { mutableStateOf<StepSpec?>(null) }
    var editIndex by remember { mutableStateOf(-1) }
    var rawStep by remember { mutableStateOf("") }

    val iconOptions = listOf("⚡", "🏢", "🚗", "🎬", "🌙", "🏠", "🎮", "🎵", "☕", "💼", "🌞", "🚀")
    val colorOptions = listOf(0xFF2196F3, 0xFF4CAF50, 0xFFFF9800, 0xFF9C27B0, 0xFFE91E63, 0xFF00BCD4)

    androidx.activity.compose.BackHandler(onBack = onDismiss)
    val canSave = name.isNotBlank() && steps.isNotEmpty()

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AppTheme.colors.backdropStart, AppTheme.colors.backdropEnd)))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("←", color = AppTheme.colors.textPrimary, fontSize = 24.sp, modifier = Modifier.clickable(onClick = onDismiss).padding(end = 16.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (macro == null) "Новий макрос" else "Редагування", color = AppTheme.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Подій: ${triggers.size} · кроків: ${steps.size}", color = AppTheme.colors.textMuted, fontSize = 11.sp)
                }
                Text("Зберегти", color = if (canSave) AppTheme.colors.successLight else AppTheme.colors.textMuted, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = canSave) {
                        val base = macro ?: Macro(id = java.util.UUID.randomUUID().toString(), name = name, steps = emptyList())
                        onSave(base.copy(
                            name = name.trim(), icon = icon, colorHex = colorHex, trigger = trigger.trim(),
                            steps = steps.map { it.trim() }.filter { it.isNotBlank() }, delayMs = delayMs, stopOnError = stopOnError,
                            triggers = triggers.toList(), constraints = constraints.trim()
                        ))
                    }.padding(8.dp))
            }
            if (!canSave) Text("Щоб зберегти: вкажіть назву й додайте хоча б один крок", color = AppTheme.colors.textMuted, fontSize = 11.sp)

            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    EditorSection("Основне") {
                        LabeledField("Назва", name, "Наприклад: Їду на роботу") { name = it }
                        Text("Іконка", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            iconOptions.forEach { ic ->
                                Box(
                                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                                        .background(if (icon == ic) AppTheme.colors.successLight.copy(alpha = 0.3f) else AppTheme.colors.textPrimary.copy(alpha = 0.05f))
                                        .clickable { icon = ic },
                                    contentAlignment = Alignment.Center
                                ) { Text(ic, fontSize = 20.sp) }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Колір", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            colorOptions.forEach { c ->
                                Box(
                                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(50)).background(Color(c))
                                        .border(if (colorHex == c) 2.dp else 0.dp, AppTheme.colors.textPrimary, RoundedCornerShape(50))
                                        .clickable { colorHex = c }
                                )
                            }
                        }
                    }
                }
                item {
                    EditorSection("🎙 Голосовий запуск", "Скажіть будь-яку з цих фраз — і макрос запуститься. Назва макросу теж працює.") {
                        LabeledField("Фрази через кому", trigger, "їду на роботу, режим роботи") { trigger = it }
                    }
                }
                item {
                    EditorSection("⚙ Автозапуск за подіями", "Макрос спрацює сам, коли станеться будь-яка з подій нижче.") {
                        if (triggers.isEmpty()) Text("Подій ще немає", color = AppTheme.colors.textMuted, fontSize = 12.sp)
                        triggers.forEachIndexed { i, t ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp))
                                    .background(AppTheme.colors.textPrimary.copy(alpha = 0.06f)).padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(describeTrigger(t), color = AppTheme.colors.textPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text("✕", color = AppTheme.colors.danger, fontSize = 16.sp, modifier = Modifier.clickable { triggers.removeAt(i) }.padding(horizontal = 6.dp))
                            }
                        }
                        TextButton(onClick = { showTriggerPicker = true }) { Text("+ Додати подію", color = AppTheme.colors.successLight, fontSize = 13.sp) }
                        if (triggers.isNotEmpty()) {
                            LabeledField("Умови автозапуску (через кому, усі мають виконуватись)", constraints, "time>08:00, time<22:00, charging") { constraints = it }
                            Text("Умови: ${MacroRunner.CONDITIONS}", color = AppTheme.colors.textMuted, fontSize = 11.sp)
                        }
                    }
                }
                item {
                    EditorSection("▶ Що робити (кроки)", "Виконуються по черзі згори вниз. Тап по кроку — редагувати текст.") {
                        if (steps.isEmpty()) Text("Кроків ще немає", color = AppTheme.colors.textMuted, fontSize = 12.sp)
                        steps.forEachIndexed { i, s ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(8.dp))
                                    .background(AppTheme.colors.textPrimary.copy(alpha = 0.06f)).padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${i + 1}.", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(describeStep(s), color = AppTheme.colors.textPrimary, fontSize = 12.sp,
                                    modifier = Modifier.weight(1f).clickable { editIndex = i; rawStep = s })
                                Text("↑", color = AppTheme.colors.accent, fontSize = 18.sp, modifier = Modifier.clickable(enabled = i > 0) {
                                    val t = steps[i]; steps[i] = steps[i - 1]; steps[i - 1] = t
                                }.padding(horizontal = 6.dp))
                                Text("↓", color = AppTheme.colors.accent, fontSize = 18.sp, modifier = Modifier.clickable(enabled = i < steps.size - 1) {
                                    val t = steps[i]; steps[i] = steps[i + 1]; steps[i + 1] = t
                                }.padding(horizontal = 6.dp))
                                Text("⧉", color = AppTheme.colors.accent, fontSize = 16.sp, modifier = Modifier.clickable { steps.add(i + 1, s) }.padding(horizontal = 6.dp))
                                Text("✕", color = AppTheme.colors.danger, fontSize = 15.sp, modifier = Modifier.clickable { steps.removeAt(i) }.padding(horizontal = 6.dp))
                            }
                        }
                        Row {
                            TextButton(onClick = { showStepPicker = true }) { Text("+ Додати крок", color = AppTheme.colors.successLight, fontSize = 13.sp) }
                            TextButton(onClick = { editIndex = -2; rawStep = "" }) { Text("+ Свій крок", color = AppTheme.colors.accent, fontSize = 13.sp) }
                        }
                    }
                }
                item {
                    EditorSection("Параметри виконання") {
                        Text("Пауза між кроками", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0L to "0", 500L to "0.5 с", 900L to "0.9 с", 2000L to "2 с", 3000L to "3 с", 5000L to "5 с").forEach { (ms, lbl) ->
                                Chip(lbl, delayMs == ms) { delayMs = ms }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { stopOnError = !stopOnError }.padding(vertical = 4.dp)) {
                            Text(if (stopOnError) "☑" else "☐", color = AppTheme.colors.successLight, fontSize = 20.sp)
                            Spacer(Modifier.width(8.dp))
                            Text("Зупинитись, якщо крок завершився помилкою", color = AppTheme.colors.textPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        if (showTriggerPicker) PickerScreen("Оберіть подію", TRIGGER_GROUPS, onPick = { spec ->
            showTriggerPicker = false
            if (spec.params.isEmpty()) triggers.add(spec.template) else pendingTrigger = spec
        }, onClose = { showTriggerPicker = false })

        if (showStepPicker) PickerScreen("Оберіть дію", STEP_CATALOG, onPick = { spec ->
            showStepPicker = false
            if (spec.params.isEmpty()) steps.add(spec.template) else pendingSpec = spec
        }, onClose = { showStepPicker = false })

        // Параметри тригера; для Bluetooth — вибір зі спарених пристроїв
        pendingTrigger?.let { spec ->
            if (spec.template.startsWith("bt_")) {
                BtDevicePickerDialog(
                    title = spec.label, current = carBtName,
                    anyLabel = "Будь-який пристрій",
                    onPick = { picked -> triggers.add(spec.template.replace("{0}", picked.replace(":", ""))); pendingTrigger = null },
                    onDismiss = { pendingTrigger = null }
                )
            } else {
                val values = remember(spec) { mutableStateListOf<String>().apply { addAll(spec.params.map { it.second }) } }
                AlertDialog(
                    onDismissRequest = { pendingTrigger = null },
                    containerColor = AppTheme.colors.backdropStart,
                    title = { Text(spec.label, color = AppTheme.colors.textPrimary, fontSize = 16.sp) },
                    text = { Column(Modifier.verticalScroll(rememberScrollState())) { spec.params.forEachIndexed { i, p -> LabeledField(p.first, values[i]) { values[i] = it } } } },
                    confirmButton = {
                        TextButton(onClick = {
                            var t = spec.template
                            values.forEachIndexed { i, v -> t = t.replace("{$i}", v.trim().replace(":", "")) }
                            triggers.add(t)
                            pendingTrigger = null
                        }) { Text("Додати", color = AppTheme.colors.successLight) }
                    },
                    dismissButton = { TextButton(onClick = { pendingTrigger = null }) { Text("Скасувати", color = AppTheme.colors.textSecondary) } }
                )
            }
        }

        // Параметри кроку; для підключення Bluetooth — вибір пристрою
        pendingSpec?.let { spec ->
            if (spec.template.startsWith("connectBluetooth")) {
                BtDevicePickerDialog(
                    title = spec.label, current = carBtName,
                    anyLabel = if (carBtName.isNotBlank()) "🚗 Моє авто ($carBtName)" else "Авто з налаштувань",
                    onPick = { picked -> steps.add("connectBluetooth(name=\"" + picked.replace("\"", "") + "\")"); pendingSpec = null },
                    onDismiss = { pendingSpec = null }
                )
            } else {
                val values = remember(spec) { mutableStateListOf<String>().apply { addAll(spec.params.map { it.second }) } }
                AlertDialog(
                    onDismissRequest = { pendingSpec = null },
                    containerColor = AppTheme.colors.backdropStart,
                    title = { Text(spec.label, color = AppTheme.colors.textPrimary, fontSize = 16.sp) },
                    text = { Column(Modifier.verticalScroll(rememberScrollState())) { spec.params.forEachIndexed { i, p -> LabeledField(p.first, values[i]) { values[i] = it } } } },
                    confirmButton = {
                        TextButton(onClick = {
                            var t = spec.template
                            values.forEachIndexed { i, v -> t = t.replace("{$i}", v.replace("\"", "")) }
                            steps.add(t.trim())
                            pendingSpec = null
                        }) { Text("Додати", color = AppTheme.colors.successLight) }
                    },
                    dismissButton = { TextButton(onClick = { pendingSpec = null }) { Text("Скасувати", color = AppTheme.colors.textSecondary) } }
                )
            }
        }

        if (editIndex != -1) {
            AlertDialog(
                onDismissRequest = { editIndex = -1 },
                containerColor = AppTheme.colors.backdropStart,
                title = { Text(if (editIndex == -2) "Свій крок" else "Редагувати крок", color = AppTheme.colors.textPrimary, fontSize = 16.sp) },
                text = {
                    Column {
                        LabeledField("Крок", rawStep, "toggleWifi(state=\"on\")  |  WAIT: 3  |  IF: online ? carMode()") { rawStep = it }
                        Text("Службові: WAIT: сек · WAIT_UNTIL: умова сек · IF: умова ? крок · IF_NOT: умова ? крок · SAY: текст · SET: ім'я=значення · REPEAT: N ? крок.\nУмови: ${MacroRunner.CONDITIONS}",
                            color = AppTheme.colors.textMuted, fontSize = 11.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (rawStep.isNotBlank()) {
                            if (editIndex >= 0) steps[editIndex] = rawStep.trim() else steps.add(rawStep.trim())
                        }
                        editIndex = -1
                    }) { Text("OK", color = AppTheme.colors.successLight) }
                },
                dismissButton = { TextButton(onClick = { editIndex = -1 }) { Text("Скасувати", color = AppTheme.colors.textSecondary) } }
            )
        }
    }
}

@Composable
fun MacroCard(
    macro: Macro, isRunning: Boolean, progress: String,
    onRun: () -> Unit, onStop: () -> Unit, onToggleEnabled: () -> Unit, onEdit: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit
) {
    val color = Color(macro.colorHex)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppTheme.colors.textPrimary.copy(alpha = 0.05f))
            .border(0.5.dp, if (isRunning) color else AppTheme.colors.glassBorder.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(macro.icon, fontSize = 24.sp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).clickable(enabled = !isRunning, onClick = onEdit)) {
            Text(macro.name, color = AppTheme.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            val sub = if (isRunning) progress else
                "${macro.steps.size} кроків" + (if (macro.trigger.isNotBlank()) " · 🎙 ${macro.trigger}" else "") +
                    (if (macro.triggers.isNotEmpty()) " · ⚙ ${macro.triggers.size} подій" else "") + " · тап — редагувати"
            Text(sub, color = if (isRunning) color else AppTheme.colors.textSecondary, fontSize = 11.sp, maxLines = 2)
        }
        if (isRunning) {
            Text("⏹", fontSize = 20.sp, modifier = Modifier.clickable(onClick = onStop).padding(8.dp))
        } else {
            if (macro.triggers.isNotEmpty()) {
                Text(if (macro.enabled) "🔔" else "🔕", fontSize = 18.sp, modifier = Modifier.clickable(onClick = onToggleEnabled).padding(6.dp))
            }
            Text("▶️", fontSize = 20.sp, modifier = Modifier.clickable(onClick = onRun).padding(8.dp))
            Text("⧉", fontSize = 18.sp, color = AppTheme.colors.accent, modifier = Modifier.clickable(onClick = onDuplicate).padding(6.dp))
            Text("🗑️", fontSize = 18.sp, modifier = Modifier.padding(4.dp).clickable { onDelete() })
        }
    }
}

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
private data class StepSpec(val label: String, val template: String, val params: List<Pair<String, String>> = emptyList())

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

private val TRIGGER_CATALOG = listOf(
    StepSpec("🔌 Зарядку підключено", "charging_start"),
    StepSpec("🔌 Зарядку відключено", "charging_stop"),
    StepSpec("🪫 Батарея нижче N %", "battery_low:{0}", listOf("Відсоток" to "20")),
    StepSpec("🔵 Bluetooth підключився", "bt_connected:{0}", listOf("Назва пристрою (порожньо — будь-який)" to "")),
    StepSpec("🔵 Bluetooth відключився", "bt_disconnected:{0}", listOf("Назва пристрою (порожньо — будь-який)" to "")),
    StepSpec("📶 Wi-Fi підключено", "wifi_connected"),
    StepSpec("📶 Wi-Fi відключено", "wifi_disconnected"),
    StepSpec("🎧 Навушники підключено", "headset_plugged"),
    StepSpec("🎧 Навушники відключено", "headset_unplugged"),
    StepSpec("📱 Екран увімкнено", "screen_on"),
    StepSpec("📱 Екран вимкнено", "screen_off"),
    StepSpec("🔓 Телефон розблоковано", "unlock"),
    StepSpec("✈ Режим польоту увімкнено", "airplane_on"),
    StepSpec("✈ Режим польоту вимкнено", "airplane_off"),
    StepSpec("⏰ За часом", "time:{0}:{1}:{2}", listOf("Година" to "7", "Хвилини" to "30", "Дні 1=Пн…7=Нд (порожньо — щодня)" to "12345")),
    StepSpec("🚀 Запуск телефону", "boot")
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
fun MacroScreen(onClose: () -> Unit) {
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
            MacroEditorDialog(
                macro = editingMacro,
                onDismiss = { showEditor = false },
                onSave = { saved ->
                    storage.add(saved)
                    macros = storage.get()
                    showEditor = false
                    syncService()
                }
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

@Composable
fun MacroEditorDialog(
    macro: Macro?,
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
    var pendingTrigger by remember { mutableStateOf<StepSpec?>(null) }
    var showTriggerPicker by remember { mutableStateOf(false) }
    val steps = remember { mutableStateListOf<String>().apply { addAll(macro?.steps ?: emptyList()) } }
    var category by remember { mutableStateOf(STEP_CATALOG.first().first) }
    var pendingSpec by remember { mutableStateOf<StepSpec?>(null) }
    var editIndex by remember { mutableStateOf(-1) }
    var rawStep by remember { mutableStateOf("") }

    val iconOptions = listOf("⚡", "🏢", "🚗", "🎬", "🌙", "🏠", "🎮", "🎵", "☕", "💼", "🌞", "🚀")
    val colorOptions = listOf(0xFF2196F3, 0xFF4CAF50, 0xFFFF9800, 0xFF9C27B0, 0xFFE91E63, 0xFF00BCD4)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.96f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = AppTheme.colors.backdropStart,
        title = { Text(if (macro == null) "Новий макрос" else "Редагувати макрос", color = AppTheme.colors.textPrimary) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 560.dp)) {
                LabeledField("Назва", name, "Наприклад: Їду на роботу") { name = it }
                LabeledField("Голосові фрази-тригери (через кому)", trigger, "їду на роботу, режим роботи") { trigger = it }

                Spacer(Modifier.height(6.dp))
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
                            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(50))
                                .background(Color(c))
                                .border(if (colorHex == c) 2.dp else 0.dp, AppTheme.colors.textPrimary, RoundedCornerShape(50))
                                .clickable { colorHex = c }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
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

                Spacer(Modifier.height(14.dp))
                Text("Автозапуск за подіями (${triggers.size})", color = AppTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("Макрос спрацює сам, коли станеться будь-яка з цих подій.", color = AppTheme.colors.textMuted, fontSize = 11.sp)
                triggers.forEachIndexed { i, t ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(8.dp)).background(AppTheme.colors.textPrimary.copy(alpha = 0.05f))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(describeTrigger(t), color = AppTheme.colors.textPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("✕", color = AppTheme.colors.danger, fontSize = 15.sp, modifier = Modifier.clickable { triggers.removeAt(i) }.padding(horizontal = 6.dp))
                    }
                }
                TextButton(onClick = { showTriggerPicker = true }) {
                    Text("+ Додати подію-тригер", color = AppTheme.colors.successLight, fontSize = 12.sp)
                }
                if (triggers.isNotEmpty()) {
                    LabeledField("Умови автозапуску (через кому, усі мають виконуватись)", constraints, "time>08:00, time<22:00, charging") { constraints = it }
                    Text("Умови: ${MacroRunner.CONDITIONS}", color = AppTheme.colors.textMuted, fontSize = 11.sp)
                }

                Spacer(Modifier.height(14.dp))
                Text("Кроки (${steps.size})", color = AppTheme.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                steps.forEachIndexed { i, s ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(8.dp)).background(AppTheme.colors.textPrimary.copy(alpha = 0.05f))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
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
                if (steps.isNotEmpty()) Text("Тап по кроку — редагувати текст", color = AppTheme.colors.textMuted, fontSize = 11.sp)

                Spacer(Modifier.height(12.dp))
                Text("Додати крок", color = AppTheme.colors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    STEP_CATALOG.forEach { (cat, _) -> Chip(cat, category == cat) { category = cat } }
                }
                Spacer(Modifier.height(4.dp))
                STEP_CATALOG.first { it.first == category }.second.forEach { spec ->
                    Text(
                        "+ ${spec.label}",
                        color = AppTheme.colors.accent,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (spec.params.isEmpty()) steps.add(spec.template) else pendingSpec = spec
                        }.padding(vertical = 6.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { editIndex = -2; rawStep = "" }) {
                    Text("+ Свій крок (текстом)", color = AppTheme.colors.successLight, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank() && steps.isNotEmpty()) {
                    val base = macro ?: Macro(id = java.util.UUID.randomUUID().toString(), name = name, steps = emptyList())
                    onSave(base.copy(
                        name = name.trim(), icon = icon, colorHex = colorHex, trigger = trigger.trim(),
                        steps = steps.map { it.trim() }.filter { it.isNotBlank() }, delayMs = delayMs, stopOnError = stopOnError,
                        triggers = triggers.toList(), constraints = constraints.trim()
                    ))
                }
            }) { Text("Зберегти", color = AppTheme.colors.successLight) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати", color = AppTheme.colors.textSecondary) } }
    )

    // Вибір тригера
    if (showTriggerPicker) {
        AlertDialog(
            onDismissRequest = { showTriggerPicker = false },
            containerColor = AppTheme.colors.backdropStart,
            title = { Text("Подія-тригер", color = AppTheme.colors.textPrimary, fontSize = 16.sp) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 420.dp)) {
                    TRIGGER_CATALOG.forEach { spec ->
                        Text(spec.label, color = AppTheme.colors.accent, fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth().clickable {
                                showTriggerPicker = false
                                if (spec.params.isEmpty()) triggers.add(spec.template) else pendingTrigger = spec
                            }.padding(vertical = 8.dp))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showTriggerPicker = false }) { Text("Закрити", color = AppTheme.colors.textSecondary) } }
        )
    }
    pendingTrigger?.let { spec ->
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

    // Параметри нового кроку
    pendingSpec?.let { spec ->
        val values = remember(spec) { mutableStateListOf<String>().apply { addAll(spec.params.map { it.second }) } }
        AlertDialog(
            onDismissRequest = { pendingSpec = null },
            containerColor = AppTheme.colors.backdropStart,
            title = { Text(spec.label, color = AppTheme.colors.textPrimary, fontSize = 16.sp) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    spec.params.forEachIndexed { i, p -> LabeledField(p.first, values[i]) { values[i] = it } }
                }
            },
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

    // Текстове редагування / власний крок
    if (editIndex != -1) {
        AlertDialog(
            onDismissRequest = { editIndex = -1 },
            containerColor = AppTheme.colors.backdropStart,
            title = { Text(if (editIndex == -2) "Свій крок" else "Редагувати крок", color = AppTheme.colors.textPrimary, fontSize = 16.sp) },
            text = {
                Column {
                    LabeledField("Крок", rawStep, "toggleWifi(state=\"on\")  |  WAIT: 3  |  IF: online ? carMode()") { rawStep = it }
                    Text("Службові: WAIT: сек · WAIT_UNTIL: умова сек · IF: умова ? крок · IF_NOT: умова ? крок · SAY: текст.\nУмови: ${MacroRunner.CONDITIONS}",
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

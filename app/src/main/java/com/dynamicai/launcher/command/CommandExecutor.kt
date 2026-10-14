package com.dynamicai.launcher.command

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import kotlinx.coroutines.launch
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import com.dynamicai.launcher.model.AppInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CommandExecutor(private val context: Context) {
    private val systemManager by lazy {
        com.dynamicai.launcher.system.SystemSettingsManager(context)
    }
    private val quickManager by lazy {
        com.dynamicai.launcher.system.QuickSettingsManager(context)
    }
    private val shizukuManager by lazy {
        com.dynamicai.launcher.system.ShizukuManager(context)
    }

    fun execute(cmd: Command, apps: List<AppInfo>): CommandResult = when (cmd) {
        is Command.OpenApp -> resolveOpenApp(cmd.query, apps)
        is Command.WebSearch -> webSearch(cmd.query)
        is Command.Call -> call(cmd.name)
        is Command.SetAlarm -> setAlarm(cmd.time, cmd.label)
        is Command.SetTimer -> setTimer(cmd.duration)
        is Command.OpenSettings -> openSettings(cmd.which)
        is Command.DeepLink -> openDeepLink(cmd.app, cmd.target)
        is Command.SearchInApp -> searchInApp(cmd.packageKeyword, cmd.query)
        is Command.SetVolume -> changeVolume(cmd.level)
        is Command.SetToggle -> handleToggle(cmd.target, cmd.enabled)
        Command.WifiOn -> handleToggle("wifi", true)
        Command.WifiOff -> handleToggle("wifi", false)
        Command.BluetoothOn -> handleToggle("bluetooth", true)
        Command.BluetoothOff -> handleToggle("bluetooth", false)
        Command.DndOn -> handleToggle("dnd", true)
        Command.DndOff -> handleToggle("dnd", false)
        Command.ToggleFlashlight -> toggleFlashlight()
        is Command.Note -> createNote(cmd.text)
        Command.Screenshot -> takeScreenshot()
        is Command.SetBrightness -> setBrightness(cmd.level)
        is Command.AskQuestion -> CommandResult.Error("__ASK_QUESTION__")  // обробляється у ViewModel
        is Command.Raw -> CommandResult.Error("Не розумію команду: \"${cmd.text}\"")
        Command.Unknown -> CommandResult.Error("Не розумію команду")
    }

    fun resolveOpenApp(query: String, apps: List<AppInfo>): CommandResult {
        val scored = apps
            .map { it to FuzzyMatcher.score(it.label, query) }
            .filter { it.second >= 35 }
            .sortedByDescending { it.second }

        if (scored.isEmpty()) return CommandResult.Error("Не знайдено застосунок: \"$query\"")

        val topScore = scored.first().second
        val candidates = scored
            .filter { it.second >= topScore - 15 }
            .map { it.first }
            .take(6)

        return if (candidates.size == 1) launchApp(candidates.first())
        else CommandResult.ChooseApp(query, candidates)
    }

    fun launchApp(app: AppInfo): CommandResult {
        val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            ?: return CommandResult.Error("Не вдалось відкрити ${app.label}")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            CommandResult.Success("Відкриваю ${app.label}")
        } catch (e: Exception) {
            CommandResult.Error("Помилка: ${e.message}")
        }
    }

    private fun webSearch(query: String): CommandResult {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra("query", query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            CommandResult.Success("Шукаю: $query")
        } catch (e: Exception) {
            val url = "https://www.google.com/search?q=" + Uri.encode(query)
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                CommandResult.Success("Шукаю: $query")
            } catch (e2: Exception) {
                CommandResult.Error("Не вдалось відкрити браузер")
            }
        }
    }

    private fun call(name: String): CommandResult {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(name)}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            CommandResult.Success("Набираю: $name")
        } catch (e: Exception) {
            CommandResult.Error("Помилка діалера: ${e.message}")
        }
    }

    private fun setAlarm(time: String, label: String?): CommandResult {
        val (hour, minute) = parseTime(time)
            ?: return CommandResult.Error("Не розпізнав час: \"$time\"")

        // Перевіряємо дозвіл exact alarm
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            try {
                val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                if (!am.canScheduleExactAlarms()) {
                    // Відкриваємо системні налаштування для дозволу
                    try {
                        context.startActivity(
                            android.content.Intent(
                                android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                android.net.Uri.parse("package:${context.packageName}")
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Throwable) {}
                    return CommandResult.Error("Дай дозвіл на точні будильники у налаштуваннях")
                }
            } catch (_: Throwable) {}
        }

        // Ставимо будильник напряму
        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager

            val cal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                set(java.util.Calendar.MINUTE, minute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
            }

            val intent = Intent(context, com.dynamicai.launcher.alarm.AlarmReceiver::class.java).apply {
                putExtra("label", label ?: "DynamicAI")
                putExtra("hour", hour)
                putExtra("minute", minute)
            }
            val pending = android.app.PendingIntent.getBroadcast(
                context,
                (hour * 100 + minute),
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setAlarmClock(
                android.app.AlarmManager.AlarmClockInfo(cal.timeInMillis, pending),
                pending
            )

            CommandResult.Success("Будильник на %02d:%02d".format(hour, minute))
        } catch (e: Exception) {
            CommandResult.Error("Будильник: ${e.message}")
        }

    }
    private fun setTimer(duration: String): CommandResult {
        val (seconds, display) = parseDuration(duration)
            ?: return CommandResult.Error("Не розпізнав тривалість: \"$duration\"")

        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            CommandResult.Success("Таймер на $display")
        } catch (e: Exception) {
            CommandResult.Error("Не вдалось поставити таймер")
        }
    }

    private fun openSettings(which: String?): CommandResult {
        val action = if (which.isNullOrBlank()) Settings.ACTION_SETTINGS
                     else SettingsRoutes.route(which)
        if (action == null) return CommandResult.Error("Невідомий розділ налаштувань: \"$which\"")
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            CommandResult.Success("Відкриваю: ${which ?: "налаштування"}")
        } catch (e: Exception) {
            CommandResult.Error("Налаштування: ${e::class.simpleName} — ${e.message}")
        }
    }

    private fun openDeepLink(app: String, target: String): CommandResult {
        val uri = when (app) {
            "telegram" -> DeepLinks.telegram(target)
            "whatsapp" -> DeepLinks.whatsapp(target)
            "instagram" -> DeepLinks.instagram(target)
            "viber" -> DeepLinks.viber(target)
            "signal" -> DeepLinks.signal(target)
            "youtube" -> DeepLinks.youtube(target)
            "maps" -> DeepLinks.maps(target)
            "spotify" -> DeepLinks.spotify(target)
            "sms" -> DeepLinks.sms(target)
            "email" -> DeepLinks.email(target)
            else -> return CommandResult.Error("Невідомий застосунок: $app")
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            CommandResult.Success("Відкриваю $app: $target")
        } catch (e: Exception) {
            // fallback 1: спробувати знайти мод-застосунок за ключовим словом
            val keyword = when (app) {
                "telegram" -> "telegram"
                "instagram" -> "insta"
                "whatsapp" -> "whatsapp"
                "viber" -> "viber"
                "signal" -> "signal"
                else -> null
            }
            if (keyword != null) {
                val pkg = findPackageByKeyword(keyword)
                if (pkg != null) {
                    val launch = context.packageManager.getLaunchIntentForPackage(pkg)
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(launch)
                            return CommandResult.Success("Відкриваю $app ($pkg)")
                        } catch (_: Exception) {}
                    }
                }
            }

            // fallback 2: спробувати звичайний веб-URL
            val webFallback = when (app) {
                "telegram" -> "https://t.me/${target.removePrefix("@")}"
                "whatsapp" -> DeepLinks.whatsapp(target)
                "instagram" -> "https://instagram.com/${target.removePrefix("@")}"
                "youtube" -> DeepLinks.youtube(target)
                "maps" -> "https://www.google.com/maps/search/" + Uri.encode(target)
                "spotify" -> "https://open.spotify.com/search/" + Uri.encode(target)
                else -> null
            }
            if (webFallback != null) {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(webFallback))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    return CommandResult.Success("Відкриваю $app (web): $target")
                } catch (_: Exception) {}
            }
            CommandResult.Error("$app: ${e::class.simpleName} — ${e.message}")
        }
    }

    private fun changeVolume(level: Int): CommandResult {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)

        val target = when {
            level < 0 -> (current - 2).coerceAtLeast(0)
            level > 100 -> max
            level == 100 -> max
            level == 1 -> (current + 2).coerceAtMost(max)
            else -> level
        }

        return try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
            CommandResult.Success("Гучність: $target/$max")
        } catch (e: Exception) {
            CommandResult.Error("Помилка гучності: ${e.message}")
        }
    }

    private fun toggleFlashlight(): CommandResult {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull { camId ->
                cm.getCameraCharacteristics(camId)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return CommandResult.Error("Ліхтарик недоступний")

            val newState = !_flashlightOn.value
            cm.setTorchMode(id, newState)
            _flashlightOn.value = newState
            CommandResult.Success(if (newState) "Ліхтарик увімкнено" else "Ліхтарик вимкнено")
        } catch (e: Exception) {
            CommandResult.Error("Помилка ліхтарика: ${e.message}")
        }
    }

    private fun parseTime(input: String): Pair<Int, Int>? {
        val nums = Regex("\\d+").findAll(input)
            .mapNotNull { it.value.toIntOrNull() }
            .toList()
        if (nums.isEmpty()) return null
        val h = nums[0]
        val m = nums.getOrNull(1) ?: 0
        if (h !in 0..23 || m !in 0..59) return null
        return h to m
    }

    private fun parseDuration(input: String): Pair<Int, String>? {
        val s = input.trim().lowercase()
        val hourRe = Regex("(\\d+)\\s*(год|годин|година|години|час|hour|hours|h)")
        val minRe = Regex("(\\d+)\\s*(хв|хвилин|хвилина|хвилини|мин|минут|minute|minutes|min)")
        val secRe = Regex("(\\d+)\\s*(сек|секунд|секунда|секунди|second|seconds|sec)")

        var total = 0
        val parts = mutableListOf<String>()

        hourRe.find(s)?.let {
            val v = it.groupValues[1].toInt()
            total += v * 3600
            parts.add("$v год")
        }
        minRe.find(s)?.let {
            val v = it.groupValues[1].toInt()
            total += v * 60
            parts.add("$v хв")
        }
        secRe.find(s)?.let {
            val v = it.groupValues[1].toInt()
            total += v
            parts.add("$v сек")
        }

        if (total == 0) {
            // Без одиниць — вважаємо хвилинами
            val n = Regex("\\d+").find(s)?.value?.toIntOrNull() ?: return null
            if (n <= 0) return null
            return n * 60 to "$n хв"
        }
        return total to parts.joinToString(" ")
    }

    private fun findPackageByKeyword(keyword: String): String? {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .asSequence()
            .mapNotNull { it.activityInfo?.packageName }
            .filter { pkg -> pkg.contains(keyword, ignoreCase = true) }
            .distinct()
            .firstOrNull()
    }

    private val _flashlightOn = MutableStateFlow(false)
    val flashlightOn: StateFlow<Boolean> = _flashlightOn.asStateFlow()

    private fun searchInApp(packageKeyword: String, query: String): CommandResult {
        val pm = context.packageManager
        val pkg = findPackageByKeyword(packageKeyword)
            ?: return CommandResult.Error("Не знайдено застосунок з ключем \"$packageKeyword\"")

        val intent = Intent(Intent.ACTION_SEARCH).apply {
            setPackage(pkg)
            putExtra(SearchManager.QUERY, query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            CommandResult.Success("Шукаю \"$query\"")
        } catch (e: Exception) {
            // Fallback: копіюємо ім'я в буфер + відкриваємо застосунок
            copyToClipboard(query)
            val launch = pm.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(launch)
                    return CommandResult.Success("\"$query\" скопійовано — встав у пошук")
                } catch (_: Exception) {}
            }
            CommandResult.Error("$pkg: ${e::class.simpleName}")
        }
    }

    private fun copyToClipboard(text: String) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("DynamicAI", text))
        } catch (_: Exception) {}
    }


    private fun createNote(text: String): CommandResult {
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, "DynamicAI Note")
            }
            val chooser = Intent.createChooser(intent, "Зберегти нотатку").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            CommandResult.Success("Нотатка: $text")
        } catch (e: Exception) {
            CommandResult.Error("Помилка нотатки: ${e.message}")
        }
    }

    private fun takeScreenshot(): CommandResult {
        return CommandResult.Error("Скріншот потребує MediaProjection — у розробці")
    }

    private fun setBrightness(level: Int): CommandResult {
        return try {
            val resolver = context.contentResolver
            val current = android.provider.Settings.System.getInt(
                resolver,
                android.provider.Settings.System.SCREEN_BRIGHTNESS,
                128
            )
            val target = when {
                level < 0 -> (current - 30).coerceAtLeast(0)
                level >= 100 -> 255
                level == 1 -> (current + 30).coerceAtMost(255)
                else -> level
            }
            android.provider.Settings.System.putInt(
                resolver,
                android.provider.Settings.System.SCREEN_BRIGHTNESS,
                target
            )
            CommandResult.Success("Яскравість: $target/255")
        } catch (e: Exception) {
            CommandResult.Error("Яскравість: потрібен дозвіл WRITE_SETTINGS")
        }
    }


    private fun toggleWifi(enabled: Boolean): CommandResult {
        return try {
            val current = systemManager.isWifiEnabled()
            if (current == enabled) {
                return CommandResult.Success("WiFi вже ${if (enabled) "увімкнено" else "вимкнено"}")
            }
            // Запускаємо у фоні
            kotlinx.coroutines.GlobalScope.launch {
                systemManager.setWifiEnabled(enabled)
            }
            CommandResult.Success("WiFi ${if (enabled) "увімкнено" else "вимкнено"}")
        } catch (e: Exception) {
            CommandResult.Error("WiFi: ${e.message}")
        }
    }

    private fun toggleBluetooth(enabled: Boolean): CommandResult {
        return try {
            val current = systemManager.isBluetoothEnabled()
            if (current == enabled) {
                return CommandResult.Success("Bluetooth вже ${if (enabled) "увімкнено" else "вимкнено"}")
            }
            kotlinx.coroutines.GlobalScope.launch {
                systemManager.setBluetoothEnabled(enabled)
            }
            CommandResult.Success("Bluetooth ${if (enabled) "увімкнено" else "вимкнено"}")
        } catch (e: Exception) {
            CommandResult.Error("Bluetooth: ${e.message}")
        }
    }

    private fun toggleDnd(enabled: Boolean): CommandResult {
        return try {
            val ok = systemManager.setDnd(enabled)
            if (ok) {
                CommandResult.Success("Не турбувати ${if (enabled) "увімкнено" else "вимкнено"}")
            } else {
                CommandResult.Error("Потрібен дозвіл Не турбувати")
            }
        } catch (e: Exception) {
            CommandResult.Error("DND: ${e.message}")
        }
    }


    private fun handleToggle(target: String, enabled: Boolean): CommandResult {
        return try {
            kotlinx.coroutines.runBlocking {
                val result = when (target) {
                    "wifi" -> quickManager.setWifi(enabled)
                    "bluetooth" -> quickManager.setBluetooth(enabled)
                    "mobile_data" -> quickManager.setMobileData(enabled)
                    "airplane" -> quickManager.setAirplaneMode(enabled)
                    "location" -> quickManager.setLocation(enabled)
                    "nfc" -> quickManager.setNfc(enabled)
                    "dnd" -> quickManager.setDnd(enabled)
                    "auto_rotate" -> quickManager.setAutoRotate(enabled)
                    "battery_saver" -> quickManager.setBatterySaver(enabled)
                    "auto_brightness" -> quickManager.setBrightnessAuto(enabled)
                    else -> com.dynamicai.launcher.system.QuickSettingsManager.Result.failure("Невідомо: $target")
                }
                when (result) {
                    is com.dynamicai.launcher.system.QuickSettingsManager.Result.success -> {
                        val label = mapOf(
                            "wifi" to "WiFi",
                            "bluetooth" to "Bluetooth",
                            "mobile_data" to "Мобільний інтернет",
                            "airplane" to "Авіарежим",
                            "location" to "Геолокація",
                            "nfc" to "NFC",
                            "dnd" to "Не турбувати",
                            "auto_rotate" to "Автоповорот",
                            "battery_saver" to "Економія батареї",
                            "auto_brightness" to "Автояскравість"
                        )[target] ?: target
                        CommandResult.Success("$label ${if (enabled) "увімкнено" else "вимкнено"}")
                    }
                    is com.dynamicai.launcher.system.QuickSettingsManager.Result.failure -> {
                        CommandResult.Error("${target}: ${result.msg}")
                    }
                }
            }
        } catch (e: Exception) {
            CommandResult.Error("$target: ${e.message}")
        }
    }

}

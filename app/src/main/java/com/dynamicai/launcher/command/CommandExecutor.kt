package com.dynamicai.launcher.command

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import com.dynamicai.launcher.model.AppInfo

class CommandExecutor(private val context: Context) {

    fun execute(cmd: Command, apps: List<AppInfo>): CommandResult = when (cmd) {
        is Command.OpenApp -> resolveOpenApp(cmd.query, apps)
        is Command.WebSearch -> webSearch(cmd.query)
        is Command.Call -> call(cmd.name)
        is Command.SetAlarm -> setAlarm(cmd.time, cmd.label)
        is Command.SetTimer -> setTimer(cmd.duration)
        is Command.OpenSettings -> openSettings(cmd.which)
        is Command.SetVolume -> changeVolume(cmd.level)
        Command.ToggleFlashlight -> toggleFlashlight()
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

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            CommandResult.Success("Будильник на %02d:%02d".format(hour, minute))
        } catch (e: Exception) {
            CommandResult.Error("Не вдалось поставити будильник")
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
        val intent = Intent(Settings.ACTION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            CommandResult.Success("Відкриваю налаштування")
        } catch (e: Exception) {
            CommandResult.Error("Не вдалось відкрити налаштування")
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

            val newState = !flashlightOn
            cm.setTorchMode(id, newState)
            flashlightOn = newState
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

    private var flashlightOn = false
}

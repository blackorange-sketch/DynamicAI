package com.dynamicai.launcher.command

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import com.dynamicai.launcher.model.AppInfo

class CommandExecutor(private val context: Context) {

    fun execute(cmd: Command, apps: List<AppInfo>): CommandResult = when (cmd) {
        is Command.OpenApp -> resolveOpenApp(cmd.query, apps)
        is Command.WebSearch -> webSearch(cmd.query)
        is Command.Call -> call(cmd.name)
        Command.ToggleFlashlight -> toggleFlashlight()
        is Command.Unknown -> CommandResult.Error("Не розумію команду: \"${cmd.raw}\"")
    }

    private fun resolveOpenApp(query: String, apps: List<AppInfo>): CommandResult {
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
        val uri = Uri.parse("tel:${Uri.encode(name)}")
        val intent = Intent(Intent.ACTION_DIAL, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            CommandResult.Success("Набираю: $name")
        } catch (e: Exception) {
            CommandResult.Error("Помилка діалера: ${e.message}")
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

    private var flashlightOn = false
}

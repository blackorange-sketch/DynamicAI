package com.dynamicai.launcher.command

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import com.dynamicai.launcher.model.AppInfo

class CommandExecutor(private val context: Context) {

    fun execute(cmd: Command, apps: List<AppInfo>): String = when (cmd) {
        is Command.OpenApp -> openApp(cmd.query, apps)
        is Command.WebSearch -> webSearch(cmd.query)
        is Command.Call -> call(cmd.name)
        Command.ToggleFlashlight -> toggleFlashlight()
        is Command.Unknown -> "Не розумію команду: \"${cmd.raw}\""
    }

    private fun openApp(query: String, apps: List<AppInfo>): String {
        val target = apps
            .map { it to score(it.label, query) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
            ?: return "Не знайдено застосунок: \"$query\""

        val intent = context.packageManager.getLaunchIntentForPackage(target.packageName)
            ?: return "Не вдалось відкрити ${target.label}"
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            "Відкриваю ${target.label}"
        } catch (e: Exception) {
            "Помилка: ${e.message}"
        }
    }

    private fun score(label: String, query: String): Int {
        val l = label.lowercase()
        val q = query.lowercase()
        return when {
            l == q -> 100
            l.startsWith(q) -> 80
            l.contains(q) -> 60
            else -> 0
        }
    }

    private fun webSearch(query: String): String {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra("query", query)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            "Шукаю: $query"
        } catch (e: Exception) {
            // fallback — відкриваємо в браузері
            val url = "https://www.google.com/search?q=" + Uri.encode(query)
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            "Шукаю: $query"
        }
    }

    private fun call(name: String): String {
        val uri = Uri.parse("tel:${Uri.encode(name)}")
        val intent = Intent(Intent.ACTION_DIAL, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return "Набираю: $name"
    }

    private fun toggleFlashlight(): String {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull { camId ->
                cm.getCameraCharacteristics(camId)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "Ліхтарик недоступний"

            val newState = !flashlightOn
            cm.setTorchMode(id, newState)
            flashlightOn = newState
            if (newState) "Ліхтарик увімкнено" else "Ліхтарик вимкнено"
        } catch (e: Exception) {
            "Помилка ліхтарика: ${e.message}"
        }
    }

    private var flashlightOn = false
}

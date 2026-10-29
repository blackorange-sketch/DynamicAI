package com.dynamicai.launcher.macro

import android.content.Context
import android.media.AudioManager
import android.os.BatteryManager
import com.dynamicai.launcher.voice.OfflineSpeech
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * Виконує макрос. Крок — це або виклик інструмента («toggleWifi(state="on")», префікс TOOL_CALL: необов'язковий),
 * або службова команда:
 *   WAIT: 3                     — пауза в секундах
 *   WAIT_UNTIL: online 20       — чекати умову (макс. секунд, типово 15)
 *   IF: умова ? крок            — виконати крок, лише якщо умова істинна
 *   IF_NOT: умова ? крок        — лише якщо хибна
 *   SAY: текст                  — повідомлення (у чат / озвучення)
 * Умови: online, wifi_on, bt_on, bt_connected, airplane, charging, music, battery<N, battery>N, time>HH:MM, time<HH:MM
 */
class MacroRunner(
    private val context: Context,
    private val exec: suspend (String) -> String,
    private val say: (String) -> Unit = {},
    private val vars: MutableMap<String, String> = mutableMapOf()
) {
    data class Progress(val index: Int, val total: Int, val step: String)
    private data class StepResult(val text: String, val failed: Boolean = false)

    companion object {
        val CONDITIONS = "online, wifi_on, bt_on, bt_connected, airplane, charging, music, battery<20, time>18:00"
        private val FAIL_PREFIXES = listOf("Помилка", "Не вдалося", "Немає", "Не можу", "Таймаут", "Не знайдено", "Не встановлено")
        fun isControl(step: String): Boolean {
            val s = step.trim()
            return s.startsWith("WAIT:") || s.startsWith("WAIT_UNTIL:") || s.startsWith("IF:") || s.startsWith("IF_NOT:") ||
                s.startsWith("SAY:") || s.startsWith("SET:") || s.startsWith("REPEAT:")
        }
    }

    suspend fun run(macro: Macro, onProgress: (Progress) -> Unit = {}): String {
        val log = StringBuilder()
        val total = macro.steps.size
        for ((i, raw) in macro.steps.withIndex()) {
            onProgress(Progress(i + 1, total, raw))
            val r = try { runStep(raw) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                StepResult("Помилка: ${e.message}", true)
            }
            log.append(i + 1).append(". ").append(r.text).append('\n')
            if (r.failed && macro.stopOnError) { log.append("⛔ Зупинено через помилку\n"); break }
            if (i < total - 1 && !isControl(raw)) delay(macro.delayMs)
        }
        return log.toString().trim()
    }

    /** Підставляє {змінні}: свої (SET), {last} — результат попереднього кроку, {time}, {date}, {battery}, {trigger}, {device}. */
    private fun substitute(s: String): String {
        if (!s.contains('{')) return s
        return Regex("\\{(\\w+)\\}").replace(s) { m ->
            val k = m.groupValues[1]
            vars[k] ?: when (k) {
                "time" -> java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                "date" -> java.text.SimpleDateFormat("d MMMM", java.util.Locale("uk", "UA")).format(java.util.Date())
                "battery" -> try { (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).toString() } catch (_: Throwable) { "?" }
                else -> m.value
            }
        }
    }

    private suspend fun runStep(raw: String): StepResult {
        val s = substitute(raw.trim())
        when {
            s.startsWith("SET:") -> {
                val body = s.removePrefix("SET:").trim()
                val eq = body.indexOf('=')
                if (eq <= 0) return StepResult("Помилка: SET потребує імя=значення", true)
                val k = body.substring(0, eq).trim()
                val v = body.substring(eq + 1).trim()
                vars[k] = v
                return StepResult("$k = $v")
            }
            s.startsWith("REPEAT:") -> {
                val body = s.removePrefix("REPEAT:").trim()
                val q = body.indexOf('?')
                if (q < 0) return StepResult("Помилка: у REPEAT немає «?»", true)
                val n = (body.substring(0, q).trim().toIntOrNull() ?: 1).coerceIn(1, 50)
                val inner = body.substring(q + 1).trim()
                var last = StepResult("")
                repeat(n) { i ->
                    last = runStep(inner)
                    if (i < n - 1) delay(400)
                }
                return StepResult("×$n: ${last.text}", last.failed)
            }
            s.startsWith("WAIT_UNTIL:") -> {
                val parts = s.removePrefix("WAIT_UNTIL:").trim().split(Regex("\\s+"), limit = 2)
                val cond = parts[0]
                val timeout = (parts.getOrNull(1)?.trim()?.toDoubleOrNull() ?: 15.0).coerceIn(1.0, 120.0)
                var waited = 0L
                while (waited < timeout * 1000) {
                    if (check(cond)) return StepResult("Дочекався: $cond")
                    delay(500); waited += 500
                }
                return if (check(cond)) StepResult("Дочекався: $cond") else StepResult("Таймаут: $cond", true)
            }
            s.startsWith("WAIT:") -> {
                val sec = s.removePrefix("WAIT:").trim().replace(',', '.').toDoubleOrNull() ?: 1.0
                delay((sec.coerceIn(0.0, 300.0) * 1000).toLong())
                return StepResult("Пауза $sec с")
            }
            s.startsWith("IF:") || s.startsWith("IF_NOT:") -> {
                val negate = s.startsWith("IF_NOT:")
                val body = s.substringAfter(':').trim()
                val q = body.indexOf('?')
                if (q < 0) return StepResult("Помилка: у IF немає «?»", true)
                val cond = body.substring(0, q).trim()
                val rest = body.substring(q + 1).trim()
                val elseAt = rest.indexOf(" || ")
                val thenStep = if (elseAt >= 0) rest.substring(0, elseAt).trim() else rest
                val elseStep = if (elseAt >= 0) rest.substring(elseAt + 4).trim() else ""
                val ok = check(cond) != negate
                return when {
                    ok -> runStep(thenStep)
                    elseStep.isNotBlank() -> runStep(elseStep)
                    else -> StepResult("Пропущено (${if (negate) "НЕ " else ""}$cond)")
                }
            }
            s.startsWith("SAY:") -> {
                val t = s.removePrefix("SAY:").trim()
                say(t)
                return StepResult(t)
            }
        }
        val res = exec(normalize(s))
        vars["last"] = res.take(500)
        return StepResult(res, FAIL_PREFIXES.any { res.startsWith(it) })
    }

    /** Прибирає TOOL_CALL: і додає лапки до аргументів без лапок: setVolume(level=80) → setVolume(level="80"). */
    private fun normalize(step: String): String {
        var s = step.trim().removePrefix("TOOL_CALL:").trim()
        if (!s.contains('"')) {
            s = Regex("(\\w+)=([^,)\\s][^,)]*)").replace(s) { "${it.groupValues[1]}=\"${it.groupValues[2].trim()}\"" }
        }
        return s
    }

    fun check(condition: String): Boolean {
        val c = condition.trim().lowercase()
        return try {
            when {
                c == "online" -> OfflineSpeech.isOnline(context)
                c == "wifi_on" -> (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager).isWifiEnabled
                c == "bt_on" -> android.bluetooth.BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
                c == "bt_connected" -> com.dynamicai.launcher.system.BluetoothManager(context).getPairedDevices().any { it.isConnected }
                c == "airplane" -> android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.AIRPLANE_MODE_ON, 0) == 1
                c == "charging" -> (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).isCharging
                c == "music" -> (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).isMusicActive
                c.startsWith("battery") -> {
                    val level = (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    val n = c.drop(8).toIntOrNull() ?: return false
                    if (c[7] == '<') level < n else level > n
                }
                c.startsWith("time") -> {
                    val hm = c.drop(5).split(':')
                    val target = (hm.getOrNull(0)?.toIntOrNull() ?: return false) * 60 + (hm.getOrNull(1)?.toIntOrNull() ?: 0)
                    val now = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
                    if (c[4] == '<') now < target else now > target
                }
                else -> false
            }
        } catch (_: Throwable) { false }
    }
}

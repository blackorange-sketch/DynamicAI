package com.dynamicai.launcher.smarthome

import android.content.Context
import com.dynamicai.launcher.data.SettingsStorage
import org.json.JSONObject

/** Керування роботом-пилососом Xiaomi по локальній мережі: miot (Dreame/Mi Robot Vacuum-Mop) або Roborock. */
class VacuumControl(private val context: Context) {

    private fun ok(resp: String): Boolean = !resp.contains("\"error\"")

    private fun miotAction(siid: Int, aiid: Int) = """{"did":"call-$siid-$aiid","siid":$siid,"aiid":$aiid,"in":[]}"""

    private suspend fun miot(c: MiioClient, action: String): String? = when (action) {
        "start" -> c.call("action", miotAction(2, 1))
        "pause", "stop" -> c.call("action", miotAction(2, 2))
        "home" -> c.call("action", miotAction(3, 1))
        "locate" -> c.call("action", miotAction(7, 1))
        "status" -> c.call("get_properties", """[{"did":"s","siid":2,"piid":1},{"did":"b","siid":3,"piid":1}]""")
        else -> null
    }

    private suspend fun roborock(c: MiioClient, action: String): String? = when (action) {
        "start" -> c.call("app_start", "[]")
        "pause" -> c.call("app_pause", "[]")
        "stop" -> c.call("app_stop", "[]")
        "home" -> c.call("app_charge", "[]")
        "locate" -> c.call("find_me", "[]")
        "status" -> c.call("get_status", "[]")
        else -> null
    }

    private fun miotStatus(r: String): String = try {
        val arr = JSONObject(r).getJSONArray("result")
        val st = arr.optJSONObject(0)?.optInt("value", -1) ?: -1
        val bat = arr.optJSONObject(1)?.optInt("value", -1) ?: -1
        val name = when (st) {
            1 -> "прибирає"; 2 -> "очікує"; 3 -> "на паузі"; 4 -> "помилка"; 5 -> "їде на базу"; 6 -> "заряджається"; 7 -> "миє підлогу"
            else -> "стан $st"
        }
        "Пилосос: $name" + (if (bat in 0..100) ", заряд $bat%" else "")
    } catch (_: Throwable) { "Пилосос відповів: ${r.take(200)}" }

    private fun roborockStatus(r: String): String = try {
        val o = JSONObject(r).getJSONArray("result").getJSONObject(0)
        val name = when (o.optInt("state")) {
            5 -> "прибирає"; 8 -> "заряджається"; 6 -> "повертається на базу"; 10 -> "на паузі"; 3 -> "очікує"; 1, 2 -> "запускається"
            else -> "стан ${o.optInt("state")}"
        }
        "Пилосос: $name, заряд ${o.optInt("battery")}%"
    } catch (_: Throwable) { "Пилосос відповів: ${r.take(200)}" }

    suspend fun run(actionRaw: String): String {
        val s = SettingsStorage(context).load()
        if (s.vacuumIp.isBlank() || s.vacuumToken.isBlank())
            return "Пилосос не налаштовано: Налаштування → Система → 🧹 Пилосос (IP і токен)"
        val action = when (actionRaw.lowercase().trim()) {
            "", "start", "старт", "clean" -> "start"
            "stop", "стоп" -> "stop"
            "pause", "пауза" -> "pause"
            "home", "dock", "charge", "додому" -> "home"
            "locate", "find", "знайти" -> "locate"
            "status", "статус" -> "status"
            else -> return "Невідома дія пилососа: $actionRaw"
        }
        val client = try { MiioClient(context, s.vacuumIp, s.vacuumToken) } catch (t: IllegalArgumentException) { return t.message ?: "Некоректний токен" }
        val order = when (s.vacuumProto) { "miot" -> listOf("miot"); "roborock" -> listOf("roborock"); else -> listOf("miot", "roborock") }
        var lastErr = ""
        for (proto in order) {
            try {
                val r = (if (proto == "miot") miot(client, action) else roborock(client, action)) ?: continue
                if (!ok(r)) { lastErr = r.take(160); continue }
                return when (action) {
                    "status" -> if (proto == "miot") miotStatus(r) else roborockStatus(r)
                    "start" -> "Пилосос запущено"
                    "pause" -> "Пилосос на паузі"
                    "stop" -> "Пилосос зупинено"
                    "home" -> "Пилосос їде на базу"
                    else -> "Пилосос подає сигнал"
                }
            } catch (t: Throwable) { lastErr = t.message ?: "помилка"; if (t is java.io.IOException) break }
        }
        return "Не вдалося керувати пилососом: $lastErr"
    }
}

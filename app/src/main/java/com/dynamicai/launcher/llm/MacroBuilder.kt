package com.dynamicai.launcher.llm

import android.content.Context
import com.dynamicai.launcher.macro.Macro
import com.dynamicai.launcher.macro.MacroStorage
import com.dynamicai.launcher.macro.MacroTriggerService
import org.json.JSONObject

/**
 * Створення макросів з природної мови: «коли підключаюсь до авто, вмикай музику» → JSON → перевірка → підтвердження → збереження.
 */
object MacroBuilder {

    private val CREATE = Regex("(зроби|створи|додай|налаштуй|запрограмуй)\\s+(мені\\s+)?(новий\\s+)?(макрос|автоматизаці|сценарій)")
    private val AUTOMATION = Regex("^(коли|щоразу|як\\s+тільки|щойно|щодня|кожного\\s+(дня|ранку|вечора)|у\\s+будні|по\\s+(буднях|вихідних)|о\\s+\\d)\\s.+")
    private val ACTION_WORDS = Regex("вмик|вимк|включ|запуск|відкр|став|постав|озвуч|нагад|надішл|відправ|вібр|тих|гучн|яскрав|музик|режим|сповіст|скажи|повідом")

    fun looksLikeRequest(question: String): Boolean {
        val t = question.lowercase().trim()
        if (CREATE.containsMatchIn(t)) return true
        if (t.endsWith("?")) return false
        return AUTOMATION.containsMatchIn(t) && t.contains(',') && ACTION_WORDS.containsMatchIn(t)
    }

    private val TRIGGER_KINDS = setOf(
        "charging_start", "charging_stop", "battery_low", "bt_connected", "bt_disconnected", "wifi_connected", "wifi_disconnected",
        "headset_plugged", "headset_unplugged", "screen_on", "screen_off", "unlock", "airplane_on", "airplane_off", "time", "boot",
        "app_opened", "app_closed", "notification", "sms_received", "call_incoming", "call_ended", "nfc", "shake", "bt_on", "bt_off",
        "battery_full", "every"
    )
    private val TOOLS = setOf(
        "openApp", "closeApp", "toggleWifi", "toggleBluetooth", "connectBluetooth", "ensureInternet", "playYouTubeMusic", "playYouTube",
        "mediaKey", "setVolume", "toggleFlashlight", "setBrightness", "setDnd", "setRingerMode", "setMobileData", "setAirplane",
        "vibrate", "notify", "speak", "takeNote", "getWeather", "getNews", "getTime", "getBattery", "searchWeb", "navigateTo",
        "openUrl", "carMode", "systemAction", "openCamera", "setAlarm", "runMacro", "sendSms", "copyText", "toggleNfc", "toggleLocation",
        "setAutoRotate", "screenOn", "screenOff", "screenshot", "pressKey", "expandNotifications", "expandQuickSettings", "collapsePanels",
        "setDarkMode", "setBatterySaver", "setAutoBrightness", "setScreenTimeout", "setStayAwake", "setNightLight", "setAnimations",
        "setFontScale", "vacuum", "killBackground", "clearCache", "openSettings"
    )
    private val CONTROL = listOf("WAIT:", "WAIT_UNTIL:", "IF:", "IF_NOT:", "SAY:", "SET:", "REPEAT:")

    private fun prompt(request: String) = """Ти конструктор макросів для Android. Перетвори прохання на JSON і виведи ТІЛЬКИ JSON, без пояснень.
Формат: {"name":"коротка назва","triggers":[],"constraints":"","steps":[]}
triggers — події автозапуску (порожньо [], якщо макрос запускається лише вручну чи голосом):
charging_start, charging_stop, battery_low:20, bt_connected:НазваПристрою, bt_disconnected:НазваПристрою, wifi_connected, wifi_disconnected, headset_plugged, headset_unplugged, screen_on, screen_off, unlock, airplane_on, airplane_off, bt_on, bt_off, battery_full, call_incoming, call_ended, shake, nfc, every:30 (кожні N хвилин), app_opened:НазваДодатка, app_closed:НазваДодатка, notification:НазваДодатка, sms_received, time:07:30:12345 (година:хвилини:дні, 1=Пн…7=Нд, дні можна пропустити), boot
constraints — умови через кому: online, wifi_on, bt_on, bt_connected, charging, music, battery<20, time>18:00
steps — кроки по порядку (лапки тільки одинарні):
toggleWifi(state='on'), toggleBluetooth(state='off'), connectBluetooth(name='...'), ensureInternet(), openApp(name='...'), playYouTubeMusic(), mediaKey(key='play'|'pause'|'next'|'prev'), setVolume(level='60'), toggleFlashlight(state='on'), setBrightness(percent='40'), setDnd(state='on'), setRingerMode(mode='normal'|'vibrate'|'silent'), setMobileData(state='on'), setAirplane(state='off'), vibrate(ms='400'), notify(title='...', text='...'), speak(text='...'), takeNote(text='...'), getWeather(city='...'), getNews(), searchWeb(query='...'), navigateTo(destination='...'), openUrl(url='...'), carMode(), setAlarm(hour='7', minute='0', message='...'), WAIT: 3, WAIT_UNTIL: bt_connected 20, IF: умова ? крок, SAY: текст, sendSms(number='+380...', text='...'), copyText(text='...'), toggleNfc(state='on'), toggleLocation(state='on'), setAutoRotate(state='on'), setDarkMode(state='on'|'off'|'auto'), setBatterySaver(state='on'), setAutoBrightness(state='on'), setNightLight(state='on'), setScreenTimeout(seconds='60'), screenOn(), screenOff(), screenshot(), pressKey(key='home'|'back'|'recents'|'power'), expandNotifications(), killBackground(), clearCache(), vacuum(action='start'|'pause'|'home'|'locate'), openSettings(page='wifi'|'bluetooth'|'display'|'battery'|'apps')
У кроках доступні змінні подій: {app} {title} {text} (сповіщення), {sender} {body} (SMS), {device}

Прохання: коли підключаюсь до Bluetooth авто, вмикай музику
{"name":"Музика в авто","triggers":["bt_connected:авто"],"constraints":"","steps":["playYouTubeMusic()"]}
Прохання: щодня о 22:30 вмикай не турбувати і вимикай wifi
{"name":"Нічний режим","triggers":["time:22:30"],"constraints":"","steps":["setDnd(state='on')","toggleWifi(state='off')"]}
Прохання: коли заряд нижче 15, скажи про це і вимкни яскравість
{"name":"Економія","triggers":["battery_low:15"],"constraints":"","steps":["notify(title='Батарея', text='Заряд нижче 15%')","setBrightness(percent='20')"]}
Прохання: $request
"""

    data class Draft(val macro: Macro, val preview: String)

    /** Повертає чернетку макросу або null, якщо модель не дала валідного JSON. */
    suspend fun build(context: Context, llm: LlmEngine, request: String): Draft? {
        val q = prompt(request.trim())
        val template = if (llm.isMnn) PromptManager.loadMnn(context) else PromptManager.loadLitert(context)
        val full = if (!llm.isMnn && template.contains("<start_of_turn>")) q else template.replace("{{question}}", q)
        val raw = llm.complete(full)?.trim() ?: return null
        return parse(context, raw)
    }

    fun parse(context: Context, raw: String): Draft? {
        val a = raw.indexOf('{'); val b = raw.lastIndexOf('}')
        if (a < 0 || b <= a) return null
        val o = try { JSONObject(raw.substring(a, b + 1)) } catch (_: Throwable) { return null }
        val carBt = com.dynamicai.launcher.data.SettingsStorage(context).load().carBtName

        val triggers = mutableListOf<String>()
        o.optJSONArray("triggers")?.let { arr ->
            for (i in 0 until arr.length()) {
                var t = arr.optString(i).trim().replace(" ", "")
                val kind = t.substringBefore(':')
                if (kind !in TRIGGER_KINDS) continue
                if (kind == "time" && !Regex("time:\\d{1,2}:\\d{2}(:\\d*)?").matches(t)) continue
                if (kind == "app_opened" || kind == "app_closed" || kind == "notification") {
                    val arg = t.substringAfter(':', "")
                    val name = arg.substringBefore(':')
                    val pkg = if (name.contains('.')) name else com.dynamicai.launcher.macro.AppUtils.packageFor(context, name) ?: continue
                    t = "$kind:$pkg" + (if (arg.contains(':')) ":" + arg.substringAfter(':') else "")
                }
                if (kind == "bt_connected" || kind == "bt_disconnected") {
                    val arg = t.substringAfter(':', "")
                    if (arg.lowercase() in setOf("авто", "машина", "машини", "car", "автомобіля", "авто.")) t = "$kind:$carBt"
                }
                if (t !in triggers) triggers.add(t)
            }
        }
        val steps = mutableListOf<String>()
        o.optJSONArray("steps")?.let { arr ->
            for (i in 0 until arr.length()) {
                var s = arr.optString(i).trim()
                if (s.isBlank()) continue
                s = Regex("'([^']*)'").replace(s) { "\"" + it.groupValues[1] + "\"" }
                val ok = CONTROL.any { s.startsWith(it) } || s.substringBefore('(') in TOOLS
                if (ok) steps.add(s)
            }
        }
        if (steps.isEmpty()) return null
        val name = o.optString("name", "").trim().take(40).ifBlank { "Макрос" }
        val constraints = o.optString("constraints", "").trim()
        val macro = Macro(
            id = java.util.UUID.randomUUID().toString(),
            name = name,
            trigger = if (triggers.isEmpty()) name.lowercase() else "",
            steps = steps,
            triggers = triggers,
            constraints = constraints
        )
        val preview = buildString {
            append("Макрос «").append(name).append("»\n")
            if (triggers.isNotEmpty()) append("Коли: ").append(triggers.joinToString(", ")).append('\n')
            else append("Запуск: голосом «").append(name.lowercase()).append("»\n")
            if (constraints.isNotBlank()) append("Умови: ").append(constraints).append('\n')
            steps.forEachIndexed { i, s -> append(i + 1).append(". ").append(s).append('\n') }
        }.trim()
        return Draft(macro, preview)
    }

    fun save(context: Context, macro: Macro) {
        MacroStorage(context).add(macro)
        MacroTriggerService.sync(context.applicationContext)
    }
}

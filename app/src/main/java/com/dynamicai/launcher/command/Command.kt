package com.dynamicai.launcher.command

sealed class Command {
    // ==== Застосунки та взаємодія ====
    data class OpenApp(val query: String) : Command()
    data class WebSearch(val query: String) : Command()
    data class Call(val name: String) : Command()
    data class DeepLink(val app: String, val target: String) : Command()
    data class SearchInApp(val packageKeyword: String, val query: String) : Command()
    data class AskQuestion(val query: String) : Command()
    data class Note(val text: String) : Command()
    data class SendSms(val contact: String, val text: String?) : Command()
    data class SendEmail(val to: String, val subject: String?) : Command()
    data class MediaControl(val action: String) : Command()   // play, pause, next, prev, stop
    data class SetVolumeExact(val percent: Int) : Command()
    object BatteryStatus : Command()
    data class Calculate(val expr: String) : Command()

    // ==== Час ====
    data class SetAlarm(val time: String, val label: String?) : Command()
    data class SetTimer(val duration: String) : Command()

    // ==== Налаштування ====
    data class OpenSettings(val which: String?) : Command()

    // ==== Звук та медіа ====
    data class SetVolume(val level: Int) : Command()
    data class SetBrightness(val level: Int) : Command()

    // ==== Toggle пристроїв ====
    data class SetToggle(val target: String, val enabled: Boolean) : Command()
    // target: "wifi", "bluetooth", "mobile_data", "airplane", "location", "nfc", "dnd", "auto_rotate", "battery_saver"

    // ==== Окремі toggle (для сумісності) ====
    object WifiOn : Command()
    object WifiOff : Command()
    object BluetoothOn : Command()
    object BluetoothOff : Command()
    object DndOn : Command()
    object DndOff : Command()

    // ==== Просте ====
    object ToggleFlashlight : Command()
    object Screenshot : Command()
    object Unknown : Command()
    data class Raw(val text: String) : Command()
    data class Compound(val steps: List<Command>, val delayMs: Long = 900L) : Command()
}

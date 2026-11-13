package com.dynamicai.launcher.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicai.launcher.accessibility.AppAutomationService
import com.dynamicai.launcher.macro.TriggerPermissions
import com.dynamicai.launcher.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Один дозвіл у списку. [runtime] — звичайні дозволи Android (запитуються системним вікном);
 * [settingsIntent] — особливий доступ, який вмикається лише на сторінці налаштувань системи.
 */
private data class PermItem(
    val title: String,
    val why: String,
    val granted: Boolean,
    val runtime: List<String> = emptyList(),
    val settingsIntent: Intent? = null,
    val shizuku: Boolean = false,
    val note: String = ""
)

private fun has(ctx: Context, perm: String) = ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

private fun pkgUri(ctx: Context) = Uri.parse("package:" + ctx.packageName)

/** Звичайні дозволи з урахуванням версії Android. */
private fun runtimeItems(ctx: Context): List<PermItem> {
    val sdk = Build.VERSION.SDK_INT
    fun item(title: String, why: String, vararg perms: String) =
        PermItem(title, why, perms.all { has(ctx, it) }, runtime = perms.toList())
    val out = mutableListOf(
        item("Мікрофон", "Голосовий ввід і фраза активації", Manifest.permission.RECORD_AUDIO),
        item("Контакти", "Пошук контактів, дзвінки й повідомлення за іменем", Manifest.permission.READ_CONTACTS),
        item("Телефон", "Дзвінки з пошуку та голосом", Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE),
        item("SMS", "Надсилання SMS і макроси на вхідні повідомлення", Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS),
        item("Календар", "Підказки про найближчі події, створення подій", Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
        item("Місцезнаходження", "Погода за поточним місцем, макроси за місцем", Manifest.permission.ACCESS_FINE_LOCATION),
        item("Камера", "Ліхтарик і режими камери", Manifest.permission.CAMERA)
    )
    if (sdk >= 33) {
        out += item("Сповіщення", "Нагадування, стан завантаження моделей, макроси", Manifest.permission.POST_NOTIFICATIONS)
        out += item("Фото", "Пошук фото за назвою, фон лаунчера", Manifest.permission.READ_MEDIA_IMAGES)
    } else {
        out += item("Файли й фото", "Пошук фото за назвою, фон лаунчера", Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    if (sdk >= 31) {
        out += item("Bluetooth", "Підключення до авто й навушників", Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    }
    return out
}

/** Особливий доступ: вмикається на окремих сторінках налаштувань Android. */
private fun specialItems(ctx: Context): List<PermItem> {
    val sdk = Build.VERSION.SDK_INT
    fun open(action: String, withPackage: Boolean = false) =
        (if (withPackage) Intent(action, pkgUri(ctx)) else Intent(action)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val out = mutableListOf<PermItem>()
    val isHome = try {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        ctx.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == ctx.packageName
    } catch (_: Throwable) { false }
    out += PermItem("Лаунчер за замовчуванням", "Щоб кнопка «Додому» відкривала DynamicAI", isHome,
        settingsIntent = open(Settings.ACTION_HOME_SETTINGS))
    out += PermItem("Показ поверх інших вікон", "Плаваючий док, асистент і переклад екрана", Settings.canDrawOverlays(ctx),
        settingsIntent = open(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, true))
    out += PermItem("Спеціальні можливості", "Натискання, прокрутка й читання екрана асистентом", AppAutomationService.isEnabled(),
        settingsIntent = open(Settings.ACTION_ACCESSIBILITY_SETTINGS), note = "Увімкніть «DynamicAI Automation»")
    out += PermItem("Доступ до сповіщень", "Бейджі на іконках, підсумок сповіщень, макроси", TriggerPermissions.hasNotificationListener(ctx),
        settingsIntent = open(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    out += PermItem("Статистика використання", "Список недавніх і найуживаніших застосунків", TriggerPermissions.hasUsageAccess(ctx),
        settingsIntent = open(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    out += PermItem("Зміна системних налаштувань", "Яскравість та автоповорот голосом і з макросів", Settings.System.canWrite(ctx),
        settingsIntent = open(Settings.ACTION_MANAGE_WRITE_SETTINGS, true))
    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    out += PermItem("Керування «Не турбувати»", "Вмикання режиму голосом, з макросів і підказок", nm.isNotificationPolicyAccessGranted,
        settingsIntent = open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
    if (sdk >= 31) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        out += PermItem("Точні будильники", "Нагадування й макроси за розкладом хвилина в хвилину", am.canScheduleExactAlarms(),
            settingsIntent = open(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, true))
    }
    val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
    out += PermItem("Робота у фоні без обмежень", "Щоб система не зупиняла макроси та фразу активації", pm.isIgnoringBatteryOptimizations(ctx.packageName),
        settingsIntent = open(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, true))
    val shizukuRunning = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
    val shizukuOk = shizukuRunning && try { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (_: Throwable) { false }
    out += PermItem("Shizuku", "Знімки екрана, перемикачі системи, закриття застосунків", shizukuOk, shizuku = true,
        note = if (shizukuRunning) "" else "Спершу запустіть застосунок Shizuku")
    return out
}

@Composable
private fun PermRow(item: PermItem, onGrant: () -> Unit) {
    val c = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(if (item.granted) c.success else c.warning))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(item.why, color = c.textMuted, fontSize = 12.sp, lineHeight = 16.sp)
            if (!item.granted && item.note.isNotBlank()) Text(item.note, color = c.warning, fontSize = 11.sp)
        }
        Spacer(Modifier.width(8.dp))
        if (item.granted) {
            Text("Надано", color = c.success, fontSize = 13.sp)
        } else {
            Text(
                "Надати",
                color = c.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, c.accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .clickable(onClick = onGrant)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}

/** Налаштування → Дозволи: стан усіх дозволів, потрібних лаунчеру, і кнопки, щоб їх надати. */
@Composable
internal fun ColumnScope.SettingsPermissionsSection() {
    val ctx = LocalContext.current
    var runtime by remember { mutableStateOf(runtimeItems(ctx)) }
    var special by remember { mutableStateOf<List<PermItem>>(emptyList()) }
    // Стан перевіряється, поки екран відкритий: дозвіл, наданий у системних налаштуваннях, одразу позначається
    LaunchedWhileStarted {
        while (true) {
            runtime = runtimeItems(ctx)
            special = withContext(Dispatchers.Default) { try { specialItems(ctx) } catch (_: Throwable) { special } }
            delay(1500)
        }
    }
    fun openAppDetails() {
        try {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(ctx)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {}
    }
    // Якщо системне вікно не з'явилось (дозвіл раніше відхилено «назавжди») — відкриваємо сторінку застосунку
    var askedAt by remember { mutableStateOf(0L) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        runtime = runtimeItems(ctx)
        val instant = System.currentTimeMillis() - askedAt < 400
        if (result.values.any { !it } && instant) openAppDetails()
    }
    fun ask(perms: List<String>) {
        askedAt = System.currentTimeMillis()
        try { launcher.launch(perms.toTypedArray()) } catch (_: Throwable) { openAppDetails() }
    }
    fun grant(item: PermItem) {
        when {
            item.runtime.isNotEmpty() -> ask(item.runtime)
            item.shizuku -> try {
                if (Shizuku.pingBinder()) Shizuku.requestPermission(4711)
                else ctx.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                    ?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            } catch (_: Throwable) {}
            item.settingsIntent != null -> try { ctx.startActivity(item.settingsIntent) } catch (_: Throwable) { openAppDetails() }
        }
    }

    val all = runtime + special
    val done = all.count { it.granted }
    GlassSectionHeader("Дозволи")
    GlassCard {
        Column(Modifier.padding(16.dp)) {
            Text("Надано $done із ${all.size}", color = AppTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text(
                "Без дозволу не працює лише пов'язана з ним функція. Звичайні дозволи можна надати всі разом; " +
                    "особливий доступ вмикається по одному на сторінках налаштувань Android.",
                color = AppTheme.colors.textMuted, fontSize = 12.sp, lineHeight = 16.sp
            )
            val missing = runtime.filter { !it.granted }.flatMap { it.runtime }
            if (missing.isNotEmpty()) {
                Spacer(Modifier.width(1.dp))
                Text(
                    "Надати всі звичайні дозволи",
                    color = AppTheme.colors.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, AppTheme.colors.accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .clickable { ask(missing) }
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                )
            }
        }
    }
    GlassSectionHeader("Звичайні дозволи")
    GlassCard { runtime.forEach { item -> PermRow(item) { grant(item) } } }
    GlassSectionHeader("Особливий доступ")
    GlassCard { special.forEach { item -> PermRow(item) { grant(item) } } }
}

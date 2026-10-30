package com.dynamicai.launcher.widget

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.dynamicai.launcher.system.SystemStats
import com.dynamicai.launcher.system.SystemStats.ProcRow
import com.dynamicai.launcher.ui.LaunchedWhileStarted
import com.dynamicai.launcher.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun levelColor(frac: Float): Color = when {
    frac < 0.6f -> Color(0xFF66BB6A)
    frac < 0.85f -> Color(0xFFFFB74D)
    else -> Color(0xFFEF5350)
}

@Composable
private fun StatBar(label: String, value: String, frac: Float, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = AppTheme.colors.textSecondary, fontSize = 11.sp)
            Text(value, color = AppTheme.colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                .background(AppTheme.colors.textPrimary.copy(alpha = 0.1f))
        ) {
            Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().background(color))
        }
    }
}

/** Віджет «Ресурси системи»: тап — докладне вікно (що їсть пам'ять, ЦП, місце, екранний час). */
@Composable
fun SystemWidget(size: String, onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var s by remember { mutableStateOf<SystemStats.Snapshot?>(null) }
    var details by remember { mutableStateOf(false) }

    LaunchedWhileStarted {
        while (true) {
            s = try { SystemStats.snapshot(ctx) } catch (_: Throwable) { s }
            delay(3000)
        }
    }

    val snap = s
    Column(
        modifier = modifier
            .widgetCard()
            .pointerInput(Unit) { detectTapGestures(onTap = { details = true }, onLongPress = { onLongPress() }) }
            .padding(12.dp)
    ) {
        Text("📊 Ресурси · тап — докладніше", color = AppTheme.colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (snap == null) {
            Text("…", color = AppTheme.colors.textMuted, fontSize = 13.sp)
        } else {
            val ramFrac = if (snap.ramTotalMb > 0) snap.ramUsedMb.toFloat() / snap.ramTotalMb else 0f
            val stFrac = if (snap.storageTotalGb > 0) snap.storageUsedGb / snap.storageTotalGb else 0f
            val ram = StatBarData("RAM", "${SystemStats.fmtMb(snap.ramUsedMb)} / ${SystemStats.fmtMb(snap.ramTotalMb)}", ramFrac)
            val cpu = StatBarData("ЦП", if (snap.cpuPct >= 0) "${snap.cpuPct}%" else "—", if (snap.cpuPct >= 0) snap.cpuPct / 100f else 0f)
            val disk = StatBarData("Сховище", "%.0f / %.0f ГБ".format(snap.storageUsedGb, snap.storageTotalGb), stFrac)
            val bat = StatBarData(
                if (snap.charging) "Батарея ⚡" else "Батарея",
                "${snap.batteryPct}% · ${"%.1f".format(snap.batteryTempC)}°C",
                snap.batteryPct / 100f, invert = true
            )
            val bars = if (size == "small") listOf(ram, cpu) else listOf(ram, cpu, disk, bat)
            bars.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { b ->
                        val c = if (b.invert) levelColor(1f - b.frac) else levelColor(b.frac)
                        StatBar(b.label, b.value, b.frac, c, Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (size != "small" && snap.swapTotalMb > 0) {
                Text(
                    "Swap ${SystemStats.fmtMb(snap.swapUsedMb)} / ${SystemStats.fmtMb(snap.swapTotalMb)}" +
                        (if (snap.currentMa > 0) " · струм ${snap.currentMa} мА" else ""),
                    color = AppTheme.colors.textMuted, fontSize = 11.sp
                )
            }
        }
    }
    if (details) SystemDetailsDialog(snap) { details = false }
}

private data class StatBarData(val label: String, val value: String, val frac: Float, val invert: Boolean = false)

private val tabs = listOf("Пам'ять", "ЦП", "Сховище", "Екран")

@Composable
private fun SystemDetailsDialog(snap: SystemStats.Snapshot?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var rows by remember { mutableStateOf<List<ProcRow>?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    var confirmStop by remember { mutableStateOf<ProcRow?>(null) }
    val shizuku = remember { SystemStats.shizukuReady(ctx) }

    LaunchedEffect(tab, reload) {
        loaded = false
        if (tab == 0 || tab == 1) {
            // Живі вкладки оновлюються, поки відкрите вікно
            while (true) {
                rows = if (tab == 0) SystemStats.topMemory(ctx) else SystemStats.topCpu(ctx)
                loaded = true
                delay(4000)
            }
        } else {
            rows = if (tab == 2) SystemStats.storageByApp(ctx) else SystemStats.screenTime(ctx)
            loaded = true
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(AppTheme.colors.surface)
                .padding(16.dp)
        ) {
            Text("Що їсть ресурси", color = AppTheme.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (snap != null) {
                Text(
                    "RAM ${SystemStats.fmtMb(snap.ramUsedMb)} / ${SystemStats.fmtMb(snap.ramTotalMb)}" +
                        (if (snap.cpuPct >= 0) " · ЦП ${snap.cpuPct}%" else "") +
                        " · %.1f°C".format(snap.batteryTempC),
                    color = AppTheme.colors.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tabs.forEachIndexed { i, t ->
                    Text(
                        t, fontSize = 13.sp, maxLines = 1,
                        color = if (i == tab) AppTheme.colors.textPrimary else AppTheme.colors.textSecondary,
                        fontWeight = if (i == tab) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppTheme.colors.textPrimary.copy(alpha = if (i == tab) 0.16f else 0.06f))
                            .clickable { tab = i; rows = null }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            val list = rows
            when {
                !loaded && list == null -> Text("Завантаження…", color = AppTheme.colors.textMuted, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                list == null && (tab == 0 || tab == 1) -> Text(
                    "Список процесів потребує Shizuku. Увімкніть його: Налаштування → Система → Shizuku.",
                    color = AppTheme.colors.textMuted, fontSize = 13.sp, modifier = Modifier.padding(8.dp)
                )
                list == null -> Column(Modifier.padding(8.dp)) {
                    Text(
                        "Потрібен доступ до статистики використання.",
                        color = AppTheme.colors.textMuted, fontSize = 13.sp
                    )
                    Text(
                        "Відкрити налаштування",
                        color = AppTheme.colors.accent, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 8.dp).clickable {
                            try {
                                ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (_: Throwable) {}
                        }
                    )
                }
                list!!.isEmpty() -> Text("Нічого не знайдено", color = AppTheme.colors.textMuted, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                else -> LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(list, key = { it.pkg }) { r ->
                        ProcRowView(
                            r,
                            canStop = shizuku && (tab == 0 || tab == 1) && !SystemStats.isSystemApp(ctx, r.pkg),
                            onStop = { confirmStop = r }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Закрити", color = AppTheme.colors.accent, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.End).clickable { onDismiss() }.padding(8.dp)
            )
        }
    }

    confirmStop?.let { r ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmStop = null },
            title = { Text("Зупинити ${r.label}?") },
            text = { Text("Додаток буде примусово закрито (${r.value}).") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    scope.launch { SystemStats.stopPackage(ctx, r.pkg); confirmStop = null; reload++ }
                }) { Text("Зупинити") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmStop = null }) { Text("Скасувати") } }
        )
    }
}

@Composable
private fun ProcRowView(r: ProcRow, canStop: Boolean, onStop: () -> Unit) {
    val ctx = LocalContext.current
    val icon = remember(r.pkg) {
        try { ctx.packageManager.getApplicationIcon(r.pkg).toBitmap(72, 72).asImageBitmap() } catch (_: Throwable) { null }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Image(icon, null, Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)))
        else Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(AppTheme.colors.textPrimary.copy(alpha = 0.1f)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(r.label, color = AppTheme.colors.textPrimary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (r.sub.isNotBlank()) Text(r.sub, color = AppTheme.colors.textMuted, fontSize = 11.sp, maxLines = 1)
        }
        Text(r.value, color = AppTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        if (canStop) {
            Text(
                "Стоп", color = Color(0xFFEF5350), fontSize = 12.sp,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFEF5350).copy(alpha = 0.12f))
                    .clickable { onStop() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

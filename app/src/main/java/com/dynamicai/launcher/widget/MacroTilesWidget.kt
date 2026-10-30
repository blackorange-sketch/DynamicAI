package com.dynamicai.launcher.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicai.launcher.macro.Macro
import com.dynamicai.launcher.macro.MacroLauncher
import com.dynamicai.launcher.macro.MacroStorage
import com.dynamicai.launcher.suggestion.ContextActions
import com.dynamicai.launcher.suggestion.SmartAction
import com.dynamicai.launcher.ui.theme.AppTheme
import kotlinx.coroutines.delay

/** Плитки макросів: тап — запуск, довгий тап — налаштування віджета. */
@Composable
fun MacroTilesWidget(
    size: String,
    onLongPress: () -> Unit,
    onOpenMacros: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    var macros by remember { mutableStateOf(MacroStorage(ctx).get().filter { it.enabled }) }
    val running by MacroLauncher.running.collectAsState()
    com.dynamicai.launcher.ui.LaunchedWhileStarted {
        while (true) { macros = MacroStorage(ctx).get().filter { it.enabled }; delay(5000) }
    }
    val cols = 3
    val rows = when (size) { "small" -> 1; "large" -> 3; "full" -> 5; else -> 2 }
    val capacity = rows * cols
    val shown: List<Macro> = if (macros.size >= capacity) macros.take(capacity - 1) else macros
    val cells: List<Macro?> = shown + listOf<Macro?>(null)   // null = плитка «+»

    Column(modifier = modifier.widgetCard().padding(12.dp)) {
        Text("⚡ Макроси", color = AppTheme.colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        cells.chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { m ->
                    Box(Modifier.weight(1f)) {
                        if (m == null) MacroTile("＋", "Усі макроси", AppTheme.colors.textSecondary, false, onOpenMacros, onLongPress)
                        else MacroTile(
                            if (m.id in running) "⏳" else m.icon, m.name, Color(m.colorHex), m.id in running,
                            { MacroLauncher.run(ctx, m) }, onLongPress
                        )
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MacroTile(icon: String, name: String, color: Color, busy: Boolean, onTap: () -> Unit, onLong: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = if (busy) 0.35f else 0.18f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .pointerInput(name) { detectTapGestures(onTap = { onTap() }, onLongPress = { onLong() }) }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(icon, fontSize = 22.sp)
        Spacer(Modifier.height(2.dp))
        Text(name, color = AppTheme.colors.textPrimary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** Контекстні пропозиції: підбираються за часом доби, станом телефону та частотою використання макросів. */
@Composable
fun SmartActionsWidget(
    size: String,
    onLongPress: () -> Unit,
    onOpenVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    var actions by remember { mutableStateOf<List<SmartAction>>(emptyList()) }
    com.dynamicai.launcher.ui.LaunchedWhileStarted {
        while (true) {
            actions = try { ContextActions.build(ctx) } catch (_: Throwable) { emptyList() }
            delay(30_000)
        }
    }
    val macrosById = remember(actions) { MacroStorage(ctx).get().associateBy { it.id } }
    fun runAction(a: SmartAction) {
        when {
            a.special == "voice" -> onOpenVoice()
            a.macroId.isNotBlank() -> macrosById[a.macroId]?.let { MacroLauncher.run(ctx, it) }
            a.tool.isNotBlank() -> MacroLauncher.runTool(ctx, a.tool, "action:" + a.key)
        }
    }
    Column(modifier = modifier.widgetCard().pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }.padding(12.dp)) {
        Text("✨ ${ContextActions.greeting()}", color = AppTheme.colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        if (size == "large") {
            actions.chunked(3).forEach { row ->
                Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { ActionChip(it) { runAction(it) } }
                }
            }
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                actions.forEach { ActionChip(it) { runAction(it) } }
            }
        }
    }
}

@Composable
private fun ActionChip(a: SmartAction, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(AppTheme.colors.textPrimary.copy(alpha = 0.08f))
            .border(1.dp, AppTheme.colors.glassBorder.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
            .pointerInput(a.key) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(a.icon, fontSize = 16.sp)
        Spacer(Modifier.width(6.dp))
        Text(a.label, color = AppTheme.colors.textPrimary, fontSize = 13.sp, maxLines = 1)
    }
}

/** Віджет «Фокус»: чіпи режимів (Робота, Сон, Авто, Вільний час) і вимкнення; активний підсвічений. */
@Composable
fun FocusWidget(onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { com.dynamicai.launcher.macro.FocusModes.init(ctx) }
    val active by com.dynamicai.launcher.macro.FocusModes.active.collectAsState()
    Column(modifier = modifier.widgetCard().pointerInput(Unit) { detectTapGestures(onLongPress = { onLongPress() }) }.padding(12.dp)) {
        Text(
            "🎯 Фокус" + (com.dynamicai.launcher.macro.FocusModes.find(active)?.let { " · ${it.name}" } ?: ""),
            color = AppTheme.colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            com.dynamicai.launcher.macro.FocusModes.presets.forEach { m ->
                val on = m.id == active
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (on) AppTheme.colors.accent.copy(alpha = 0.28f) else AppTheme.colors.textPrimary.copy(alpha = 0.08f))
                        .border(1.dp, if (on) AppTheme.colors.accent else AppTheme.colors.glassBorder.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                        .pointerInput(m.id, on) {
                            detectTapGestures(onTap = {
                                if (on) com.dynamicai.launcher.macro.FocusModes.deactivate(ctx)
                                else com.dynamicai.launcher.macro.FocusModes.activate(ctx, m.id)
                            }, onLongPress = { onLongPress() })
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(m.icon, fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(m.name, color = AppTheme.colors.textPrimary, fontSize = 13.sp, maxLines = 1)
                }
            }
        }
    }
}

/** Віджет «Сповіщення»: підсумок за додатками, тап — прочитати вголос. */
@Composable
fun NotificationDigestWidget(onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    com.dynamicai.launcher.ui.LaunchedWhileStarted {
        while (true) {
            text = try { com.dynamicai.launcher.macro.NotificationDigest.build(ctx) } catch (_: Throwable) { "" }
            delay(15_000)
        }
    }
    Column(
        modifier = modifier
            .widgetCard()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { com.dynamicai.launcher.macro.MacroTools(ctx).speak(text) },
                    onLongPress = { onLongPress() }
                )
            }
            .padding(12.dp)
    ) {
        Text("🔔 Сповіщення · тап — прочитати", color = AppTheme.colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(text.ifBlank { "…" }, color = AppTheme.colors.textPrimary, fontSize = 13.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
    }
}

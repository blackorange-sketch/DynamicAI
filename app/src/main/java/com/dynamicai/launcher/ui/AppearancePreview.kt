package com.dynamicai.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicai.launcher.ui.theme.AppTheme

/**
 * Живий попередній перегляд: ті самі IconPlate, шрифт і відступи, що й у шторці/папках/пошуку,
 * тож слайдери вигляду одразу видно без виходу з налаштувань.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GridStylePreview(modifier: Modifier = Modifier) {
    val st = LocalGridStyle.current
    val sample = listOf("📞" to "Телефон", "💬" to "Месенджер", "📷" to "Камера", "🎵" to "Музика",
        "🗺️" to "Карти", "⚙️" to "Налаштування", "🌐" to "Браузер", "📝" to "Нотатки")
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(st.gapHDp.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(st.gapVDp.dp)
    ) {
        sample.forEach { (emoji, label) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(st.cellWidthDp.dp)) {
                IconPlate(st.gridIconDp) {
                    Text(emoji, fontSize = (st.gridIconDp * 0.5f).sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(label, color = AppTheme.colors.textPrimary, fontSize = st.fontSp.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private val AccentPalette = listOf(
    0xFF64B5F6.toInt(), 0xFF4DD0E1.toInt(), 0xFF81C784.toInt(), 0xFFFFD54F.toInt(),
    0xFFFFB74D.toInt(), 0xFFFF8A65.toInt(), 0xFFE57373.toInt(), 0xFFF06292.toInt(),
    0xFFBA68C8.toInt(), 0xFF9575CD.toInt()
)

/** Вибір власного акценту: «авто» = колір теми, інакше один з готових кольорів. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentPicker(current: Int, onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(34.dp).clip(CircleShape)
                .border(if (current == 0) 2.dp else 1.dp, if (current == 0) AppTheme.colors.textPrimary else AppTheme.colors.glassBorder, CircleShape)
                .clickable { onPick(0) },
            contentAlignment = Alignment.Center
        ) { Text("A", color = AppTheme.colors.textPrimary, fontSize = 14.sp) }
        AccentPalette.forEach { c ->
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Color(c))
                    .border(if (current == c) 3.dp else 0.dp, AppTheme.colors.textPrimary, CircleShape)
                    .clickable { onPick(c) }
            )
        }
    }
}

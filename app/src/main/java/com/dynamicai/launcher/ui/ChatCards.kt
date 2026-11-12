package com.dynamicai.launcher.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicai.launcher.ui.theme.AppTheme

/** Відповідь асистента, яку можна показати карткою замість суцільного тексту. */
sealed class ChatCard {
    data class Weather(
        val place: String, val temp: String, val description: String, val feels: String,
        val humidity: String, val wind: String, val min: String, val max: String
    ) : ChatCard()
    data class Currency(val amount: String, val from: String, val result: String, val to: String, val rate: String) : ChatCard()
    data class Route(val destination: String) : ChatCard()
}

private val WEATHER = Regex(
    "^Погода(?: \\((.+?)\\))?: (-?\\d+)°, (.+?)\\. Відчувається як (-?\\d+)°, вологість (\\d+)%, вітер (.+?)\\. Сьогодні від (-?\\d+)° до (-?\\d+)°\\.?$"
)
private val CURRENCY = Regex("^(\\S+) ([A-Z]{3}) = ([\\d.]+) ([A-Z]{3}) \\(курс ([\\d.]+)\\)$")
private val ROUTE = Regex("^Будую маршрут до: (.+)$")

/** Розпізнає відповіді інструментів (погода, курс валют, маршрут) за їхнім сталим форматом. */
fun parseChatCard(text: String): ChatCard? {
    val t = text.trim()
    WEATHER.find(t)?.groupValues?.let { g -> return ChatCard.Weather(g[1], g[2], g[3], g[4], g[5], g[6], g[7], g[8]) }
    CURRENCY.find(t)?.groupValues?.let { g -> return ChatCard.Currency(g[1], g[2], g[3], g[4], g[5]) }
    ROUTE.find(t)?.groupValues?.let { g -> return ChatCard.Route(g[1].trim()) }
    return null
}

private fun weatherEmoji(description: String): String {
    val d = description.lowercase()
    return when {
        "гроз" in d -> "⛈"
        "сніг" in d || "замет" in d -> "❄️"
        "дощ" in d || "злив" in d || "мряк" in d -> "🌧"
        "туман" in d -> "🌫"
        "хмар" in d || "похмур" in d -> "☁️"
        "ясн" in d || "сонячн" in d -> "☀️"
        else -> "🌤"
    }
}

@Composable
private fun Chip(label: String, value: String) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AppTheme.colors.textPrimary.copy(alpha = 0.06f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = AppTheme.colors.textMuted, fontSize = 10.sp)
        Text(value, color = AppTheme.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CardButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = AppTheme.colors.accent,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, AppTheme.colors.accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
fun ChatCardView(card: ChatCard) {
    val ctx = LocalContext.current
    val c = AppTheme.colors
    when (card) {
        is ChatCard.Weather -> Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(weatherEmoji(card.description), fontSize = 40.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("${card.temp}°", color = c.textPrimary, fontSize = 34.sp, fontWeight = FontWeight.Light)
                    Text(
                        card.description.replaceFirstChar { it.uppercase() } + if (card.place.isNotBlank()) " · ${card.place}" else "",
                        color = c.textSecondary, fontSize = 13.sp
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip("Відчувається", "${card.feels}°")
                Chip("Вологість", "${card.humidity}%")
                Chip("Вітер", card.wind)
            }
            Spacer(Modifier.height(6.dp))
            Text("Сьогодні від ${card.min}° до ${card.max}°", color = c.textMuted, fontSize = 12.sp)
        }
        is ChatCard.Currency -> Column {
            Text("${card.amount} ${card.from}", color = c.textSecondary, fontSize = 13.sp)
            Text("${card.result} ${card.to}", color = c.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.Light)
            Text("Курс: 1 ${card.from} = ${card.rate} ${card.to}", color = c.textMuted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            CardButton("Скопіювати") {
                try {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("result", card.result))
                    Toast.makeText(ctx, "Скопійовано: ${card.result}", Toast.LENGTH_SHORT).show()
                } catch (_: Throwable) {}
            }
        }
        is ChatCard.Route -> Column {
            Text("📍 Маршрут", color = c.textSecondary, fontSize = 13.sp)
            Text(card.destination, color = c.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            CardButton("Відкрити карту") {
                try {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(card.destination)))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Throwable) {}
            }
        }
    }
}

package com.dynamicai.launcher.ui

import com.dynamicai.launcher.ui.theme.AppTheme
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dynamicai.launcher.widget.widgetCard
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicai.launcher.data.WeatherState
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeWidget(
    modifier: Modifier = Modifier,
    onSettingsLongPress: (() -> Unit)? = null,
    weather: WeatherState = WeatherState.Loading,
    showWeather: Boolean = true,
    onWeatherTap: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    // Показуємо лише ГГ:ХХ — оновлюємось раз на хвилину (раніше щосекунди, навіть у фоні)
    LaunchedWhileStarted {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - (System.currentTimeMillis() % 60_000L) + 50L)
        }
    }

    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val dateFmt = remember {
        DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("uk", "UA"))
    }
    val dateText = now.format(dateFmt).replaceFirstChar { it.uppercase() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .widgetCard()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSettingsLongPress?.invoke()
                    }
                )
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Ліва колонка: годинник + дата
        Column(modifier = Modifier.weight(1f)) {
            Text(
                now.format(timeFmt),
                color = AppTheme.colors.textPrimary,
                fontSize = 34.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures {
                        try {
                            ctx.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } catch (_: Exception) {
                            try {
                                ctx.packageManager.getLaunchIntentForPackage("com.android.deskclock")
                                    ?.let {
                                        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        ctx.startActivity(it)
                                    }
                            } catch (_: Exception) {}
                        }
                    }
                }
            )
            Text(
                dateText,
                color = AppTheme.colors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures {
                        try {
                            val startMillis = System.currentTimeMillis()
                            val builder = CalendarContract.CONTENT_URI.buildUpon()
                                .appendPath("time")
                                .appendPath(startMillis.toString())
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, builder.build())
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: Exception) {}
                    }
                }
            )
        }

        // Права колонка: погода
        if (showWeather) {
            WeatherCompact(weather, onTap = onWeatherTap)
        }
    }
}

@Composable
private fun WeatherCompact(state: WeatherState, onTap: (() -> Unit)?) {
    Column(
        horizontalAlignment = Alignment.End,
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures { onTap?.invoke() }
        }
    ) {
        when (state) {
            is WeatherState.Loading -> {
                Text("—", color = AppTheme.colors.textSecondary, fontSize = 22.sp)
            }
            is WeatherState.Error -> {
                Text("—", color = AppTheme.colors.textSecondary, fontSize = 22.sp)
            }
            is WeatherState.Data -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(state.icon, fontSize = 18.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${state.tempC}°${state.unit}",
                        color = AppTheme.colors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Light
                    )
                }
                Text(
                    state.description,
                    color = AppTheme.colors.textSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

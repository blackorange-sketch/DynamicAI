package com.dynamicai.launcher.ui

import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
    onSettingsTap: (() -> Unit)? = null,
    weather: WeatherState = WeatherState.Loading,
    showWeather: Boolean = true,
    onWeatherTap: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000)
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
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xF01F1F1F),
                        Color(0xE00D0D0D)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.35f),
                        Color.White.copy(alpha = 0.08f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Годинник — тап відкриває будильник
            Text(
                now.format(timeFmt),
                color = Color.White,
                fontSize = 56.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures {
                            try {
                                ctx.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            } catch (_: Exception) {
                                try {
                                    ctx.startActivity(
                                        ctx.packageManager.getLaunchIntentForPackage("com.android.deskclock")
                                            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                    )
                                } catch (_: Exception) {}
                            }
                        }
                    }
            )
            Spacer(Modifier.height(2.dp))
            // Дата — тап відкриває календар
            Text(
                dateText,
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures {
                            try {
                                val startMillis = System.currentTimeMillis()
                                val builder = CalendarContract.CONTENT_URI.buildUpon()
                                    .appendPath("time")
                                    .appendPath(startMillis.toString())
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, builder.build()).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            } catch (_: Exception) {}
                        }
                    }
            )

            if (showWeather) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures {
                            onWeatherTap?.invoke()
                        }
                    }
                ) {
                    WeatherRow(weather)
                }
            }
        }

        // Кнопка налаштувань — у правому верхньому кутку віджета
        if (onSettingsTap != null) {
            IconButton(onClick = onSettingsTap) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Налаштування",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun WeatherRow(state: WeatherState) {
    when (state) {
        is WeatherState.Loading -> {
            Text("Погода: завантаження…", color = Color.Gray, fontSize = 14.sp)
        }
        is WeatherState.Error -> {
            Text("Погода недоступна", color = Color.Gray, fontSize = 14.sp)
        }
        is WeatherState.Data -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(state.icon, fontSize = 28.sp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "${state.tempC}°${state.unit} · ${state.description}",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Text(
                        "мін ${state.tempMin}° · макс ${state.tempMax}°",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

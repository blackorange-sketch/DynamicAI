package com.dynamicai.launcher.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamicai.launcher.data.WeatherState

@Composable
fun WeatherDetailScreen(
    state: WeatherState,
    cityName: String,
    backgroundAlphaPercent: Int,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backgroundAlphaPercent / 100f))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        // Заголовок
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, "Назад", tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Погода", color = Color.White, fontSize = 22.sp)
                if (cityName.isNotBlank()) {
                    Text(cityName, color = Color(0xFF64B5F6), fontSize = 13.sp)
                }
            }
        }

        when (state) {
            is WeatherState.Loading -> {
                Text(
                    "Завантаження…",
                    color = Color.Gray, fontSize = 15.sp,
                    modifier = Modifier.padding(vertical = 40.dp)
                )
            }
            is WeatherState.Error -> {
                GlassCard {
                    Text(
                        "Не вдалось: ${state.message}",
                        color = Color(0xFFFF6B6B), fontSize = 14.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            is WeatherState.Data -> {
                // ===== Головна картка =====
                GlassCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(state.icon, fontSize = 72.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${state.tempC}°${state.unit}",
                            color = Color.White,
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Light
                        )
                        Text(state.description, color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Відчувається як ${state.feelsLike}° · мін ${state.tempMin}° · макс ${state.tempMax}°",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                }

                // ===== Деталі =====
                Spacer(Modifier.height(10.dp))
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        DetailItem(
                            icon = { Icon(Icons.Default.Info, null, tint = Color(0xFF64B5F6)) },
                            value = "${state.humidity}%",
                            label = "Вологість"
                        )
                        DetailItem(
                            icon = { Icon(Icons.Default.Refresh, null, tint = Color(0xFF81C784)) },
                            value = "${state.windSpeed} км/г",
                            label = "Вітер"
                        )
                        DetailItem(
                            icon = { Icon(Icons.Default.Info, null, tint = Color(0xFFFF8A65)) },
                            value = "${state.pressure}",
                            label = "Тиск"
                        )
                    }
                }

                // UV + Схід/Захід
                if (state.uvIndex > 0 || state.sunrise.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    GlassCard {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            if (state.uvIndex > 0) {
                                DetailItem(
                                    icon = { Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107)) },
                                    value = "${state.uvIndex}",
                                    label = uvLabel(state.uvIndex)
                                )
                            }
                            if (state.sunrise.isNotBlank()) {
                                DetailItem(
                                    icon = { Text("🌅", fontSize = 20.sp) },
                                    value = state.sunrise,
                                    label = "Схід"
                                )
                            }
                            if (state.sunset.isNotBlank()) {
                                DetailItem(
                                    icon = { Text("🌇", fontSize = 20.sp) },
                                    value = state.sunset,
                                    label = "Захід"
                                )
                            }
                        }
                    }
                }

                // ===== Прогноз на 7 днів =====
                if (state.forecast.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    GlassSectionHeader("Прогноз на 7 днів")
                    GlassCard {
                        state.forecast.forEachIndexed { index, day ->
                            ForecastRow(day)
                            if (index < state.forecast.size - 1) {
                                HorizontalDividerCompat()
                            }
                        }
                    }
                }

                // ===== Кнопка детального прогнозу =====
                Spacer(Modifier.height(14.dp))
                Text(
                    "Відкрити детальний прогноз у браузері",
                    color = Color(0xFF64B5F6),
                    fontSize = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapGestures {
                                val q = if (cityName.isNotBlank()) cityName else "weather"
                                val url = "https://www.google.com/search?q=погода+$q"
                                try {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    })
                                } catch (_: Exception) {}
                            }
                        }
                        .padding(vertical = 12.dp)
                )

                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

@Composable
private fun DetailItem(
    icon: @Composable () -> Unit,
    value: String,
    label: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        icon()
        Spacer(Modifier.height(4.dp))
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ForecastRow(day: WeatherState.DailyForecast) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.width(90.dp)) {
            Text(day.dayName, color = Color.White, fontSize = 14.sp)
            Text(day.date, color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp)
        }
        Text(day.icon, fontSize = 22.sp)
        Spacer(Modifier.width(10.dp))
        Text(
            day.description,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        Text(
            "${day.tempMax}°",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "${day.tempMin}°",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 13.sp
        )
    }
}

@Composable
private fun HorizontalDividerCompat() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(0.5.dp)
            .background(Color.White.copy(alpha = 0.08f))
    )
}

private fun uvLabel(uv: Int): String = when {
    uv <= 2 -> "Низький"
    uv <= 5 -> "Помірний"
    uv <= 7 -> "Високий"
    uv <= 10 -> "Дуже високий"
    else -> "Екстремальний"
}

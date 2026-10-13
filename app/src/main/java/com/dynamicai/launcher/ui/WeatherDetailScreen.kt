package com.dynamicai.launcher.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, "Назад", tint = Color.White)
            }
            Text("Погода", color = Color.White, fontSize = 22.sp)
        }

        when (state) {
            is WeatherState.Loading -> {
                Text(
                    "Завантаження…",
                    color = Color.Gray, fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                )
            }
            is WeatherState.Error -> {
                Text(
                    "Не вдалось: ${state.message}",
                    color = Color(0xFFFF6B6B), fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                )
            }
            is WeatherState.Data -> {
                // Велика іконка і температура
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.icon, fontSize = 72.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "${state.tempC}°${state.unit}",
                        color = Color.White,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Light
                    )
                    Text(
                        state.description,
                        color = Color.Gray,
                        fontSize = 18.sp
                    )
                    if (cityName.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            cityName,
                            color = Color(0xFF64B5F6),
                            fontSize = 16.sp
                        )
                    }
                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        InfoCard("Мінімум", "${state.tempMin}°")
                        InfoCard("Максимум", "${state.tempMax}°")
                    }
                }

                // Кнопка «Детальніше» → Google Weather / Windy
                Text(
                    "Відкрити детальний прогноз у браузері",
                    color = Color(0xFF64B5F6),
                    fontSize = 15.sp,
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
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                )
            }
        }

        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun InfoCard(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 28.sp)
        Text(label, color = Color.Gray, fontSize = 13.sp)
    }
}

package com.dynamicai.launcher.data

import android.content.Context
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class WeatherRepository(private val context: Context) {

    suspend fun fetch(): WeatherState = withContext(Dispatchers.IO) {
        try {
            val (lat, lon) = getCoordinates() ?: return@withContext WeatherState.Error("немає локації")
            val url = "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code" +
                    "&daily=temperature_2m_max,temperature_2m_min" +
                    "&timezone=auto"

            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(body)
            val current = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")

            val temp = current.getDouble("temperature_2m").toInt()
            val code = current.getInt("weather_code")
            val tMax = daily.getJSONArray("temperature_2m_max").getDouble(0).toInt()
            val tMin = daily.getJSONArray("temperature_2m_min").getDouble(0).toInt()

            WeatherState.Data(
                tempC = temp,
                tempMin = tMin,
                tempMax = tMax,
                description = weatherDescription(code),
                icon = weatherIcon(code)
            )
        } catch (t: Throwable) {
            WeatherState.Error(t.message ?: "помилка")
        }
    }

    private fun getCoordinates(): Pair<Double, Double>? {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            for (p in providers) {
                if (lm.isProviderEnabled(p)) {
                    @Suppress("MissingPermission")
                    val loc = lm.getLastKnownLocation(p)
                    if (loc != null) return loc.latitude to loc.longitude
                }
            }
            // Fallback — Київ
            50.4501 to 30.5234
        } catch (_: Throwable) {
            50.4501 to 30.5234
        }
    }

    private fun weatherDescription(code: Int): String = when (code) {
        0 -> "Ясно"
        1, 2 -> "Мінлива хмарність"
        3 -> "Хмарно"
        45, 48 -> "Туман"
        51, 53, 55 -> "Мряка"
        56, 57 -> "Крижана мряка"
        61, 63, 65 -> "Дощ"
        66, 67 -> "Крижаний дощ"
        71, 73, 75 -> "Сніг"
        77 -> "Снігова крупа"
        80, 81, 82 -> "Злива"
        85, 86 -> "Снігопад"
        95 -> "Гроза"
        96, 99 -> "Гроза з градом"
        else -> "?"
    }

    private fun weatherIcon(code: Int): String = when (code) {
        0 -> "☀️"
        1, 2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 56, 57 -> "🌦️"
        61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
        71, 73, 75, 77, 85, 86 -> "❄️"
        95, 96, 99 -> "⛈️"
        else -> "🌡️"
    }
}

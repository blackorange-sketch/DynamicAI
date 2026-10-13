package com.dynamicai.launcher.data

import android.content.Context
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class WeatherRepository(private val context: Context) {

    private val settingsStorage = SettingsStorage(context)

    suspend fun fetch(): WeatherState = withContext(Dispatchers.IO) {
        try {
            val settings = settingsStorage.load()
            val (lat, lon) = when {
                settings.cityName.isNotBlank() -> geocode(settings.cityName)
                    ?: (settings.weatherLat.toDouble() to settings.weatherLon.toDouble())
                else -> getCoordinates(settings)
                    ?: (settings.weatherLat.toDouble() to settings.weatherLon.toDouble())
            }

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

            val tempC = current.getDouble("temperature_2m").toInt()
            val code = current.getInt("weather_code")
            val tMaxC = daily.getJSONArray("temperature_2m_max").getDouble(0).toInt()
            val tMinC = daily.getJSONArray("temperature_2m_min").getDouble(0).toInt()

            val toF = settings.weatherUnit == "F"
            WeatherState.Data(
                tempC = if (toF) cToF(tempC) else tempC,
                tempMin = if (toF) cToF(tMinC) else tMinC,
                tempMax = if (toF) cToF(tMaxC) else tMaxC,
                description = weatherDescription(code),
                icon = weatherIcon(code),
                unit = if (toF) "F" else "C"
            )
        } catch (t: Throwable) {
            WeatherState.Error(t.message ?: "помилка")
        }
    }

    private fun geocode(cityName: String): Pair<Double, Double>? {
        return try {
            val encoded = java.net.URLEncoder.encode(cityName, "UTF-8")
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=uk&format=json"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                requestMethod = "GET"
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return null
            if (results.length() == 0) return null
            val first = results.getJSONObject(0)
            val lat = first.getDouble("latitude")
            val lon = first.getDouble("longitude")
            lat to lon
        } catch (_: Throwable) {
            null
        }
    }

    private fun cToF(c: Int): Int = (c * 9 / 5 + 32)

    private fun getCoordinates(settings: LauncherSettings): Pair<Double, Double>? {
        if (!settings.weatherUseGps) return null
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
            null
        } catch (_: Throwable) {
            null
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

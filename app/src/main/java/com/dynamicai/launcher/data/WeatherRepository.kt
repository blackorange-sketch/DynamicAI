package com.dynamicai.launcher.data

import android.content.Context
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class WeatherRepository(private val context: Context) {

    private val settingsStorage = SettingsStorage(context)
    private val cachePrefs = context.getSharedPreferences("dynamicai_weather_cache", Context.MODE_PRIVATE)

    /** Читає останню збережену погоду (миттєво). */
    fun loadCachedWeather(): WeatherState.Data? {
        val raw = cachePrefs.getString("last", null) ?: return null
        return try {
            val o = JSONObject(raw)
            val fcArr = o.optJSONArray("forecast")
            val forecast = mutableListOf<WeatherState.DailyForecast>()
            if (fcArr != null) {
                for (i in 0 until fcArr.length()) {
                    val f = fcArr.getJSONObject(i)
                    forecast.add(
                        WeatherState.DailyForecast(
                            date = f.optString("date", ""),
                            dayName = f.optString("dayName", ""),
                            tempMin = f.optInt("tempMin", 0),
                            tempMax = f.optInt("tempMax", 0),
                            icon = f.optString("icon", ""),
                            description = f.optString("description", "")
                        )
                    )
                }
            }
            WeatherState.Data(
                tempC = o.optInt("tempC", 0),
                tempMin = o.optInt("tempMin", 0),
                tempMax = o.optInt("tempMax", 0),
                description = o.optString("description", ""),
                icon = o.optString("icon", ""),
                unit = o.optString("unit", "C"),
                feelsLike = o.optInt("feelsLike", 0),
                humidity = o.optInt("humidity", 0),
                windSpeed = o.optInt("windSpeed", 0),
                pressure = o.optInt("pressure", 0),
                uvIndex = o.optInt("uvIndex", 0),
                sunrise = o.optString("sunrise", ""),
                sunset = o.optString("sunset", ""),
                forecast = forecast
            )
        } catch (_: Throwable) { null }
    }

    /** Зберігає погоду у SharedPreferences. */
    private fun saveCache(state: WeatherState.Data) {
        try {
            val o = JSONObject()
            o.put("tempC", state.tempC)
            o.put("tempMin", state.tempMin)
            o.put("tempMax", state.tempMax)
            o.put("description", state.description)
            o.put("icon", state.icon)
            o.put("unit", state.unit)
            o.put("feelsLike", state.feelsLike)
            o.put("humidity", state.humidity)
            o.put("windSpeed", state.windSpeed)
            o.put("pressure", state.pressure)
            o.put("uvIndex", state.uvIndex)
            o.put("sunrise", state.sunrise)
            o.put("sunset", state.sunset)
            val arr = org.json.JSONArray()
            state.forecast.forEach { f ->
                arr.put(JSONObject().apply {
                    put("date", f.date)
                    put("dayName", f.dayName)
                    put("tempMin", f.tempMin)
                    put("tempMax", f.tempMax)
                    put("icon", f.icon)
                    put("description", f.description)
                })
            }
            o.put("forecast", arr)
            o.put("cachedAt", System.currentTimeMillis())
            cachePrefs.edit().putString("last", o.toString()).apply()
        } catch (_: Throwable) {}
    }

    suspend fun fetch(cityOverride: String? = null): WeatherState = withContext(Dispatchers.IO) {
        try {
            val settings = settingsStorage.load()
            val requested = cityOverride?.trim()?.takeIf { it.isNotBlank() }
            val (lat, lon) = when {
                requested != null -> geocodeWithFallbacks(requested)
                    ?: return@withContext WeatherState.Error("Не вдалось знайти місто: $requested")
                settings.cityName.isNotBlank() -> geocode(settings.cityName)
                    ?: (settings.weatherLat.toDouble() to settings.weatherLon.toDouble())
                else -> getCoordinates(settings)
                    ?: (settings.weatherLat.toDouble() to settings.weatherLon.toDouble())
            }

            val url = "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,surface_pressure" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max" +
                    "&timezone=auto" +
                    "&forecast_days=7"

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
            val feels = current.getDouble("apparent_temperature").toInt()
            val humidity = current.getInt("relative_humidity_2m")
            val wind = current.getDouble("wind_speed_10m").toInt()
            val pressure = current.getDouble("surface_pressure").toInt()
            val code = current.getInt("weather_code")

            val tMaxArr = daily.getJSONArray("temperature_2m_max")
            val tMinArr = daily.getJSONArray("temperature_2m_min")
            val codeArr = daily.getJSONArray("weather_code")
            val uvArr = daily.optJSONArray("uv_index_max")
            val sunriseArr = daily.optJSONArray("sunrise")
            val sunsetArr = daily.optJSONArray("sunset")

            val tMaxC = tMaxArr.getDouble(0).toInt()
            val tMinC = tMinArr.getDouble(0).toInt()
            val uv = uvArr?.optDouble(0, 0.0)?.toInt() ?: 0

            val sunrise = sunriseArr?.optString(0, "")?.takeLast(5) ?: ""
            val sunset = sunsetArr?.optString(0, "")?.takeLast(5) ?: ""

            val toF = settings.weatherUnit == "F"
            val convert = { c: Int -> if (toF) (c * 9 / 5 + 32) else c }

            // Прогноз на 7 днів
            val forecast = mutableListOf<WeatherState.DailyForecast>()
            val today = LocalDate.now()
            val dateFmt = DateTimeFormatter.ofPattern("d MMM", Locale("uk", "UA"))
            val dayFmt = DateTimeFormatter.ofPattern("EEEE", Locale("uk", "UA"))

            for (i in 0 until minOf(7, tMaxArr.length())) {
                val fcCode = codeArr.getInt(i)
                val date = today.plusDays(i.toLong())
                val dayName = if (i == 0) "Сьогодні"
                    else if (i == 1) "Завтра"
                    else date.format(dayFmt).replaceFirstChar { it.uppercase() }

                forecast.add(
                    WeatherState.DailyForecast(
                        date = date.format(dateFmt),
                        dayName = dayName,
                        tempMin = convert(tMinArr.getDouble(i).toInt()),
                        tempMax = convert(tMaxArr.getDouble(i).toInt()),
                        icon = weatherIcon(fcCode),
                        description = weatherDescription(fcCode)
                    )
                )
            }

            val result = WeatherState.Data(
                tempC = convert(tempC),
                tempMin = convert(tMinC),
                tempMax = convert(tMaxC),
                description = weatherDescription(code),
                icon = weatherIcon(code),
                unit = if (toF) "F" else "C",
                feelsLike = convert(feels),
                humidity = humidity,
                windSpeed = wind,
                pressure = pressure,
                uvIndex = uv,
                sunrise = sunrise,
                sunset = sunset,
                forecast = forecast
            )
            saveCache(result)
            result
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
            first.getDouble("latitude") to first.getDouble("longitude")
        } catch (_: Throwable) { null }
    }

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
        } catch (_: Throwable) { null }
    }


    private fun geocodeWithFallbacks(raw: String): Pair<Double, Double>? {
        val name = raw.trim()
        geocode(name)?.let { return it }
        val variants = LinkedHashSet<String>()
        if (name.endsWith("єві")) variants.add(name.dropLast(2) + "їв")
        if (name.endsWith("ові")) variants.add(name.dropLast(2) + "ів")
        if (name.endsWith("еві")) variants.add(name.dropLast(2) + "ів")
        if (name.endsWith("ві")) variants.add(name.dropLast(2) + "в")
        if (name.endsWith("сі")) variants.add(name.dropLast(1) + "а")
        if (name.endsWith("ці")) variants.add(name.dropLast(1) + "я")
        if (name.endsWith("зі")) variants.add(name.dropLast(1) + "а")
        if (name.endsWith("ні")) variants.add(name.dropLast(1) + "ь")
        if (name.endsWith("рі")) variants.add(name.dropLast(1) + "о")
        if (name.endsWith("і")) variants.add(name.dropLast(1) + "а")
        if (name.endsWith("і")) variants.add(name.dropLast(1) + "ь")
        if (name.endsWith("і")) variants.add(name.dropLast(1))
        if (name.endsWith("у")) variants.add(name.dropLast(1) + "а")
        if (name.endsWith("ю")) variants.add(name.dropLast(1) + "я")
        if (name.endsWith("ою")) variants.add(name.dropLast(3) + "а")
        if (name.endsWith("ею")) variants.add(name.dropLast(3) + "я")
        val first = name.substringBefore(" ").trim()
        if (first.isNotBlank() && first != name) variants.add(first)
        for (v in variants) {
            if (v.isBlank() || v == name) continue
            geocode(v)?.let { return it }
        }
        return null
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

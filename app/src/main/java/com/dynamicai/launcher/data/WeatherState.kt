package com.dynamicai.launcher.data

sealed class WeatherState {
    object Loading : WeatherState()
    data class Error(val message: String) : WeatherState()
    data class Data(
        val tempC: Int,
        val tempMin: Int,
        val tempMax: Int,
        val description: String,
        val icon: String,
        val unit: String = "C",
        // Розширені дані
        val feelsLike: Int = tempC,
        val humidity: Int = 0,
        val windSpeed: Int = 0,
        val pressure: Int = 0,
        val uvIndex: Int = 0,
        val sunrise: String = "",
        val sunset: String = "",
        val forecast: List<DailyForecast> = emptyList()
    ) : WeatherState()

    data class DailyForecast(
        val date: String,
        val dayName: String,
        val tempMin: Int,
        val tempMax: Int,
        val icon: String,
        val description: String
    )
}

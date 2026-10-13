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
        val unit: String = "C"
    ) : WeatherState()
}

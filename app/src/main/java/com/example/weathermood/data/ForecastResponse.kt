package com.example.weathermood.data

data class ForecastResponse(
    val list: List<ForecastItem>
)

data class ForecastItem(
    val dt: Long,
    val main: MainInfo,
    val weather: List<WeatherInfo>
)

data class MainInfo(
    val temp: Double
)

data class WeatherInfo(
    val main: String,
    val icon: String
)
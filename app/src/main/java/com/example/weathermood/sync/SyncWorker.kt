package com.example.weathermood.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.weathermood.data.db.WeatherMoodDatabase
import com.example.weathermood.data.db.WeatherCacheEntity
import com.example.weathermood.api.ApiClient
import com.example.weathermood.auth.AuthService
import com.example.weathermood.auth.FakeAuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val authService: AuthService = FakeAuthService()
    private val database = WeatherMoodDatabase.get(context)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val user = authService.currentUser()
            if (user == null) {
                return@withContext Result.failure()
            }

            // Синхронизируем кэш погоды
            syncWeatherCache()
            
            // Синхронизируем историю настроений
            syncMoodHistory()

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun syncWeatherCache() {
        try {
            val cachedWeather = database.weatherCacheDao().getAllCachedWeather()
            
            for (weather in cachedWeather) {
                // Обновляем данные погоды через API
                val response = ApiClient.weatherApi.getWeather(
                    city = weather.cityName,
                    apiKey = com.example.weathermood.Constants.API_KEY
                )
                
                if (response.isSuccessful) {
                    val weatherData = response.body()
                    if (weatherData != null) {
                        // Обновляем кэш
                        val updatedCache = weather.copy(
                            temperature = weatherData.main.temp,
                            weatherCondition = weatherData.weather.firstOrNull()?.main ?: "",
                            humidity = weatherData.main.humidity,
                            windSpeed = weatherData.wind.speed,
                            pressure = weatherData.main.pressure,
                            timestamp = Date().time
                        )
                        database.weatherCacheDao().updateWeatherCache(updatedCache)
                    }
                }
            }
        } catch (e: Exception) {
            // Логируем ошибку, но не прерываем синхронизацию
        }
    }

    private suspend fun syncMoodHistory() {
        try {
            // Здесь можно добавить синхронизацию с Firebase
            // Пока просто логируем
        } catch (e: Exception) {
            // Логируем ошибку
        }
    }
}

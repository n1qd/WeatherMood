package com.example.weathermood.data

import android.content.Context
import android.util.Log
import com.example.weathermood.auth.UserManager
import com.example.weathermood.data.db.FavoriteCityEntity
import com.example.weathermood.data.db.WeatherMoodDatabase
import com.example.weathermood.firebase.FirestoreService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CityManager(private val context: Context) {
    
    private val database = WeatherMoodDatabase.get(context)
    private val userManager = UserManager(context)
    private val firestore = FirestoreService()
    private val TAG = "CityManager"
    
    // Сохранить город в избранное для текущего пользователя
    suspend fun addFavoriteCity(cityName: String, latitude: Double = 0.0, longitude: Double = 0.0) {
        withContext(Dispatchers.IO) {
            val currentUser = userManager.getCurrentUser()
            val userId = currentUser?.userId ?: "anonymous"
            
            // Проверяем, нет ли уже такого города у пользователя
            val existingCities = database.favoriteCityDao().list(userId)
            val cleanCityName = cityName.split(",")[0].trim() // Убираем код страны для сравнения
            
            val cityExists = existingCities.any { 
                it.cityName.split(",")[0].trim().equals(cleanCityName, ignoreCase = true)
            }
            
            if (!cityExists) {
                val city = FavoriteCityEntity(
                    userId = userId,
                    cityId = cityName.hashCode().toString(),
                    cityName = cityName,
                    countryCode = extractCountryCode(cityName),
                    latitude = latitude,
                    longitude = longitude,
                    isDefault = false,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = 0
                )
                
                database.favoriteCityDao().upsert(city)
                
                // Синхронизируем с Firestore если пользователь не анонимный
                val isAnonymous = currentUser?.isAnonymous ?: true
                if (!isAnonymous) {
                    try {
                        firestore.saveFavoriteCity(userId, city)
                        database.favoriteCityDao().upsert(city.copy(syncStatus = 1))
                        Log.d(TAG, "Город синхронизирован с Firestore")
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка синхронизации города: ${e.message}")
                    }
                }
            }
        }
    }
    
    // Получить список избранных городов текущего пользователя
    suspend fun getFavoriteCities(): List<FavoriteCityEntity> {
        return withContext(Dispatchers.IO) {
            val currentUser = userManager.getCurrentUser()
            val userId = currentUser?.userId ?: "anonymous"
            database.favoriteCityDao().list(userId)
        }
    }
    
    // Удалить город из избранного
    suspend fun removeFavoriteCity(cityId: Int) {
        withContext(Dispatchers.IO) {
            val currentUser = userManager.getCurrentUser()
            val userId = currentUser?.userId ?: "anonymous"
            
            // Получаем информацию о городе перед удалением
            val cities = database.favoriteCityDao().list(userId)
            val city = cities.find { it.id == cityId }
            
            // Удаляем локально
            database.favoriteCityDao().delete(cityId)
            
            // Удаляем из Firestore если пользователь не анонимный
            val isAnonymous = currentUser?.isAnonymous ?: true
            if (!isAnonymous && city != null) {
                try {
                    firestore.deleteFavoriteCity(userId, city.cityId)
                    Log.d(TAG, "Город удален из Firestore")
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка удаления города из Firestore: ${e.message}")
                }
            }
        }
    }
    
    // Установить город как дефолтный
    suspend fun setDefaultCity(cityName: String) {
        withContext(Dispatchers.IO) {
            val currentUser = userManager.getCurrentUser()
            val userId = currentUser?.userId ?: "anonymous"
            
            // Сначала убираем флаг дефолта у всех городов пользователя
            val allCities = database.favoriteCityDao().list(userId)
            allCities.forEach { city ->
                if (city.isDefault) {
                    database.favoriteCityDao().upsert(city.copy(isDefault = false))
                }
            }
            
            // Ищем город и делаем его дефолтным
            val targetCity = allCities.find { it.cityName == cityName }
            if (targetCity != null) {
                database.favoriteCityDao().upsert(targetCity.copy(isDefault = true))
            } else {
                // Если города нет в избранном, добавляем его как дефолтный
                addFavoriteCity(cityName)
            }
        }
    }
    
    private fun extractCountryCode(cityName: String): String? {
        // Простое извлечение кода страны из строки типа "Москва, RU"
        val parts = cityName.split(",")
        return if (parts.size > 1) parts[1].trim() else null
    }
}


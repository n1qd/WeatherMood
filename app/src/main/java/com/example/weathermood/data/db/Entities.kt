package com.example.weathermood.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: String,
    val email: String? = null,
    val displayName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLogin: Long = System.currentTimeMillis(),
    val syncEnabled: Boolean = false,
    val isAnonymous: Boolean = true,
    val themeMode: Int = 0 // 0 = светлая, 1 = тёмная, 2 = системная
)

@Entity(tableName = "favorite_cities")
data class FavoriteCityEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String,
    val cityId: String,
    val cityName: String,
    val countryCode: String?,
    val latitude: Double,
    val longitude: Double,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: Int = 0
)

@Entity(tableName = "mood_ratings")
data class MoodRatingEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String,
    val rating: Int, // 1..5
    val weatherCondition: String?,
    val temperature: Double?,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val cityId: String?,
    val syncStatus: Int = 0
)

@Entity(tableName = "weather_cache")
data class WeatherCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val cityId: String,
    val cityName: String,
    val latitude: Double,
    val longitude: Double,
    val temperature: Double,
    val feelsLike: Double,
    val weatherCondition: String,
    val weatherDescription: String?,
    val weatherIcon: String?,
    val windSpeed: Double,
    val humidity: Int,
    val pressure: Int,
    val visibility: Int?,
    val timestamp: Long,
    val expiresAt: Long
)

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String?,
    val tableName: String,
    val recordId: Int?,
    val operation: Int, // 1 insert, 2 update, 3 delete
    val data: String?,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val status: Int = 0
)





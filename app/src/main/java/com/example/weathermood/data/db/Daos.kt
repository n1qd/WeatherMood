package com.example.weathermood.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: UserEntity)

    @Query("SELECT * FROM users WHERE userId = :id LIMIT 1")
    suspend fun getById(id: String): UserEntity?
    
    @Query("UPDATE users SET themeMode = :themeMode WHERE userId = :userId")
    suspend fun updateThemeMode(userId: String, themeMode: Int)
}

@Dao
interface FavoriteCityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(city: FavoriteCityEntity)

    @Query("SELECT * FROM favorite_cities WHERE userId = :userId ORDER BY isDefault DESC, updatedAt DESC")
    suspend fun list(userId: String): List<FavoriteCityEntity>

    @Query("DELETE FROM favorite_cities WHERE id = :id")
    suspend fun delete(id: Int)
}

@Dao
interface MoodRatingDao {
    @Insert
    suspend fun insert(item: MoodRatingEntity)

    @Query("SELECT * FROM mood_ratings WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun list(userId: String): List<MoodRatingEntity>
    
    @Query("DELETE FROM mood_ratings WHERE id = :id")
    suspend fun delete(id: Int)
    
    // Анализ настроения по погодным условиям
    @Query("SELECT AVG(CAST(rating AS REAL)) as avgRating, weatherCondition, COUNT(*) as count FROM mood_ratings WHERE userId = :userId AND weatherCondition IS NOT NULL GROUP BY weatherCondition")
    suspend fun getMoodByWeatherCondition(userId: String): List<MoodByWeather>
    
    // Получить все записи для анализа по температуре (обработка в коде)
    @Query("SELECT * FROM mood_ratings WHERE userId = :userId AND temperature IS NOT NULL")
    suspend fun getMoodWithTemperature(userId: String): List<MoodRatingEntity>
    
    // Среднее настроение по дням недели
    @Query("SELECT AVG(CAST(rating AS REAL)) as avgRating, CAST(strftime('%w', datetime(createdAt/1000, 'unixepoch')) AS INTEGER) as dayOfWeek, COUNT(*) as count FROM mood_ratings WHERE userId = :userId GROUP BY dayOfWeek")
    suspend fun getMoodByDayOfWeek(userId: String): List<MoodByDay>
    
    // Получить все записи для графика
    @Query("SELECT * FROM mood_ratings WHERE userId = :userId ORDER BY createdAt ASC")
    suspend fun getAllForChart(userId: String): List<MoodRatingEntity>
}

// Классы для результатов анализа
data class MoodByWeather(
    val avgRating: Double,
    val weatherCondition: String,
    val count: Int
)

data class MoodByDay(
    val avgRating: Double,
    val dayOfWeek: Int,
    val count: Int
)

@Dao
interface WeatherCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WeatherCacheEntity)

    @Query("SELECT * FROM weather_cache WHERE cityId = :cityId LIMIT 1")
    suspend fun get(cityId: String): WeatherCacheEntity?

    @Query("SELECT * FROM weather_cache")
    suspend fun getAllCachedWeather(): List<WeatherCacheEntity>

    @Update
    suspend fun updateWeatherCache(item: WeatherCacheEntity)
}

@Dao
interface SyncQueueDao {
    @Insert
    suspend fun insert(item: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue WHERE status = 0 ORDER BY createdAt ASC")
    suspend fun pending(): List<SyncQueueEntity>
}





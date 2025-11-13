package com.example.weathermood.firebase

import android.util.Log
import com.example.weathermood.data.db.FavoriteCityEntity
import com.example.weathermood.data.db.MoodRatingEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FirestoreService {
    
    private val db = FirebaseFirestore.getInstance()
    private val TAG = "FirestoreService"
    
    // ========== Избранные города ==========
    
    suspend fun saveFavoriteCity(userId: String, city: FavoriteCityEntity): Result<Unit> {
        return try {
            val cityData = hashMapOf(
                "cityId" to city.cityId,
                "cityName" to city.cityName,
                "countryCode" to city.countryCode,
                "latitude" to city.latitude,
                "longitude" to city.longitude,
                "isDefault" to city.isDefault,
                "createdAt" to city.createdAt,
                "updatedAt" to System.currentTimeMillis()
            )
            
            db.collection("users")
                .document(userId)
                .collection("favoriteCities")
                .document(city.cityId)
                .set(cityData, SetOptions.merge())
                .await()
            
            Log.d(TAG, "Город ${city.cityName} сохранен в Firestore")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка сохранения города: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    suspend fun getFavoriteCities(userId: String): Result<List<FavoriteCityEntity>> {
        return try {
            val snapshot = db.collection("users")
                .document(userId)
                .collection("favoriteCities")
                .get()
                .await()
            
            val cities = snapshot.documents.mapNotNull { doc ->
                try {
                    FavoriteCityEntity(
                        id = 0, // Room автоматически назначит ID
                        userId = userId,
                        cityId = doc.getString("cityId") ?: return@mapNotNull null,
                        cityName = doc.getString("cityName") ?: return@mapNotNull null,
                        countryCode = doc.getString("countryCode"),
                        latitude = doc.getDouble("latitude") ?: 0.0,
                        longitude = doc.getDouble("longitude") ?: 0.0,
                        isDefault = doc.getBoolean("isDefault") ?: false,
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                        updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                        syncStatus = 1 // Синхронизировано
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка парсинга города: ${e.message}")
                    null
                }
            }
            
            Log.d(TAG, "Загружено ${cities.size} городов из Firestore")
            Result.success(cities)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки городов: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    suspend fun deleteFavoriteCity(userId: String, cityId: String): Result<Unit> {
        return try {
            db.collection("users")
                .document(userId)
                .collection("favoriteCities")
                .document(cityId)
                .delete()
                .await()
            
            Log.d(TAG, "Город удален из Firestore")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка удаления города: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    // ========== История настроений ==========
    
    suspend fun saveMoodRating(userId: String, mood: MoodRatingEntity): Result<Unit> {
        return try {
            val moodData = hashMapOf(
                "rating" to mood.rating,
                "weatherCondition" to mood.weatherCondition,
                "weatherDescription" to mood.weatherDescription,
                "temperature" to mood.temperature,
                "feelsLike" to mood.feelsLike,
                "humidity" to mood.humidity,
                "pressure" to mood.pressure,
                "windSpeed" to mood.windSpeed,
                "note" to mood.note,
                "cityId" to mood.cityId,
                "cityName" to mood.cityName,
                "createdAt" to mood.createdAt,
                "updatedAt" to System.currentTimeMillis()
            )
            
            db.collection("users")
                .document(userId)
                .collection("moodHistory")
                .add(moodData)
                .await()
            
            Log.d(TAG, "Настроение сохранено в Firestore")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка сохранения настроения: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    suspend fun getMoodHistory(userId: String): Result<List<MoodRatingEntity>> {
        return try {
            val snapshot = db.collection("users")
                .document(userId)
                .collection("moodHistory")
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .await()
            
            val moods = snapshot.documents.mapNotNull { doc ->
                try {
                    MoodRatingEntity(
                        id = 0, // Room автоматически назначит ID
                        userId = userId,
                        rating = doc.getLong("rating")?.toInt() ?: return@mapNotNull null,
                        weatherCondition = doc.getString("weatherCondition"),
                        weatherDescription = doc.getString("weatherDescription"),
                        temperature = doc.getDouble("temperature"),
                        feelsLike = doc.getDouble("feelsLike"),
                        humidity = doc.getLong("humidity")?.toInt(),
                        pressure = doc.getLong("pressure")?.toInt(),
                        windSpeed = doc.getDouble("windSpeed"),
                        note = doc.getString("note"),
                        cityId = doc.getString("cityId"),
                        cityName = doc.getString("cityName"),
                        createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                        updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                        syncStatus = 1 // Синхронизировано
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка парсинга настроения: ${e.message}")
                    null
                }
            }
            
            Log.d(TAG, "Загружено ${moods.size} настроений из Firestore")
            Result.success(moods)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки настроений: ${e.message}", e)
            Result.failure(e)
        }
    }
}


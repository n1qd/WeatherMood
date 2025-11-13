package com.example.weathermood.auth

import android.content.Context
import android.util.Log
import com.example.weathermood.data.db.FavoriteCityEntity
import com.example.weathermood.data.db.UserEntity
import com.example.weathermood.data.db.WeatherMoodDatabase
import com.example.weathermood.firebase.FirestoreService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserManager(private val context: Context) {
    
    private val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    private val database = WeatherMoodDatabase.get(context)
    private val firestore = FirestoreService()
    private val TAG = "UserManager"
    
    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_IS_ANONYMOUS = "is_anonymous"
    }
    
    // Сохранение пользователя после авторизации
    suspend fun saveUser(user: AuthUser.LoggedIn) {
        withContext(Dispatchers.IO) {
            // Сохраняем в SharedPreferences
            prefs.edit().apply {
                putString(KEY_USER_ID, user.uid)
                putString(KEY_USER_EMAIL, user.email ?: "")
                putString(KEY_USER_NAME, getUserNameFromEmail(user.email))
                putBoolean(KEY_IS_LOGGED_IN, true)
                putBoolean(KEY_IS_ANONYMOUS, user.isAnonymous)
                apply()
            }
            
            // Проверяем, существует ли пользователь в базе
            val existingUser = database.userDao().getById(user.uid)
            
            // Получаем тему из SharedPreferences (если была установлена для анонимного пользователя)
            val prefs = context.getSharedPreferences("weathermood_prefs", Context.MODE_PRIVATE)
            val savedTheme = prefs.getInt("theme_mode", 0)
            
            if (existingUser == null) {
                // Новый пользователь - создаем запись и добавляем Москву по умолчанию
                val userEntity = UserEntity(
                    userId = user.uid,
                    email = user.email,
                    displayName = getUserNameFromEmail(user.email),
                    createdAt = System.currentTimeMillis(),
                    lastLogin = System.currentTimeMillis(),
                    syncEnabled = !user.isAnonymous,
                    isAnonymous = user.isAnonymous,
                    themeMode = savedTheme // Сохраняем тему из SharedPreferences
                )
                database.userDao().upsert(userEntity)
                
                // Добавляем Москву как дефолтный город для нового пользователя
                val moscowCity = FavoriteCityEntity(
                    userId = user.uid,
                    cityId = "524901", // OpenWeatherMap ID для Москвы
                    cityName = "Москва",
                    countryCode = "RU",
                    latitude = 55.7558,
                    longitude = 37.6173,
                    isDefault = true,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = 0
                )
                database.favoriteCityDao().upsert(moscowCity)
            } else {
                // Существующий пользователь - обновляем время последнего входа
                // Если тема не была установлена в БД, используем из SharedPreferences
                val themeMode = if (existingUser.themeMode == 0 && savedTheme != 0) savedTheme else existingUser.themeMode
                val updatedUser = existingUser.copy(
                    lastLogin = System.currentTimeMillis(),
                    themeMode = themeMode
                )
                database.userDao().upsert(updatedUser)
            }
        }
    }
    
    // Получение текущего пользователя
    fun getCurrentUser(): UserInfo? {
        val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        if (!isLoggedIn) return null
        
        return UserInfo(
            userId = prefs.getString(KEY_USER_ID, "") ?: "",
            email = prefs.getString(KEY_USER_EMAIL, null),
            displayName = prefs.getString(KEY_USER_NAME, "Гость") ?: "Гость",
            isAnonymous = prefs.getBoolean(KEY_IS_ANONYMOUS, true)
        )
    }
    
    // Выход из аккаунта
    suspend fun logout() {
        withContext(Dispatchers.IO) {
            prefs.edit().clear().apply()
        }
    }
    
    // Проверка авторизации
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }
    
    // Синхронизация данных с Firestore
    suspend fun syncWithFirestore() {
        withContext(Dispatchers.IO) {
            try {
                val currentUser = getCurrentUser()
                if (currentUser == null || currentUser.isAnonymous) {
                    Log.d(TAG, "Пропуск синхронизации для анонимного пользователя")
                    return@withContext
                }
                
                val userId = currentUser.userId
                
                // Синхронизация городов: локальные -> Firestore
                val localCities = database.favoriteCityDao().list(userId)
                localCities.forEach { city ->
                    if (city.syncStatus == 0) { // Не синхронизировано
                        firestore.saveFavoriteCity(userId, city)
                        // Обновляем статус синхронизации
                        database.favoriteCityDao().upsert(city.copy(syncStatus = 1))
                    }
                }
                
                // Синхронизация настроений: локальные -> Firestore
                val localMoods = database.moodRatingDao().list(userId)
                localMoods.forEach { mood ->
                    if (mood.syncStatus == 0) { // Не синхронизировано
                        firestore.saveMoodRating(userId, mood)
                    }
                }
                
                Log.d(TAG, "Синхронизация с Firestore завершена")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка синхронизации: ${e.message}", e)
            }
        }
    }
    
    // Загрузка данных из Firestore в локальную БД
    suspend fun loadFromFirestore() {
        withContext(Dispatchers.IO) {
            try {
                val currentUser = getCurrentUser()
                if (currentUser == null || currentUser.isAnonymous) {
                    Log.d(TAG, "Пропуск загрузки для анонимного пользователя")
                    return@withContext
                }
                
                val userId = currentUser.userId
                
                // Загрузка городов из Firestore
                val cloudCities = firestore.getFavoriteCities(userId)
                cloudCities.getOrNull()?.forEach { city ->
                    database.favoriteCityDao().upsert(city)
                }
                
                // Загрузка настроений из Firestore
                val cloudMoods = firestore.getMoodHistory(userId)
                cloudMoods.getOrNull()?.forEach { mood ->
                    database.moodRatingDao().insert(mood)
                }
                
                Log.d(TAG, "Загрузка из Firestore завершена")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки: ${e.message}", e)
            }
        }
    }
    
    // Вспомогательная функция для получения имени из email
    private fun getUserNameFromEmail(email: String?): String {
        if (email.isNullOrEmpty()) return "Гость"
        return email.substringBefore("@").replaceFirstChar { it.uppercase() }
    }
    
    data class UserInfo(
        val userId: String,
        val email: String?,
        val displayName: String,
        val isAnonymous: Boolean
    )
}


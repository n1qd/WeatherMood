package com.example.weathermood.data

import android.content.Context
import com.example.weathermood.auth.UserManager

object Prefs {
    private const val PREFS_NAME = "weathermood_prefs"
    private const val KEY_CITY = "selected_city"
    private const val KEY_USE_FAHRENHEIT = "use_fahrenheit"
    private const val KEY_USE_MPH = "use_mph"
    private const val KEY_USE_CURRENT_LOCATION = "use_current_location"

    fun getSelectedCity(context: Context, defaultCity: String = "Москва"): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Получаем userId для создания уникального ключа для каждого пользователя
        val userManager = UserManager(context)
        val userId = userManager.getCurrentUser()?.userId ?: "anonymous"
        val userCityKey = "${KEY_CITY}_$userId"
        
        return prefs.getString(userCityKey, defaultCity) ?: defaultCity
    }

    fun setSelectedCity(context: Context, city: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Получаем userId для создания уникального ключа для каждого пользователя
        val userManager = UserManager(context)
        val userId = userManager.getCurrentUser()?.userId ?: "anonymous"
        val userCityKey = "${KEY_CITY}_$userId"
        
        prefs.edit().putString(userCityKey, city).apply()
    }
    
    fun getUseFahrenheit(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_USE_FAHRENHEIT, false)
    }
    
    fun setUseFahrenheit(context: Context, useFahrenheit: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_USE_FAHRENHEIT, useFahrenheit).apply()
    }
    
    fun getUseMph(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_USE_MPH, false)
    }
    
    fun setUseMph(context: Context, useMph: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_USE_MPH, useMph).apply()
    }
    
    fun getUseCurrentLocation(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val userManager = UserManager(context)
        val userId = userManager.getCurrentUser()?.userId ?: "anonymous"
        val userLocationKey = "${KEY_USE_CURRENT_LOCATION}_$userId"
        return prefs.getBoolean(userLocationKey, true) // По умолчанию true
    }
    
    fun setUseCurrentLocation(context: Context, useCurrentLocation: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val userManager = UserManager(context)
        val userId = userManager.getCurrentUser()?.userId ?: "anonymous"
        val userLocationKey = "${KEY_USE_CURRENT_LOCATION}_$userId"
        prefs.edit().putBoolean(userLocationKey, useCurrentLocation).apply()
    }
}



package com.example.weathermood

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.example.weathermood.data.Prefs

class WeatherMoodApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Применяем сохранённую тему при запуске приложения
        val themeMode = Prefs.getThemeMode(this)
        when (themeMode) {
            0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO) // Светлая
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) // Тёмная
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) // Системная
        }
    }
}


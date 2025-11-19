package com.example.weathermood.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import com.weatherapp.R
import com.example.weathermood.data.Prefs
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProfileActivity : AppCompatActivity() {
    
    private var useFahrenheit = false
    private var useMph = false
    private var themeMode = 0 // 0 = светлая, 1 = тёмная
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Применяем тему перед созданием активности
        val savedThemeMode = Prefs.getThemeMode(this)
        when (savedThemeMode) {
            0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        
        // Загружаем текущие настройки
        useFahrenheit = Prefs.getUseFahrenheit(this)
        useMph = Prefs.getUseMph(this)
        themeMode = Prefs.getThemeMode(this)
        
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
        
        setupTemperatureToggle()
        setupWindToggle()
        setupThemeToggle()
        setupMenuItems()
    }
    
    private fun setupTemperatureToggle() {
        val btnCelsius = findViewById<TextView>(R.id.btnCelsius)
        val btnFahrenheit = findViewById<TextView>(R.id.btnFahrenheit)
        
        // Устанавливаем начальное состояние
        updateTemperatureUI(btnCelsius, btnFahrenheit)
        
        btnCelsius.setOnClickListener {
            if (useFahrenheit) {
                useFahrenheit = false
                Prefs.setUseFahrenheit(this, false)
                updateTemperatureUI(btnCelsius, btnFahrenheit)
                Toast.makeText(this, "Температура: °C", Toast.LENGTH_SHORT).show()
            }
        }
        
        btnFahrenheit.setOnClickListener {
            if (!useFahrenheit) {
                useFahrenheit = true
                Prefs.setUseFahrenheit(this, true)
                updateTemperatureUI(btnCelsius, btnFahrenheit)
                Toast.makeText(this, "Температура: °F", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun updateTemperatureUI(btnCelsius: TextView, btnFahrenheit: TextView) {
        val blueColor = getColor(R.color.blue)
        val activeTextColor = getColor(R.color.button_text_active)
        val inactiveTextColor = getColor(R.color.button_text_inactive)
        
        if (useFahrenheit) {
            btnFahrenheit.setBackgroundColor(blueColor)
            btnFahrenheit.setTextColor(activeTextColor)
            btnCelsius.setBackgroundColor(getColor(android.R.color.transparent))
            btnCelsius.setTextColor(inactiveTextColor)
        } else {
            btnCelsius.setBackgroundColor(blueColor)
            btnCelsius.setTextColor(activeTextColor)
            btnFahrenheit.setBackgroundColor(getColor(android.R.color.transparent))
            btnFahrenheit.setTextColor(inactiveTextColor)
        }
    }
    
    private fun setupWindToggle() {
        val btnKm = findViewById<TextView>(R.id.btnKm)
        val btnMiles = findViewById<TextView>(R.id.btnMiles)
        
        // Устанавливаем начальное состояние
        updateWindUI(btnKm, btnMiles)
        
        btnKm.setOnClickListener {
            if (useMph) {
                useMph = false
                Prefs.setUseMph(this, false)
                updateWindUI(btnKm, btnMiles)
                Toast.makeText(this, "Единицы скорости ветра: м/с", Toast.LENGTH_SHORT).show()
            }
        }
        
        btnMiles.setOnClickListener {
            if (!useMph) {
                useMph = true
                Prefs.setUseMph(this, true)
                updateWindUI(btnKm, btnMiles)
                Toast.makeText(this, "Единицы скорости ветра: миль/ч", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun updateWindUI(btnKm: TextView, btnMiles: TextView) {
        val blueColor = getColor(R.color.blue)
        val activeTextColor = getColor(R.color.button_text_active)
        val inactiveTextColor = getColor(R.color.button_text_inactive)
        
        if (useMph) {
            btnMiles.setBackgroundColor(blueColor)
            btnMiles.setTextColor(activeTextColor)
            btnKm.setBackgroundColor(getColor(android.R.color.transparent))
            btnKm.setTextColor(inactiveTextColor)
        } else {
            btnKm.setBackgroundColor(blueColor)
            btnKm.setTextColor(activeTextColor)
            btnMiles.setBackgroundColor(getColor(android.R.color.transparent))
            btnMiles.setTextColor(inactiveTextColor)
        }
    }
    
    private fun setupThemeToggle() {
        val btnLightTheme = findViewById<TextView>(R.id.btnLightTheme)
        val btnDarkTheme = findViewById<TextView>(R.id.btnDarkTheme)
        
        // Устанавливаем начальное состояние
        updateThemeUI(btnLightTheme, btnDarkTheme)
        
        btnLightTheme.setOnClickListener {
            if (themeMode != 0) {
                // Сохраняем и применяем тему асинхронно
                lifecycleScope.launch(Dispatchers.Main) {
                    themeMode = 0
                    Prefs.setThemeModeAsync(this@ProfileActivity, 0)
                    // Применяем тему глобально
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    // Перезапускаем активность для применения темы
                    finish()
                    startActivity(intent)
                }
            }
        }
        
        btnDarkTheme.setOnClickListener {
            if (themeMode != 1) {
                // Сохраняем и применяем тему асинхронно
                lifecycleScope.launch(Dispatchers.Main) {
                    themeMode = 1
                    Prefs.setThemeModeAsync(this@ProfileActivity, 1)
                    // Применяем тему глобально
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    // Перезапускаем активность для применения темы
                    finish()
                    startActivity(intent)
                }
            }
        }
    }
    
    private fun updateThemeUI(btnLightTheme: TextView, btnDarkTheme: TextView) {
        val blueColor = getColor(R.color.blue)
        val activeTextColor = getColor(R.color.button_text_active)
        val inactiveTextColor = getColor(R.color.button_text_inactive)
        
        if (themeMode == 1) {
            // Тёмная тема активна
            btnDarkTheme.setBackgroundColor(blueColor)
            btnDarkTheme.setTextColor(activeTextColor)
            btnLightTheme.setBackgroundColor(getColor(android.R.color.transparent))
            btnLightTheme.setTextColor(inactiveTextColor)
        } else {
            // Светлая тема активна
            btnLightTheme.setBackgroundColor(blueColor)
            btnLightTheme.setTextColor(activeTextColor)
            btnDarkTheme.setBackgroundColor(getColor(android.R.color.transparent))
            btnDarkTheme.setTextColor(inactiveTextColor)
        }
    }
    
    
    private fun setupMenuItems() {
        // О приложении
        findViewById<LinearLayout>(R.id.btnAbout).setOnClickListener {
            Toast.makeText(this, "WeatherMood v1.0\nПриложение для погоды и настроения", Toast.LENGTH_LONG).show()
        }
        
        // Поделиться
        findViewById<LinearLayout>(R.id.btnShare).setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "WeatherMood")
                putExtra(Intent.EXTRA_TEXT, "Попробуй приложение WeatherMood для прогноза погоды!")
            }
            startActivity(Intent.createChooser(shareIntent, "Поделиться через"))
        }
        
        // Присоединиться к нам
        findViewById<LinearLayout>(R.id.btnJoinUs).setOnClickListener {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/n1qd/WeatherMood"))
            startActivity(browserIntent)
        }
        
        // Оставить отзыв
        findViewById<LinearLayout>(R.id.btnFeedback).setOnClickListener {
            Toast.makeText(this, "Спасибо за отзыв! 😊", Toast.LENGTH_SHORT).show()
        }
        
        // Обратная связь
        findViewById<LinearLayout>(R.id.btnContact).setOnClickListener {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:n1qd1337@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "Обратная связь WeatherMood")
            }
            try {
                startActivity(Intent.createChooser(emailIntent, "Отправить email"))
            } catch (e: Exception) {
                Toast.makeText(this, "Нет email клиента", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

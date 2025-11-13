package com.example.weathermood.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.weatherapp.R
import com.example.weathermood.data.Prefs

class ProfileActivity : AppCompatActivity() {
    
    private var useFahrenheit = false
    private var useMph = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        
        // Загружаем текущие настройки
        useFahrenheit = Prefs.getUseFahrenheit(this)
        useMph = Prefs.getUseMph(this)
        
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
        
        setupTemperatureToggle()
        setupWindToggle()
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
        if (useFahrenheit) {
            btnFahrenheit.setBackgroundColor(0xFF5B9FED.toInt())
            btnFahrenheit.setTextColor(getColor(android.R.color.white))
            btnCelsius.setBackgroundColor(getColor(android.R.color.transparent))
            btnCelsius.setTextColor(0xFF1A1A2E.toInt())
        } else {
            btnCelsius.setBackgroundColor(0xFF5B9FED.toInt())
            btnCelsius.setTextColor(getColor(android.R.color.white))
            btnFahrenheit.setBackgroundColor(getColor(android.R.color.transparent))
            btnFahrenheit.setTextColor(0xFF1A1A2E.toInt())
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
                Toast.makeText(this, "Скорость ветра: м/с", Toast.LENGTH_SHORT).show()
            }
        }
        
        btnMiles.setOnClickListener {
            if (!useMph) {
                useMph = true
                Prefs.setUseMph(this, true)
                updateWindUI(btnKm, btnMiles)
                Toast.makeText(this, "Скорость ветра: миль/ч", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun updateWindUI(btnKm: TextView, btnMiles: TextView) {
        if (useMph) {
            btnMiles.setBackgroundColor(0xFF5B9FED.toInt())
            btnMiles.setTextColor(getColor(android.R.color.white))
            btnKm.setBackgroundColor(getColor(android.R.color.transparent))
            btnKm.setTextColor(0xFF1A1A2E.toInt())
        } else {
            btnKm.setBackgroundColor(0xFF5B9FED.toInt())
            btnKm.setTextColor(getColor(android.R.color.white))
            btnMiles.setBackgroundColor(getColor(android.R.color.transparent))
            btnMiles.setTextColor(0xFF1A1A2E.toInt())
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

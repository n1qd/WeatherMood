package com.example.weathermood.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.weathermood.api.CitySearchClient
import com.example.weathermood.data.CityManager
import com.example.weathermood.data.Prefs
import com.weatherapp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CityPickerActivity : AppCompatActivity() {
    private var searchJob: Job? = null
    private lateinit var cities: MutableList<String>
    private lateinit var adapter: CityAdapter
    private lateinit var cityManager: CityManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_city_picker)

        cityManager = CityManager(this)

        // Кнопка назад
        findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener {
            finish()
        }

        val editText = findViewById<EditText>(R.id.etQuery)
        val listView = findViewById<ListView>(R.id.lvCities)

        // Стартовый список популярных городов
        val popularCities = listOf(
            "Москва", "Санкт-Петербург", "Казань", "Екатеринбург", "Новосибирск",
            "Нижний Новгород", "Самара", "Ростов-на-Дону", "Уфа", "Красноярск",
            "Сочи", "Минск", "Алматы", "Тбилиси", "Баку"
        )
        
        cities = popularCities.toMutableList()
        adapter = CityAdapter(this, cities)
        listView.adapter = adapter

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchJob?.cancel()
                val query = s?.toString()?.trim()
                
                if (query.isNullOrEmpty()) {
                    // Показываем популярные города
                    cities.clear()
                    cities.addAll(popularCities)
                    adapter.notifyDataSetChanged()
                } else if (query.length >= 2) {
                    // Ищем через API
                    searchJob = lifecycleScope.launch {
                        delay(300) // Задержка для избежания частых запросов
                        searchCities(query)
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        listView.setOnItemClickListener { _, _, position, _ ->
            val city = cities[position]
            
            // Сохраняем город в избранное текущего пользователя
            lifecycleScope.launch {
                try {
                    cityManager.addFavoriteCity(city)
                    Prefs.setUseCurrentLocation(this@CityPickerActivity, false)
                    Prefs.setSelectedCity(this@CityPickerActivity, city)
                    Toast.makeText(this@CityPickerActivity, "Город добавлен: $city", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                } catch (e: Exception) {
                    Toast.makeText(this@CityPickerActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private suspend fun searchCities(query: String) {
        try {
            withContext(Dispatchers.IO) {
                val response = CitySearchClient.citySearchApi.searchCities(
                    query = query,
                    limit = 10,
                    apiKey = com.example.weathermood.Constants.API_KEY
                )
                
                        if (response.isSuccessful) {
                            val foundCities = response.body()?.map { "${it.name}, ${it.country}" } ?: emptyList()

                            withContext(Dispatchers.Main) {
                                cities.clear()
                                cities.addAll(foundCities)
                                adapter.notifyDataSetChanged()
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@CityPickerActivity, "Ошибка поиска городов", Toast.LENGTH_SHORT).show()
                            }
                        }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@CityPickerActivity, "Ошибка сети: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}





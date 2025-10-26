package com.example.weathermood.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.weathermood.Constants
import com.example.weathermood.api.ApiClient
import com.weatherapp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CityAdapter(
    context: Context,
    private val cities: List<String>
) : ArrayAdapter<String>(context, R.layout.item_city, cities) {

    private val weatherIcons = mutableMapOf<String, String>()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(
            R.layout.item_city, parent, false
        )

        val city = cities[position]
        val cityName = city.split(",")[0].trim()
        
        view.findViewById<TextView>(R.id.tvCityName).text = city
        
        val weatherIconView = view.findViewById<TextView>(R.id.tvWeatherIcon)
        
        // Если уже загружена иконка - показываем
        if (weatherIcons.containsKey(cityName)) {
            weatherIconView.text = weatherIcons[cityName]
        } else {
            // Загружаем погоду для города
            weatherIconView.text = "⏳"
            loadWeatherIcon(cityName, weatherIconView)
        }

        return view
    }

    private fun loadWeatherIcon(cityName: String, iconView: TextView) {
        if (context is LifecycleOwner) {
            (context as LifecycleOwner).lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val response = ApiClient.weatherApi.getWeather(
                        city = cityName,
                        apiKey = Constants.API_KEY
                    )
                    
                    if (response.isSuccessful) {
                        val weatherData = response.body()
                        val iconCode = weatherData?.weather?.firstOrNull()?.icon ?: "01d"
                        val emoji = getWeatherEmoji(iconCode)
                        
                        weatherIcons[cityName] = emoji
                        
                        withContext(Dispatchers.Main) {
                            iconView.text = emoji
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        iconView.text = "🌤️"
                    }
                }
            }
        } else {
            iconView.text = "🌤️"
        }
    }

    private fun getWeatherEmoji(iconCode: String): String {
        return when (iconCode) {
            "01d" -> "☀️"
            "01n" -> "🌙"
            "02d" -> "⛅"
            "02n" -> "☁️"
            "03d", "03n" -> "☁️"
            "04d", "04n" -> "☁️"
            "09d", "09n" -> "🌦️"
            "10d" -> "🌧️"
            "10n" -> "🌧️"
            "11d", "11n" -> "⛈️"
            "13d", "13n" -> "❄️"
            "50d", "50n" -> "🌫️"
            else -> "🌤️"
        }
    }
}

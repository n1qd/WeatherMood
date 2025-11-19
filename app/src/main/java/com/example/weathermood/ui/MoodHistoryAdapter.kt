package com.example.weathermood.ui

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.example.weathermood.data.db.MoodRatingEntity
import com.weatherapp.R
import java.text.SimpleDateFormat
import java.util.*

class MoodHistoryAdapter(
    private val context: Context,
    private var moodHistory: MutableList<MoodRatingEntity>,
    private val onDeleteClick: (MoodRatingEntity) -> Unit
) : BaseAdapter() {

    override fun getCount(): Int = moodHistory.size

    override fun getItem(position: Int): MoodRatingEntity = moodHistory[position]

    override fun getItemId(position: Int): Long = moodHistory[position].id.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(
            R.layout.item_mood_history, parent, false
        )
        
        val mood = getItem(position)
        
        val dateText = view.findViewById<TextView>(R.id.tvDate)
        val weatherText = view.findViewById<TextView>(R.id.tvWeather)
        val temperatureText = view.findViewById<TextView>(R.id.tvTemperature)
        val starsContainer = view.findViewById<LinearLayout>(R.id.starsContainer)
        val btnDelete = view.findViewById<ImageButton>(R.id.btnDelete)
        
        // Очищаем контейнер звёзд
        starsContainer.removeAllViews()
        
        // Форматируем дату
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        dateText.text = dateFormat.format(Date(mood.createdAt))
        
        // Создаём звёзды графически
        val rating = mood.rating.toInt()
        val starSize = 28f // размер в sp
        val starMargin = 6 // отступ между звёздами в dp
        val starMarginPx = (starMargin * context.resources.displayMetrics.density).toInt()
        
        for (i in 1..5) {
            val starView = TextView(context)
            val isFilled = i <= rating
            starView.text = if (isFilled) "★" else "☆"
            starView.textSize = starSize
            starView.setTextColor(
                if (isFilled) {
                    Color.parseColor("#FFD700") // Золотой цвет для заполненных звёзд
                } else {
                    Color.parseColor("#CCCCCC") // Светло-серый для пустых звёзд
                }
            )
            
            // Добавляем тень для заполненных звёзд
            if (isFilled) {
                starView.setShadowLayer(4f, 2f, 2f, Color.parseColor("#40FFD700"))
            }
            
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (i < 5) {
                params.marginEnd = starMarginPx
            }
            starView.layoutParams = params
            
            starsContainer.addView(starView)
        }
        
        // Показываем информацию о погоде
        mood.weatherCondition?.let {
            weatherText.text = getWeatherConditionName(it)
            weatherText.visibility = View.VISIBLE
        } ?: run {
            weatherText.visibility = View.GONE
        }
        
        // Показываем температуру отдельно
        mood.temperature?.let {
            temperatureText.text = "${it.toInt()}°C"
            temperatureText.visibility = View.VISIBLE
        } ?: run {
            temperatureText.visibility = View.GONE
        }
        
        // Обработчик удаления
        btnDelete.setOnClickListener {
            onDeleteClick(mood)
        }
        
        return view
    }
    
    fun removeItem(mood: MoodRatingEntity) {
        moodHistory.remove(mood)
        notifyDataSetChanged()
    }
    
    fun updateList(newList: List<MoodRatingEntity>) {
        moodHistory.clear()
        moodHistory.addAll(newList)
        notifyDataSetChanged()
    }
    
    private fun getWeatherConditionName(condition: String): String {
        return when (condition.lowercase()) {
            "clear" -> "☀️ Ясно"
            "clouds" -> "⛅ Облачно"
            "rain" -> "🌧️ Дождь"
            "snow" -> "❄️ Снег"
            "thunderstorm" -> "⛈️ Гроза"
            "drizzle" -> "🌦️ Морось"
            "mist", "fog" -> "🌫️ Туман"
            else -> condition
        }
    }
}

package com.example.weathermood.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
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
        val ratingText = view.findViewById<TextView>(R.id.tvRating)
        val weatherText = view.findViewById<TextView>(R.id.tvWeather)
        val btnDelete = view.findViewById<ImageButton>(R.id.btnDelete)
        
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        dateText.text = dateFormat.format(Date(mood.createdAt))
        
        val rating = mood.rating.toInt()
        val ratingEmoji = when (rating) {
            1 -> "😞"
            2 -> "😐"
            3 -> "😊"
            4 -> "😄"
            5 -> "🤩"
            else -> "😐"
        }
        
        ratingText.text = "$ratingEmoji Оценка: $rating/5"
        
        // Показываем информацию о погоде, если есть
        val weatherInfo = buildString {
            mood.weatherCondition?.let {
                append(getWeatherConditionName(it))
            }
            mood.temperature?.let {
                append(" ${it.toInt()}°C")
            }
        }
        
        if (weatherInfo.isNotEmpty()) {
            weatherText.text = weatherInfo
            weatherText.visibility = View.VISIBLE
        } else {
            weatherText.visibility = View.GONE
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

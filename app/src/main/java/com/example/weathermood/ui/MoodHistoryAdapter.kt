package com.example.weathermood.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.RatingBar
import android.widget.TextView
import com.example.weathermood.data.db.MoodRatingEntity
import java.text.SimpleDateFormat
import java.util.*

class MoodHistoryAdapter(
    private val context: Context,
    private val moodHistory: List<MoodRatingEntity>
) : BaseAdapter() {

    override fun getCount(): Int = moodHistory.size

    override fun getItem(position: Int): MoodRatingEntity = moodHistory[position]

    override fun getItemId(position: Int): Long = moodHistory[position].id.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(
            android.R.layout.simple_list_item_2, parent, false
        )
        
        val mood = getItem(position)
        
        val dateText = view.findViewById<TextView>(android.R.id.text1)
        val ratingText = view.findViewById<TextView>(android.R.id.text2)
        
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
        
        val cityInfo = if (!mood.cityId.isNullOrEmpty()) " • ${mood.cityId}" else ""
        ratingText.text = "$ratingEmoji Оценка: $rating/5$cityInfo"
        
        return view
    }
}

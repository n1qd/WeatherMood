package com.example.weathermood.ui

import android.os.Bundle
import android.widget.ImageButton
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.weathermood.auth.UserManager
import com.example.weathermood.data.db.WeatherMoodDatabase
import com.weatherapp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MoodHistoryActivity : AppCompatActivity() {
    private lateinit var database: WeatherMoodDatabase
    private lateinit var userManager: UserManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mood_history)
        
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
        
        database = WeatherMoodDatabase.get(this)
        userManager = UserManager(this)
        loadMoodHistory()
    }
    
    private fun loadMoodHistory() {
        lifecycleScope.launch {
            try {
                // Получаем историю настроений текущего пользователя
                val currentUser = userManager.getCurrentUser()
                val userId = currentUser?.userId ?: "anonymous"
                
                val moodHistory = withContext(Dispatchers.IO) {
                    database.moodRatingDao().list(userId)
                }
                
                if (moodHistory.isEmpty()) {
                    Toast.makeText(this@MoodHistoryActivity, "История настроений пуста", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val listView = findViewById<ListView>(android.R.id.list)
                val adapter = MoodHistoryAdapter(this@MoodHistoryActivity, moodHistory)
                listView.adapter = adapter
                
            } catch (e: Exception) {
                Toast.makeText(this@MoodHistoryActivity, "Ошибка загрузки истории: ${e.message}", Toast.LENGTH_SHORT).show()
            }   
        }
    }
}





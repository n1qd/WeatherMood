package com.example.weathermood.ui

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
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
    private lateinit var adapter: MoodHistoryAdapter
    private lateinit var listView: ListView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mood_history)
        
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
        
        // Кнопка для перехода к анализу
        val btnAnalysis = findViewById<TextView>(R.id.btnAnalysis)
        if (btnAnalysis != null) {
            btnAnalysis.setOnClickListener {
                startActivity(Intent(this, MoodAnalysisActivity::class.java))
            }
        }
        
        database = WeatherMoodDatabase.get(this)
        userManager = UserManager(this)
        listView = findViewById(android.R.id.list)
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
                
                adapter = MoodHistoryAdapter(
                    this@MoodHistoryActivity,
                    moodHistory.toMutableList(),
                    onDeleteClick = { mood -> deleteMoodRating(mood) }
                )
                listView.adapter = adapter
                
            } catch (e: Exception) {
                Toast.makeText(this@MoodHistoryActivity, "Ошибка загрузки истории: ${e.message}", Toast.LENGTH_SHORT).show()
            }   
        }
    }
    
    private fun deleteMoodRating(mood: com.example.weathermood.data.db.MoodRatingEntity) {
        // Показываем диалог подтверждения
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Удалить запись?")
            .setMessage("Вы уверены, что хотите удалить эту запись о настроении?")
            .setPositiveButton("Удалить") { _, _ ->
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            database.moodRatingDao().delete(mood.id)
                        }
                        
                        // Удаляем из адаптера
                        adapter.removeItem(mood)
                        
                        // Если список пуст, показываем сообщение
                        if (adapter.count == 0) {
                            Toast.makeText(this@MoodHistoryActivity, "История настроений пуста", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MoodHistoryActivity, "Запись удалена", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@MoodHistoryActivity, "Ошибка удаления: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
}





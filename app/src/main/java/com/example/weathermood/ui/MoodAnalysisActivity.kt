package com.example.weathermood.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.example.weathermood.auth.UserManager
import com.example.weathermood.data.Prefs
import com.example.weathermood.data.db.MoodByDay
import com.example.weathermood.data.db.MoodByWeather
import com.example.weathermood.data.db.MoodRatingEntity
import com.example.weathermood.data.db.WeatherMoodDatabase
import com.weatherapp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

enum class ChartViewMode {
    DAYS, MONTHS
}

class MoodAnalysisActivity : AppCompatActivity() {
    
    private lateinit var database: WeatherMoodDatabase
    private lateinit var userManager: UserManager
    private var viewMode: ChartViewMode = ChartViewMode.DAYS
    private var allRatings: List<MoodRatingEntity> = emptyList()
    private var currentDayCalendar: Calendar = Calendar.getInstance()
    private var currentMonthCalendar: Calendar = Calendar.getInstance()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Применяем тему перед созданием активности
        val savedThemeMode = Prefs.getThemeMode(this)
        when (savedThemeMode) {
            0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mood_analysis)
        
        database = WeatherMoodDatabase.get(this)
        userManager = UserManager(this)
        
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
        
        loadAnalysis()
    }
    
    private fun loadAnalysis() {
        lifecycleScope.launch {
            try {
                val currentUser = userManager.getCurrentUser()
                val userId = currentUser?.userId ?: "anonymous"
                
                // Загружаем данные для анализа
                val moodByWeather = withContext(Dispatchers.IO) {
                    database.moodRatingDao().getMoodByWeatherCondition(userId)
                }
                
                val moodWithTemp = withContext(Dispatchers.IO) {
                    database.moodRatingDao().getMoodWithTemperature(userId)
                }
                
                val moodByDay = withContext(Dispatchers.IO) {
                    database.moodRatingDao().getMoodByDayOfWeek(userId)
                }
                
                allRatings = withContext(Dispatchers.IO) {
                    database.moodRatingDao().getAllForChart(userId)
                }
                
                // Настраиваем кнопки переключения и навигацию
                setupViewModeButtons()
                setupNavigationButtons()
                
                // Отображаем график
                displayChart(allRatings)
                
                // Отображаем анализ по погодным условиям
                displayWeatherAnalysis(moodByWeather)
                
                // Отображаем анализ по температуре
                displayTemperatureAnalysis(moodWithTemp)
                
                // Отображаем анализ по дням недели
                displayDayOfWeekAnalysis(moodByDay)
                
                // Генерируем тезисы
                generateTheses(moodByWeather, moodWithTemp, moodByDay)
                
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun setupViewModeButtons() {
        val btnDays = findViewById<TextView>(R.id.btnViewByDays)
        val btnMonths = findViewById<TextView>(R.id.btnViewByMonths)
        
        updateViewModeButtons()
        
        btnDays.setOnClickListener {
            if (viewMode != ChartViewMode.DAYS) {
                viewMode = ChartViewMode.DAYS
                currentDayCalendar = Calendar.getInstance() // Сбрасываем на сегодня
                updateViewModeButtons()
                updatePeriodLabel()
                displayChart(allRatings)
            }
        }
        
        btnMonths.setOnClickListener {
            if (viewMode != ChartViewMode.MONTHS) {
                viewMode = ChartViewMode.MONTHS
                currentMonthCalendar = Calendar.getInstance() // Сбрасываем на текущий месяц
                updateViewModeButtons()
                updatePeriodLabel()
                displayChart(allRatings)
            }
        }
    }
    
    private fun updateViewModeButtons() {
        val btnDays = findViewById<TextView>(R.id.btnViewByDays)
        val btnMonths = findViewById<TextView>(R.id.btnViewByMonths)
        val blueColor = getColor(R.color.blue)
        val activeTextColor = getColor(R.color.button_text_active)
        val inactiveTextColor = getColor(R.color.button_text_inactive)
        
        if (viewMode == ChartViewMode.DAYS) {
            btnDays.setBackgroundColor(blueColor)
            btnDays.setTextColor(activeTextColor)
            btnMonths.setBackgroundColor(getColor(android.R.color.transparent))
            btnMonths.setTextColor(inactiveTextColor)
        } else {
            btnMonths.setBackgroundColor(blueColor)
            btnMonths.setTextColor(activeTextColor)
            btnDays.setBackgroundColor(getColor(android.R.color.transparent))
            btnDays.setTextColor(inactiveTextColor)
        }
    }
    
    private fun setupNavigationButtons() {
        val btnPrev = findViewById<ImageButton>(R.id.btnPrevPeriod)
        val btnNext = findViewById<ImageButton>(R.id.btnNextPeriod)
        val tvPeriod = findViewById<TextView>(R.id.tvCurrentPeriod)
        
        updatePeriodLabel()
        
        btnPrev.setOnClickListener {
            when (viewMode) {
                ChartViewMode.DAYS -> {
                    currentDayCalendar.add(Calendar.DAY_OF_YEAR, -1)
                }
                ChartViewMode.MONTHS -> {
                    currentMonthCalendar.add(Calendar.MONTH, -1)
                }
            }
            updatePeriodLabel()
            displayChart(allRatings)
        }
        
        btnNext.setOnClickListener {
            when (viewMode) {
                ChartViewMode.DAYS -> {
                    currentDayCalendar.add(Calendar.DAY_OF_YEAR, 1)
                }
                ChartViewMode.MONTHS -> {
                    currentMonthCalendar.add(Calendar.MONTH, 1)
                }
            }
            updatePeriodLabel()
            displayChart(allRatings)
        }
    }
    
    private fun updatePeriodLabel() {
        val tvPeriod = findViewById<TextView>(R.id.tvCurrentPeriod)
        val dateFormat = when (viewMode) {
            ChartViewMode.DAYS -> SimpleDateFormat("d MMMM yyyy", Locale("ru"))
            ChartViewMode.MONTHS -> SimpleDateFormat("MMMM yyyy", Locale("ru"))
        }
        
        val calendar = when (viewMode) {
            ChartViewMode.DAYS -> currentDayCalendar
            ChartViewMode.MONTHS -> currentMonthCalendar
        }
        
        tvPeriod.text = dateFormat.format(calendar.time)
    }
    
    private fun displayChart(ratings: List<MoodRatingEntity>) {
        val chartView = findViewById<MoodChartView>(R.id.moodChartView)
        
        val processedRatings = when (viewMode) {
            ChartViewMode.DAYS -> getRatingsForDay(ratings, currentDayCalendar)
            ChartViewMode.MONTHS -> getRatingsForMonth(ratings, currentMonthCalendar)
        }
        
        chartView.setRatings(processedRatings, viewMode)
    }
    
    private fun getRatingsForDay(ratings: List<MoodRatingEntity>, dayCalendar: Calendar): List<MoodRatingEntity> {
        if (ratings.isEmpty()) return emptyList()
        
        val startOfDay = Calendar.getInstance().apply {
            timeInMillis = dayCalendar.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val endOfDay = Calendar.getInstance().apply {
            timeInMillis = dayCalendar.timeInMillis
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        
        val dayRatings = ratings.filter { rating ->
            val ratingTime = rating.createdAt
            ratingTime >= startOfDay.timeInMillis && ratingTime <= endOfDay.timeInMillis
        }
        
        // Группируем по часам
        val groupedByHour = dayRatings.groupBy { rating ->
            val cal = Calendar.getInstance()
            cal.timeInMillis = rating.createdAt
            cal.get(Calendar.HOUR_OF_DAY)
        }
        
        // Создаем записи для каждого часа (0-23)
        return (0..23).mapNotNull { hour ->
            val hourRatings = groupedByHour[hour] ?: return@mapNotNull null
            val avgRating = hourRatings.map { it.rating }.average().toInt()
            val firstRating = hourRatings.first()
            
            // Создаем время для этого часа
            val hourTime = Calendar.getInstance().apply {
                timeInMillis = startOfDay.timeInMillis
                set(Calendar.HOUR_OF_DAY, hour)
            }.timeInMillis
            
            MoodRatingEntity(
                id = firstRating.id,
                userId = firstRating.userId,
                rating = avgRating,
                weatherCondition = firstRating.weatherCondition,
                weatherDescription = firstRating.weatherDescription,
                temperature = hourRatings.mapNotNull { it.temperature }.average().takeIf { !it.isNaN() },
                feelsLike = hourRatings.mapNotNull { it.feelsLike }.average().takeIf { !it.isNaN() },
                humidity = hourRatings.mapNotNull { it.humidity }.average().toInt().takeIf { hourRatings.any { it.humidity != null } },
                pressure = hourRatings.mapNotNull { it.pressure }.average().toInt().takeIf { hourRatings.any { it.pressure != null } },
                windSpeed = hourRatings.mapNotNull { it.windSpeed }.average().takeIf { !it.isNaN() },
                note = null,
                createdAt = hourTime,
                updatedAt = hourTime,
                cityId = firstRating.cityId,
                cityName = firstRating.cityName,
                syncStatus = firstRating.syncStatus
            )
        }
    }
    
    private fun getRatingsForMonth(ratings: List<MoodRatingEntity>, monthCalendar: Calendar): List<MoodRatingEntity> {
        if (ratings.isEmpty()) return emptyList()
        
        val startOfMonth = Calendar.getInstance().apply {
            timeInMillis = monthCalendar.timeInMillis
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val endOfMonth = Calendar.getInstance().apply {
            timeInMillis = monthCalendar.timeInMillis
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        
        val monthRatings = ratings.filter { rating ->
            val ratingTime = rating.createdAt
            ratingTime >= startOfMonth.timeInMillis && ratingTime <= endOfMonth.timeInMillis
        }
        
        // Группируем по дням месяца
        val groupedByDay = monthRatings.groupBy { rating ->
            val cal = Calendar.getInstance()
            cal.timeInMillis = rating.createdAt
            cal.get(Calendar.DAY_OF_MONTH)
        }
        
        // Создаем записи для каждого дня месяца
        val daysInMonth = startOfMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
        return (1..daysInMonth).mapNotNull { day ->
            val dayRatings = groupedByDay[day] ?: return@mapNotNull null
            val avgRating = dayRatings.map { it.rating }.average().toInt()
            val firstRating = dayRatings.first()
            
            // Создаем время для этого дня
            val dayTime = Calendar.getInstance().apply {
                timeInMillis = startOfMonth.timeInMillis
                set(Calendar.DAY_OF_MONTH, day)
            }.timeInMillis
            
            MoodRatingEntity(
                id = firstRating.id,
                userId = firstRating.userId,
                rating = avgRating,
                weatherCondition = firstRating.weatherCondition,
                weatherDescription = firstRating.weatherDescription,
                temperature = dayRatings.mapNotNull { it.temperature }.average().takeIf { !it.isNaN() },
                feelsLike = dayRatings.mapNotNull { it.feelsLike }.average().takeIf { !it.isNaN() },
                humidity = dayRatings.mapNotNull { it.humidity }.average().toInt().takeIf { dayRatings.any { it.humidity != null } },
                pressure = dayRatings.mapNotNull { it.pressure }.average().toInt().takeIf { dayRatings.any { it.pressure != null } },
                windSpeed = dayRatings.mapNotNull { it.windSpeed }.average().takeIf { !it.isNaN() },
                note = null,
                createdAt = dayTime,
                updatedAt = dayTime,
                cityId = firstRating.cityId,
                cityName = firstRating.cityName,
                syncStatus = firstRating.syncStatus
            )
        }
    }
    
    private fun displayWeatherAnalysis(moodByWeather: List<MoodByWeather>) {
        val container = findViewById<LinearLayout>(R.id.weatherAnalysisContainer)
        container.removeAllViews()
        
        if (moodByWeather.isEmpty()) {
            val emptyText = TextView(this)
            emptyText.text = "Недостаточно данных для анализа"
            emptyText.setTextColor(getColor(R.color.gray_text))
            emptyText.setPadding(16, 16, 16, 16)
            container.addView(emptyText)
            return
        }
        
        moodByWeather.sortedByDescending { it.avgRating }.forEach { item ->
            val card = createAnalysisCard(
                getWeatherConditionName(item.weatherCondition),
                "Среднее настроение: ${String.format("%.1f", item.avgRating)}/5.0",
                "Записей: ${item.count}"
            )
            container.addView(card)
        }
    }
    
    private fun displayTemperatureAnalysis(moodWithTemp: List<MoodRatingEntity>) {
        val container = findViewById<LinearLayout>(R.id.temperatureAnalysisContainer)
        container.removeAllViews()
        
        if (moodWithTemp.isEmpty()) {
            return
        }
        
        // Группируем по диапазонам температуры
        val tempRanges = mapOf(
            "Очень холодно" to moodWithTemp.filter { it.temperature != null && it.temperature!! < 0 },
            "Холодно" to moodWithTemp.filter { it.temperature != null && it.temperature!! >= 0 && it.temperature!! < 10 },
            "Прохладно" to moodWithTemp.filter { it.temperature != null && it.temperature!! >= 10 && it.temperature!! < 20 },
            "Тепло" to moodWithTemp.filter { it.temperature != null && it.temperature!! >= 20 && it.temperature!! < 25 },
            "Жарко" to moodWithTemp.filter { it.temperature != null && it.temperature!! >= 25 }
        )
        
        tempRanges.forEach { (range, items) ->
            if (items.isNotEmpty()) {
                val avgRating = items.map { it.rating }.average()
                val card = createAnalysisCard(
                    range,
                    "Среднее настроение: ${String.format("%.1f", avgRating)}/5.0",
                    "Записей: ${items.size}"
                )
                container.addView(card)
            }
        }
    }
    
    private fun displayDayOfWeekAnalysis(moodByDay: List<MoodByDay>) {
        val container = findViewById<LinearLayout>(R.id.dayOfWeekAnalysisContainer)
        container.removeAllViews()
        
        if (moodByDay.isEmpty()) {
            return
        }
        
        val dayNames = arrayOf("Воскресенье", "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота")
        
        moodByDay.sortedBy { it.dayOfWeek }.forEach { item ->
            val dayName = dayNames.getOrNull(item.dayOfWeek) ?: "День ${item.dayOfWeek}"
            val card = createAnalysisCard(
                dayName,
                "Среднее настроение: ${String.format("%.1f", item.avgRating)}/5.0",
                "Записей: ${item.count}"
            )
            container.addView(card)
        }
    }
    
    private fun generateTheses(
        moodByWeather: List<MoodByWeather>,
        moodWithTemp: List<MoodRatingEntity>,
        moodByDay: List<MoodByDay>
    ) {
        val container = findViewById<LinearLayout>(R.id.thesesContainer)
        container.removeAllViews()
        
        val theses = mutableListOf<String>()
        
        // Тезисы по погодным условиям
        if (moodByWeather.isNotEmpty()) {
            val bestWeather = moodByWeather.maxByOrNull { it.avgRating }
            val worstWeather = moodByWeather.minByOrNull { it.avgRating }
            
            if (bestWeather != null && bestWeather.count >= 3) {
                theses.add("В погоду \"${getWeatherConditionName(bestWeather.weatherCondition)}\" у вас лучшее настроение (${String.format("%.1f", bestWeather.avgRating)}/5.0)")
            }
            
            if (worstWeather != null && worstWeather.count >= 3 && worstWeather != bestWeather) {
                theses.add("В погоду \"${getWeatherConditionName(worstWeather.weatherCondition)}\" у вас хуже настроение (${String.format("%.1f", worstWeather.avgRating)}/5.0)")
            }
        }
        
        // Тезисы по температуре
        if (moodWithTemp.isNotEmpty()) {
            val tempRanges = mapOf(
                "Очень холодно" to moodWithTemp.filter { it.temperature != null && it.temperature!! < 0 },
                "Холодно" to moodWithTemp.filter { it.temperature != null && it.temperature!! >= 0 && it.temperature!! < 10 },
                "Прохладно" to moodWithTemp.filter { it.temperature != null && it.temperature!! >= 10 && it.temperature!! < 20 },
                "Тепло" to moodWithTemp.filter { it.temperature != null && it.temperature!! >= 20 && it.temperature!! < 25 },
                "Жарко" to moodWithTemp.filter { it.temperature != null && it.temperature!! >= 25 }
            )
            
            val bestTempRange = tempRanges
                .filter { it.value.isNotEmpty() }
                .maxByOrNull { it.value.map { r -> r.rating }.average() }
            
            if (bestTempRange != null && bestTempRange.value.size >= 3) {
                val avgRating = bestTempRange.value.map { it.rating }.average()
                theses.add("В диапазоне \"${bestTempRange.key}\" у вас лучшее настроение (${String.format("%.1f", avgRating)}/5.0)")
            }
        }
        
        // Тезисы по дням недели
        if (moodByDay.isNotEmpty()) {
            val bestDay = moodByDay.maxByOrNull { it.avgRating }
            val worstDay = moodByDay.minByOrNull { it.avgRating }
            
            val dayNames = arrayOf("Воскресенье", "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота")
            
            if (bestDay != null && bestDay.count >= 3) {
                val dayName = dayNames.getOrNull(bestDay.dayOfWeek) ?: "День ${bestDay.dayOfWeek}"
                theses.add("В $dayName у вас лучшее настроение (${String.format("%.1f", bestDay.avgRating)}/5.0)")
            }
            
            if (worstDay != null && worstDay.count >= 3 && worstDay != bestDay) {
                val dayName = dayNames.getOrNull(worstDay.dayOfWeek) ?: "День ${worstDay.dayOfWeek}"
                theses.add("В $dayName у вас хуже настроение (${String.format("%.1f", worstDay.avgRating)}/5.0)")
            }
        }
        
        if (theses.isEmpty()) {
            val emptyText = TextView(this)
            emptyText.text = "Недостаточно данных для генерации тезисов. Добавьте больше записей о настроении."
            emptyText.setTextColor(getColor(R.color.gray_text))
            emptyText.setPadding(16, 16, 16, 16)
            container.addView(emptyText)
        } else {
            theses.forEach { thesis ->
                val card = createThesisCard(thesis)
                container.addView(card)
            }
        }
    }
    
    private fun createAnalysisCard(title: String, subtitle: String, detail: String): CardView {
        val card = CardView(this)
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, 16)
        card.layoutParams = params
        card.radius = 16f
        card.cardElevation = 4f
        card.setCardBackgroundColor(getColor(R.color.card_background))
        
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(16, 16, 16, 16)
        
        val titleView = TextView(this)
        titleView.text = title
        titleView.setTextColor(getColor(R.color.dark_text))
        titleView.textSize = 18f
        titleView.setTypeface(null, android.graphics.Typeface.BOLD)
        
        val subtitleView = TextView(this)
        subtitleView.text = subtitle
        subtitleView.setTextColor(getColor(R.color.blue))
        subtitleView.textSize = 16f
        subtitleView.setPadding(0, 8, 0, 4)
        
        val detailView = TextView(this)
        detailView.text = detail
        detailView.setTextColor(getColor(R.color.gray_text))
        detailView.textSize = 14f
        
        layout.addView(titleView)
        layout.addView(subtitleView)
        layout.addView(detailView)
        card.addView(layout)
        
        return card
    }
    
    private fun createThesisCard(thesis: String): CardView {
        val card = CardView(this)
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(0, 0, 0, 12)
        card.layoutParams = params
        card.radius = 12f
        card.cardElevation = 2f
        card.setCardBackgroundColor(getColor(R.color.card_background))
        
        val textView = TextView(this)
        textView.text = "• $thesis"
        textView.setTextColor(getColor(R.color.dark_text))
        textView.textSize = 15f
        textView.setPadding(16, 12, 16, 12)
        textView.setLineSpacing(4f, 1f)
        
        card.addView(textView)
        return card
    }
    
    private fun getWeatherConditionName(condition: String?): String {
        return when (condition?.lowercase()) {
            "clear" -> "Ясно ☀️"
            "clouds" -> "Облачно ⛅"
            "rain" -> "Дождь 🌧️"
            "snow" -> "Снег ❄️"
            "thunderstorm" -> "Гроза ⛈️"
            "drizzle" -> "Морось 🌦️"
            "mist", "fog" -> "Туман 🌫️"
            else -> condition ?: "Неизвестно"
        }
    }
}

// Кастомный View для отображения графика
class MoodChartView @JvmOverloads constructor(
    context: android.content.Context,
    attrs: android.util.AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var ratings: List<MoodRatingEntity> = emptyList()
    private var viewMode: ChartViewMode = ChartViewMode.DAYS
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    init {
        textPaint.textSize = 36f
        textPaint.color = Color.GRAY
        pathPaint.style = Paint.Style.STROKE
        pathPaint.strokeWidth = 5f
        pathPaint.color = Color.parseColor("#5B9FED")
        pathPaint.strokeCap = Paint.Cap.ROUND
        pathPaint.strokeJoin = Paint.Join.ROUND
    }
    
    fun setRatings(ratings: List<MoodRatingEntity>, viewMode: ChartViewMode = ChartViewMode.DAYS) {
        this.ratings = ratings
        this.viewMode = viewMode
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (ratings.isEmpty()) {
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("Недостаточно данных для графика", width / 2f, height / 2f, textPaint)
            return
        }
        
        val padding = 60f
        val chartWidth = width - padding * 2
        val chartHeight = height - padding * 2
        val startX = padding
        val startY = padding
        val endX = width - padding
        val endY = height - padding
        
        // Рисуем оси
        paint.color = Color.GRAY
        paint.strokeWidth = 2f
        canvas.drawLine(startX, endY, endX, endY, paint) // X ось
        canvas.drawLine(startX, startY, startX, endY, paint) // Y ось
        
        // Рисуем сетку и подписи для Y оси
        textPaint.textSize = 28f
        textPaint.color = Color.GRAY
        textPaint.textAlign = Paint.Align.RIGHT
        for (i in 1..5) {
            val y = endY - (chartHeight / 5) * i
            canvas.drawLine(startX, y, endX, y, paint.apply { alpha = 50 })
            canvas.drawText("$i", startX - 10, y + 10, textPaint)
        }
        
        // Рисуем график
        if (ratings.size > 1) {
            val path = Path()
            val pointSpacing = if (ratings.size > 1) chartWidth / (ratings.size - 1) else chartWidth
            
            ratings.forEachIndexed { index, rating ->
                val x = startX + index * pointSpacing
                val normalizedRating = (rating.rating - 1) / 4f // Нормализуем от 0 до 1
                val y = endY - (normalizedRating * chartHeight)
                
                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
                
                // Рисуем точку с обводкой
                paint.color = Color.WHITE
                paint.style = Paint.Style.FILL
                canvas.drawCircle(x, y, 8f, paint)
                paint.color = Color.parseColor("#5B9FED")
                paint.style = Paint.Style.FILL
                canvas.drawCircle(x, y, 6f, paint)
            }
            
            // Рисуем линию графика
            canvas.drawPath(path, pathPaint)
        } else if (ratings.size == 1) {
            // Если только одна точка, рисуем её красиво
            val rating = ratings[0]
            val x = startX + chartWidth / 2
            val normalizedRating = (rating.rating - 1) / 4f
            val y = endY - (normalizedRating * chartHeight)
            
            // Рисуем точку с обводкой
            paint.color = Color.WHITE
            paint.style = Paint.Style.FILL
            canvas.drawCircle(x, y, 10f, paint)
            paint.color = Color.parseColor("#5B9FED")
            paint.style = Paint.Style.FILL
            canvas.drawCircle(x, y, 8f, paint)
        }
        
        // Подписи для X оси
        if (ratings.isNotEmpty()) {
            textPaint.textSize = 22f
            textPaint.color = Color.GRAY
            textPaint.textAlign = Paint.Align.CENTER
            
            val pointSpacing = if (ratings.size > 1) chartWidth / (ratings.size - 1) else chartWidth
            val maxLabels = 12 // Максимум подписей на оси
            
            val step = maxOf(1, ratings.size / maxLabels)
            
            ratings.forEachIndexed { index, rating ->
                if (index % step == 0 || index == ratings.size - 1) {
                    val x = startX + index * pointSpacing
                    val labelText = when (viewMode) {
                        ChartViewMode.DAYS -> {
                            // Показываем часы
                            val calendar = Calendar.getInstance()
                            calendar.timeInMillis = rating.createdAt
                            "${calendar.get(Calendar.HOUR_OF_DAY)}:00"
                        }
                        ChartViewMode.MONTHS -> {
                            // Показываем дни месяца
                            val calendar = Calendar.getInstance()
                            calendar.timeInMillis = rating.createdAt
                            "${calendar.get(Calendar.DAY_OF_MONTH)}"
                        }
                    }
                    canvas.drawText(labelText, x, endY + 30, textPaint)
                }
            }
        }
        
        // Рисуем область под графиком для красоты
        if (ratings.size > 1) {
            val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.LinearGradient(
                    0f, startY.toFloat(),
                    0f, endY.toFloat(),
                    intArrayOf(
                        Color.parseColor("#5B9FED").let { Color.argb(30, Color.red(it), Color.green(it), Color.blue(it)) },
                        Color.parseColor("#5B9FED").let { Color.argb(5, Color.red(it), Color.green(it), Color.blue(it)) }
                    ),
                    null,
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
            
            val fillPath = Path()
            val pointSpacing = if (ratings.size > 1) chartWidth / (ratings.size - 1) else chartWidth
            
            ratings.forEachIndexed { index, rating ->
                val x = startX + index * pointSpacing
                val normalizedRating = (rating.rating - 1) / 4f
                val y = endY - (normalizedRating * chartHeight)
                
                if (index == 0) {
                    fillPath.moveTo(x, endY)
                    fillPath.lineTo(x, y)
                } else {
                    fillPath.lineTo(x, y)
                }
            }
            
            // Замыкаем путь
            if (ratings.isNotEmpty()) {
                val lastX = startX + (ratings.size - 1) * (if (ratings.size > 1) chartWidth / (ratings.size - 1) else chartWidth)
                fillPath.lineTo(lastX, endY)
                fillPath.close()
            }
            
            canvas.drawPath(fillPath, gradientPaint)
        }
    }
}


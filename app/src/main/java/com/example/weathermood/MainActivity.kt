package com.example.weathermood
import com.weatherapp.R
import com.example.weathermood.api.ApiClient
import com.example.weathermood.data.Prefs
import com.example.weathermood.data.db.WeatherMoodDatabase
import com.example.weathermood.data.WeatherResponse
import com.example.weathermood.data.db.MoodRatingEntity
import com.example.weathermood.sync.SyncManager
import com.example.weathermood.auth.UserManager
import com.example.weathermood.data.CityManager
import com.example.weathermood.utils.LocationHelper
import com.example.weathermood.utils.LocationData

import android.os.Bundle
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout    
import com.example.weathermood.api.ApiClient.weatherApi
import com.example.weathermood.api.WeatherApi
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.ScrollView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var database: WeatherMoodDatabase
    private lateinit var syncManager: SyncManager
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var userManager: UserManager
    private lateinit var cityManager: CityManager
    private lateinit var locationHelper: LocationHelper
    private val TAG = "WeatherApp"
    private var isLoading = false
    private var currentWeather: WeatherResponse? = null
    private var useFahrenheit = false
    private var useMph = false
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        // Применяем тему перед созданием активности
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        database = WeatherMoodDatabase.get(this)
        syncManager = SyncManager(this)
        userManager = UserManager(this)
        cityManager = CityManager(this)
        locationHelper = LocationHelper(this)
        
        // Загружаем настройки
        useFahrenheit = Prefs.getUseFahrenheit(this)
        useMph = Prefs.getUseMph(this)
        
        // Запрашиваем разрешения на местоположение, если их нет
        requestLocationPermissionIfNeeded()
        
        // Настройка бокового меню
        drawerLayout = findViewById(R.id.drawer_layout)
        val navView = findViewById<NavigationView>(R.id.nav_view)
        navView.setNavigationItemSelectedListener(this)
        
        setupNavigationHeader(navView)
        loadUserCitiesIntoMenu(navView)
        setupSwipeRefresh()
        setupUI()
        loadWeatherData()
        
        // Запускаем периодическую синхронизацию
        syncManager.schedulePeriodicSync()
        
        // Синхронизируем данные с Firestore при запуске (в фоне, без блокировки)
        lifecycleScope.launch {
            try {
                userManager.syncWithFirestore()
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка синхронизации при запуске: ${e.message}")
                // Приложение продолжает работать даже при ошибке синхронизации
            }
        }
    }
    
    private fun loadUserCitiesIntoMenu(navView: NavigationView) {
        lifecycleScope.launch {
            try {
                val favoriteCities = cityManager.getFavoriteCities()
                
                // Получаем контейнер из header
                val headerView = navView.getHeaderView(0)
                val citiesContainer = headerView.findViewById<LinearLayout>(R.id.citiesContainer)
                citiesContainer.removeAllViews()
                
                // Добавляем кнопку "Текущее положение"
                val currentLocationView = LayoutInflater.from(this@MainActivity)
                    .inflate(R.layout.nav_city_item, citiesContainer, false)
                val tvCurrentLocation = currentLocationView.findViewById<TextView>(R.id.tvCityName)
                val btnDeleteCurrent = currentLocationView.findViewById<ImageButton>(R.id.btnDeleteCity)
                
                val useCurrentLocation = Prefs.getUseCurrentLocation(this@MainActivity)
                tvCurrentLocation.text = "📍 Текущее положение"
                if (useCurrentLocation) {
                    // Выделяем активную кнопку
                    tvCurrentLocation.setTextColor(getColor(R.color.accent_gold))
                    tvCurrentLocation.setTypeface(null, android.graphics.Typeface.BOLD)
                } else {
                    tvCurrentLocation.setTextColor(getColor(android.R.color.white))
                    tvCurrentLocation.setTypeface(null, android.graphics.Typeface.NORMAL)
                }
                btnDeleteCurrent.visibility = View.GONE // Скрываем кнопку удаления
                
                currentLocationView.setOnClickListener {
                    Prefs.setUseCurrentLocation(this@MainActivity, true)
                    loadWeatherData()
                    Toast.makeText(this@MainActivity, "Используется текущее местоположение", Toast.LENGTH_SHORT).show()
                    drawerLayout.closeDrawer(GravityCompat.START)
                    // Обновляем меню, чтобы показать выделение
                    loadUserCitiesIntoMenu(navView)
                }
                citiesContainer.addView(currentLocationView)
                
                // Добавляем кнопку "Добавить город"
                val addCityView = LayoutInflater.from(this@MainActivity)
                    .inflate(R.layout.nav_city_item, citiesContainer, false)
                val tvAddCity = addCityView.findViewById<TextView>(R.id.tvCityName)
                val btnDeleteAdd = addCityView.findViewById<ImageButton>(R.id.btnDeleteCity)
                
                tvAddCity.text = "➕ Добавить локацию"
                btnDeleteAdd.visibility = View.GONE // Скрываем кнопку удаления для "Добавить"
                
                addCityView.setOnClickListener {
                    startActivity(Intent(this@MainActivity, com.example.weathermood.ui.CityPickerActivity::class.java))
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
                citiesContainer.addView(addCityView)
                
                // Добавляем избранные города пользователя
                if (favoriteCities.isEmpty()) {
                    val emptyView = TextView(this@MainActivity)
                    emptyView.text = "Добавьте город"
                    emptyView.setTextColor(getColor(android.R.color.white))
                    emptyView.alpha = 0.6f
                    emptyView.setPadding(16, 16, 16, 16)
                    citiesContainer.addView(emptyView)
                } else {
                    favoriteCities.forEach { city ->
                        val cityView = LayoutInflater.from(this@MainActivity)
                            .inflate(R.layout.nav_city_item, citiesContainer, false)
                        
                        val tvCityName = cityView.findViewById<TextView>(R.id.tvCityName)
                        val btnDelete = cityView.findViewById<ImageButton>(R.id.btnDeleteCity)
                        
                        val displayName = city.cityName.split(",")[0].trim()
                        val icon = if (city.isDefault) "⭐" else "📍"
                        tvCityName.text = "$icon $displayName"
                        
                        // Выделяем выбранный город, если не используется текущее местоположение
                        val useCurrentLocation = Prefs.getUseCurrentLocation(this@MainActivity)
                        val selectedCity = Prefs.getSelectedCity(this@MainActivity)
                        if (!useCurrentLocation && displayName == selectedCity) {
                            tvCityName.setTextColor(getColor(R.color.accent_gold))
                            tvCityName.setTypeface(null, android.graphics.Typeface.BOLD)
                        } else {
                            tvCityName.setTextColor(getColor(android.R.color.white))
                            tvCityName.setTypeface(null, android.graphics.Typeface.NORMAL)
                        }
                        
                        // Клик на название города - выбор города
                        cityView.setOnClickListener {
                            Prefs.setUseCurrentLocation(this@MainActivity, false)
                            Prefs.setSelectedCity(this@MainActivity, displayName)
                            loadWeatherData()
                            Toast.makeText(this@MainActivity, "Выбран город: $displayName", Toast.LENGTH_SHORT).show()
                            drawerLayout.closeDrawer(GravityCompat.START)
                            // Обновляем меню, чтобы показать выделение
                            loadUserCitiesIntoMenu(navView)
                        }
                        
                        // Клик на кнопку удаления
                        btnDelete.setOnClickListener {
                            showDeleteCityDialog(city.id, displayName)
                        }
                        
                        citiesContainer.addView(cityView)
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки городов: ${e.message}", e)
            }
        }
    }
    
    private fun applyTheme() {
        val themeMode = Prefs.getThemeMode(this)
        when (themeMode) {
            0 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO) // Светлая
            1 -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) // Тёмная
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) // Системная
        }
    }
    
    private fun requestLocationPermissionIfNeeded() {
        if (!locationHelper.hasLocationPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Разрешение получено, загружаем погоду по текущему местоположению
                if (Prefs.getUseCurrentLocation(this)) {
                    loadWeatherData()
                }
            } else {
                // Разрешение отклонено, используем дефолтный город
                Prefs.setUseCurrentLocation(this, false)
                Toast.makeText(this, "Разрешение на местоположение отклонено. Используется дефолтный город.", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun showDeleteCityDialog(cityId: Int, cityName: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Удалить город?")
            .setMessage("Удалить $cityName из избранного?")
            .setPositiveButton("Удалить") { _, _ ->
                lifecycleScope.launch {
                    try {
                        cityManager.removeFavoriteCity(cityId)
                        Toast.makeText(this@MainActivity, "$cityName удален", Toast.LENGTH_SHORT).show()
                        // Обновляем список городов
                        val navView = findViewById<NavigationView>(R.id.nav_view)
                        loadUserCitiesIntoMenu(navView)
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Ошибка удаления: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun setupNavigationHeader(navView: NavigationView) {
        val headerView = navView.getHeaderView(0)
        val btnAuthCard = headerView.findViewById<CardView>(R.id.btnAuthCard)
        val tvUserName = headerView.findViewById<TextView>(R.id.tvUserName)
        val tvUserEmail = headerView.findViewById<TextView>(R.id.tvUserEmail)
        val tvAuthButton = headerView.findViewById<TextView>(R.id.tvAuthButton)
        
        // Обновляем UI в зависимости от статуса авторизации
        updateUserUI(tvUserName, tvUserEmail, tvAuthButton)
        
        // Обработчик кнопки авторизации
        btnAuthCard.setOnClickListener {
            val currentUser = userManager.getCurrentUser()
            if (currentUser != null && !currentUser.isAnonymous) {
                // Пользователь авторизован - показываем меню выхода
                showLogoutDialog()
            } else {
                // Пользователь не авторизован - открываем экран входа
                startActivity(Intent(this, com.example.weathermood.ui.LoginActivity::class.java))
            }
            drawerLayout.closeDrawer(GravityCompat.START)
        }
    }
    
    private fun updateUserUI(tvUserName: TextView, tvUserEmail: TextView, tvAuthButton: TextView) {
        val currentUser = userManager.getCurrentUser()
        
        if (currentUser != null && !currentUser.isAnonymous) {
            // Авторизованный пользователь
            tvUserName.text = currentUser.displayName
            tvUserEmail.text = currentUser.email ?: "Нет email"
            tvAuthButton.text = "Выйти из аккаунта"
        } else {
            // Гость или анонимный пользователь
            tvUserName.text = "Гость"
            tvUserEmail.text = "Войдите в аккаунт"
            tvAuthButton.text = "Войти / Регистрация"
        }
    }
    
    private fun showLogoutDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Выход из аккаунта")
            .setMessage("Вы уверены, что хотите выйти?")
            .setPositiveButton("Выйти") { _, _ ->
                lifecycleScope.launch {
                    userManager.logout()
                    Toast.makeText(this@MainActivity, "Вы вышли из аккаунта", Toast.LENGTH_SHORT).show()
                    // Перезапускаем активность для обновления UI
                    recreate()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun setupSwipeRefresh() {
        val scrollView = findViewById<ScrollView>(R.id.scrollView)
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)
        
        // Контролируем, когда SwipeRefresh может быть активирован
        swipeRefreshLayout.setOnChildScrollUpCallback { parent, child ->
            // Возвращаем true, если ScrollView может прокручиваться вверх (т.е. не в верхней позиции)
            // Если true - SwipeRefresh отключен, если false - SwipeRefresh работает
            scrollView.scrollY > 0
        }
        
        swipeRefreshLayout.setOnRefreshListener {
            Log.d(TAG, "🔄 Свайп для обновления обнаружен")
            loadWeatherData()
        }
        
        // Устанавливаем цвета индикатора загрузки
        swipeRefreshLayout.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light,
            android.R.color.holo_red_light
        )
    }

    private fun setupUI() {
        // Кнопка открытия бокового меню
        findViewById<ImageButton>(R.id.btnMenu).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        findViewById<ImageButton>(R.id.btnSearch).setOnClickListener {
            startActivity(Intent(this, com.example.weathermood.ui.CityPickerActivity::class.java))
        }

        // Клик на виджет погоды для показа деталей
        findViewById<CardView>(R.id.weatherCard).setOnClickListener {
            currentWeather?.let { weather ->
                showWeatherDetailsDialog(weather)
            }
        }

        // Обработка RatingBar настроения
        val ratingBar = findViewById<RatingBar>(R.id.ratingMood)
        ratingBar.setOnRatingBarChangeListener { _, rating, fromUser ->
            if (fromUser && rating > 0) {
                val currentTime = System.currentTimeMillis()
                val lastRatingTime = Prefs.getLastMoodRatingTime(this)
                val timeSinceLastRating = currentTime - lastRatingTime
                val oneMinuteInMillis = 60 * 1000L
                
                if (timeSinceLastRating < oneMinuteInMillis) {
                    val secondsLeft = (oneMinuteInMillis - timeSinceLastRating) / 1000
                    Toast.makeText(
                        this, 
                        "Подождите ${secondsLeft} сек. перед следующей оценкой", 
                        Toast.LENGTH_SHORT
                    ).show()
                    // Сбрасываем рейтинг обратно
                    ratingBar.rating = 0f
                } else {
                    saveMoodRating(rating)
                    Prefs.setLastMoodRatingTime(this, currentTime)
                    val moodText = when (rating.toInt()) {
                        1 -> "😢 Очень плохо"
                        2 -> "☹️ Плохо"
                        3 -> "😐 Нормально"
                        4 -> "😊 Хорошо"
                        5 -> "🤩 Отлично!"
                        else -> "Спасибо!"
                    }
                    Toast.makeText(this, "$moodText - Настроение сохранено!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        setCurrentDate()
        showLoadingMessage()
    }

    private fun setCurrentDate() {
        try {
            val currentDate = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("ru"))
                .format(Date())
            findViewById<TextView>(R.id.tvDate).text = currentDate
        } catch (e: Exception) {
            findViewById<TextView>(R.id.tvDate).text = "Сегодня"
        }
    }

    private fun showLoadingMessage() {
        findViewById<TextView>(R.id.tvCity).text = "Загрузка..."
        findViewById<TextView>(R.id.tvTemperature).text = "--°"
        findViewById<TextView>(R.id.tvWeatherCondition).text = "Получаем данные..."
        findViewById<TextView>(R.id.tvFeelsLike).text = "Ощущается как --°"
        findViewById<TextView>(R.id.tvWind).text = "Ветер: -- м/с"
        findViewById<TextView>(R.id.tvHumidity).text = "Влажность: --%"
        findViewById<TextView>(R.id.tvPrecipitation).text = "-- hPa"
    }
    
    /**
     * Проверяет наличие активного интернет-соединения
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun loadWeatherData() {
        Log.d(TAG, "=== НАЧАЛО ЗАГРУЗКИ ДАННЫХ ===")
        
        // Если уже идет загрузка, не запускаем повторно
        if (isLoading) {
            Log.d(TAG, "⚠️ Загрузка уже выполняется, пропускаем")
            swipeRefreshLayout.isRefreshing = false
            return
        }
        
        // Проверяем интернет-соединение перед запросом
        if (!isNetworkAvailable()) {
            Log.e(TAG, "❌ НЕТ ИНТЕРНЕТ-СОЕДИНЕНИЯ")
            swipeRefreshLayout.isRefreshing = false
            Toast.makeText(this, "Нет подключения к интернету. Проверьте настройки сети.", Toast.LENGTH_LONG).show()
            findViewById<TextView>(R.id.tvCity).text = "Нет соединения"
            findViewById<TextView>(R.id.tvWeatherCondition).text = "Проверьте интернет"
            tryLoadFromCache()
            return
        }

        isLoading = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val useCurrentLocation = Prefs.getUseCurrentLocation(this@MainActivity)
                val httpResponse: retrofit2.Response<WeatherResponse>
                
                if (useCurrentLocation && locationHelper.hasLocationPermission()) {
                    // Получаем погоду по текущему местоположению
                    Log.d(TAG, "1. Получаем текущее местоположение...")
                    val locationData = locationHelper.getCurrentLocationOnce()
                    
                    if (locationData != null) {
                        Log.d(TAG, "   Координаты: lat=${locationData.latitude}, lon=${locationData.longitude}")
                        httpResponse = kotlinx.coroutines.withTimeout(30000) {
                            ApiClient.weatherApi.getWeatherByCoordinates(
                                lat = locationData.latitude,
                                lon = locationData.longitude,
                                apiKey = Constants.API_KEY
                            )
                        }
                    } else {
                        Log.w(TAG, "   Не удалось получить местоположение, используем дефолтный город")
                        val selectedCity = Prefs.getSelectedCity(this@MainActivity, Constants.DEFAULT_CITY)
                        httpResponse = kotlinx.coroutines.withTimeout(30000) {
                            ApiClient.weatherApi.getWeather(
                                city = selectedCity,
                                apiKey = Constants.API_KEY
                            )
                        }
                    }
                } else {
                    // Используем выбранный город
                    val selectedCity = Prefs.getSelectedCity(this@MainActivity, Constants.DEFAULT_CITY)
                    Log.d(TAG, "1. Вызываем API для города: $selectedCity")
                    httpResponse = kotlinx.coroutines.withTimeout(30000) {
                        ApiClient.weatherApi.getWeather(
                            city = selectedCity,
                            apiKey = Constants.API_KEY
                        )
                    }
                }
                
                Log.d(TAG, "   API Key: ${Constants.API_KEY.take(8)}...")

                Log.d(TAG, "HTTP code getWeather: ${'$'}{httpResponse.code()} success=${'$'}{httpResponse.isSuccessful}")
                if (!httpResponse.isSuccessful) {
                    val code = httpResponse.code()
                    val body = httpResponse.errorBody()?.string()
                    Log.e(TAG, "HTTP ${'$'}code при getWeather, errorBody=${'$'}body")
                    throw RuntimeException("Weather HTTP ${'$'}code")
                }

                val response = httpResponse.body() ?: throw RuntimeException("Пустой ответ getWeather")

                Log.d(TAG, "2. ✅ ОТВЕТ ОТ API ПОЛУЧЕН!")
                Log.d(TAG, "   - Город: ${'$'}{response.cityName}")
                Log.d(TAG, "   - Температура: ${'$'}{response.main.temp}°C")
                Log.d(TAG, "   - Погода: ${'$'}{response.weather.firstOrNull()?.main}")
                Log.d(TAG, "   - Описание: ${'$'}{response.weather.firstOrNull()?.description}")
                Log.d(TAG, "   - Влажность: ${'$'}{response.main.humidity}%")
                Log.d(TAG, "   - Ветер: ${'$'}{response.wind.speed} м/с")

                withContext(Dispatchers.Main) {
                    Log.d(TAG, "3. Переходим в главный поток...")
                    swipeRefreshLayout.isRefreshing = false
                    updateUIWithWeatherData(response)
                    // Загружаем реальный почасовой прогноз после успешного получения текущей погоды
                    setupRealHourlyForecast()
                    Toast.makeText(this@MainActivity, "Данные обновлены! ✅", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "4. ✅ ИНТЕРФЕЙС ОБНОВЛЕН!")
                }

            } catch (e: Exception) {
                // Расширенная диагностика ошибок сети/парсинга
                val rootMsg = e.message ?: e.toString()
                Log.e(TAG, "❌ ОШИБКА ПРИ ЗАПРОСЕ ПОГОДЫ: $rootMsg", e)
                
                val errorMessage = when (e) {
                    is retrofit2.HttpException -> {
                        val code = e.code()
                        val body = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
                        Log.e(TAG, "HTTP $code, errorBody=$body")
                        when (code) {
                            401 -> "Неверный API ключ"
                            404 -> "Город не найден"
                            429 -> "Превышен лимит запросов"
                            else -> "Ошибка сервера: $code"
                        }
                    }
                    is kotlinx.coroutines.TimeoutCancellationException -> {
                        Log.e(TAG, "Таймаут запроса (30 сек)")
                        "Превышено время ожидания. Проверьте интернет-соединение"
                    }
                    is java.net.UnknownHostException -> {
                        Log.e(TAG, "Нет интернета или DNS недоступен")
                        "Нет подключения к интернету"
                    }
                    is java.net.SocketTimeoutException -> {
                        Log.e(TAG, "Таймаут сокета")
                        "Медленное интернет-соединение"
                    }
                    is com.google.gson.JsonSyntaxException -> {
                        Log.e(TAG, "Ошибка парсинга JSON: ${e.localizedMessage}")
                        "Ошибка обработки данных"
                    }
                    else -> {
                        Log.e(TAG, "Неизвестная ошибка: ${e.javaClass.simpleName}")
                        "Не удалось загрузить погоду"
                    }
                }

                withContext(Dispatchers.Main) {
                    swipeRefreshLayout.isRefreshing = false
                    Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG).show()
                    
                    // Показываем сообщение об ошибке на экране
                    findViewById<TextView>(R.id.tvCity).text = "Ошибка загрузки"
                    findViewById<TextView>(R.id.tvWeatherCondition).text = errorMessage
                    
                    // Пытаемся загрузить из кэша
                    tryLoadFromCache()
                }
            }
            finally {
                isLoading = false
            }
        }
    }

    private fun updateUIWithWeatherData(weather: WeatherResponse) {
        try {
            Log.d(TAG, "=== ОБНОВЛЕНИЕ ИНТЕРФЕЙСА ===")

            // Сохраняем текущую погоду
            currentWeather = weather
            
            // Обновляем фон и иконку погоды
            updateWeatherBackground(weather)

            // Город и страна (получаем страну из sys.country если доступно)
            val country = weather.sys?.country ?: ""
            val countryName = when(country) {
                "RU" -> "Россия"
                "US" -> "США"
                "GB" -> "Великобритания"
                "DE" -> "Германия"
                "FR" -> "Франция"
                "IT" -> "Италия"
                "ES" -> "Испания"
                "CN" -> "Китай"
                "JP" -> "Япония"
                "KR" -> "Корея"
                "UA" -> "Украина"
                "BY" -> "Беларусь"
                "KZ" -> "Казахстан"
                else -> country
            }
            findViewById<TextView>(R.id.tvCity).text = if (countryName.isNotEmpty()) {
                "${weather.cityName}, $countryName"
            } else {
                weather.cityName
            }
            
            // Температура с поддержкой F/C
            val temp = if (useFahrenheit) celsiusToFahrenheit(weather.main.temp) else weather.main.temp
            val feelsLike = if (useFahrenheit) celsiusToFahrenheit(weather.main.feelsLike) else weather.main.feelsLike
            val unit = if (useFahrenheit) "°F" else "°C"
            
            findViewById<TextView>(R.id.tvTemperature).text = "${temp.toInt()}$unit"
            findViewById<TextView>(R.id.tvFeelsLike).text = "Ощущается как ${feelsLike.toInt()}$unit"

            // Погодные условия (без эмодзи)
            val weatherCondition = weather.weather.firstOrNull()
            if (weatherCondition != null) {
                val conditionText = when(weatherCondition.main.lowercase()) {
                    "clear" -> "Ясно"
                    "clouds" -> "Облачно"
                    "rain" -> "Дождь"
                    "snow" -> "Снег"
                    "thunderstorm" -> "Гроза"
                    "drizzle" -> "Морось"
                    else -> weatherCondition.description ?: "Неизвестно"
                }
                findViewById<TextView>(R.id.tvWeatherCondition).text = conditionText.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }

            // Влажность, ветер и давление (без эмодзи)
            findViewById<TextView>(R.id.tvHumidity).text = "Влажность: ${weather.main.humidity}%"
            
            // Скорость ветра с учетом настроек
            val windSpeed = if (useMph) {
                val mph = weather.wind.speed * 2.23694  // м/с в миль/ч
                String.format("%.1f", mph) + " миль/ч"
            } else {
                String.format("%.1f", weather.wind.speed) + " м/с"
            }
            findViewById<TextView>(R.id.tvWind).text = "Ветер: $windSpeed"
            
            findViewById<TextView>(R.id.tvPrecipitation).text = "${weather.main.pressure} hPa"

            // Генерируем советы
            generateAdvice(weather)

            // Почасовой прогноз загружается реальными данными отдельно

            Log.d(TAG, "=== ИНТЕРФЕЙС УСПЕШНО ОБНОВЛЕН ===")

        } catch (e: Exception) {
            Log.e(TAG, "❌ ОШИБКА ПРИ ОБНОВЛЕНИИ ИНТЕРФЕЙСА: ${e.message}", e)
            Toast.makeText(this, "Ошибка обновления интерфейса", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun celsiusToFahrenheit(celsius: Double): Double {
        return celsius * 9 / 5 + 32
    }
    
    private fun updateWeatherBackground(weather: WeatherResponse) {
        try {
            val weatherCondition = weather.weather.firstOrNull()?.main?.lowercase() ?: "default"
            val weatherCardBackground = findViewById<RelativeLayout>(R.id.weatherCardBackground)
            val weatherIconLarge = findViewById<TextView>(R.id.tvWeatherIconLarge)
            
            // Определяем фон и иконку в зависимости от погоды
            val (backgroundRes, iconText) = when (weatherCondition) {
                "clear" -> {
                    Pair(R.drawable.anim_weather_clear, "☀️")
                }
                "clouds" -> {
                    Pair(R.drawable.anim_weather_clouds, "☁️")
                }
                "rain", "drizzle" -> {
                    Pair(R.drawable.anim_weather_rain, "🌧️")
                }
                "snow" -> {
                    Pair(R.drawable.anim_weather_snow, "❄️")
                }
                "thunderstorm" -> {
                    Pair(R.drawable.anim_weather_thunderstorm, "⛈️")
                }
                else -> {
                    Pair(R.drawable.anim_weather_default, "🌤️")
                }
            }
            
            // Устанавливаем анимированный фон
            weatherCardBackground.setBackgroundResource(backgroundRes)
            
            // Запускаем анимацию фона
            val background = weatherCardBackground.background
            if (background is android.graphics.drawable.AnimationDrawable) {
                background.start()
            }
            
            // Устанавливаем иконку
            weatherIconLarge.text = iconText
            
            // Анимация появления иконки
            weatherIconLarge.alpha = 0f
            weatherIconLarge.scaleX = 0.5f
            weatherIconLarge.scaleY = 0.5f
            weatherIconLarge.animate()
                .alpha(0.5f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(800)
                .withEndAction {
                    // После появления запускаем пульсацию
                    startIconPulseAnimation(weatherIconLarge)
                }
                .start()
            
            Log.d(TAG, "Фон погоды обновлен: $weatherCondition -> $iconText")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обновления фона погоды: ${e.message}", e)
        }
    }
    
    private fun startIconPulseAnimation(view: TextView) {
        // Создаем пульсирующую анимацию для иконки
        view.animate()
            .scaleX(1.08f)
            .scaleY(1.08f)
            .alpha(0.6f)
            .setDuration(2500)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(0.5f)
                    .setDuration(2500)
                    .withEndAction {
                        // Повторяем анимацию бесконечно
                        startIconPulseAnimation(view)
                    }
                    .start()
            }
            .start()
    }

    private fun generateAdvice(weather: WeatherResponse) {
        try {
            // Используем ощущаемую температуру (feelsLike) для всех советов
            val temp = weather.main.feelsLike.toInt()
            val condition = weather.weather.firstOrNull()?.main?.lowercase() ?: ""
            val conditionDesc = weather.weather.firstOrNull()?.description?.lowercase() ?: ""
            val windSpeed = weather.wind.speed
            val humidity = weather.main.humidity
            val pressure = weather.main.pressure
            
            // Советы по одежде на основе ощущаемой температуры и условий
            val clothingAdvice = when {
                // Экстремально холодно
                temp < -15 -> {
                    val base = "Экстремально холодно! Обязательно: пуховик, термобелье, шапка, шарф, перчатки, теплые сапоги ❄️"
                    if (windSpeed > 8) base + " Сильный ветер усилит холод!" else base
                }
                // Очень холодно
                temp < -10 -> {
                    val base = "Очень холодно! Наденьте теплую зимнюю куртку, шапку, шарф и перчатки ❄️"
                    if (humidity > 80) base + " Высокая влажность усилит ощущение холода." else base
                }
                // Холодно
                temp < -5 -> {
                    when {
                        windSpeed > 7 -> "Холодно и ветрено! Зимняя куртка с капюшоном, шапка, шарф, перчатки 🧥🌬️"
                        humidity > 75 -> "Холодно и сыро! Водонепроницаемая куртка, теплые слои одежды, шапка 🧥💧"
                        else -> "Холодно! Зимняя куртка, теплый свитер, шапка, перчатки 🧥"
                    }
                }
                // Морозно
                temp < 0 -> {
                    when {
                        condition.contains("snow") -> "Мороз и снег! Зимняя куртка, водонепроницаемые ботинки, шапка, варежки ❄️🥾"
                        windSpeed > 5 -> "Морозно и ветрено! Теплая куртка, шапка, шарф, закрывающий лицо 🧣🌬️"
                        else -> "Морозно! Зимняя куртка, теплый свитер, шапка, перчатки 🧥"
                    }
                }
                // Прохладно
                temp < 5 -> {
                    when {
                        condition.contains("rain") -> "Прохладно и дождливо! Водонепроницаемая куртка, зонт, закрытая обувь ☂️🧥"
                        windSpeed > 6 -> "Прохладно и ветрено! Ветровка, легкий свитер, закрытая обувь 🌬️🧣"
                        humidity > 70 -> "Прохладно и влажно! Легкая куртка, дышащие материалы, зонт на всякий случай 💧"
                        else -> "Прохладно. Легкая куртка или плотный свитер, длинные брюки 🧣"
                    }
                }
                // Свежо
                temp < 10 -> {
                    when {
                        condition.contains("rain") || condition.contains("drizzle") -> "Свежо и дождливо! Легкая куртка, зонт, непромокаемая обувь ☂️"
                        windSpeed > 5 -> "Свежо и ветрено! Ветровка или легкая куртка, шарф 🌬️"
                        condition.contains("clouds") -> "Свежо и пасмурно. Легкая куртка или кардиган, длинные брюки ☁️"
                        else -> "Свежо. Легкая куртка или свитер, длинные брюки 🧥"
                    }
                }
                // Умеренно
                temp < 15 -> {
                    when {
                        condition.contains("rain") -> "Умеренно и дождливо! Легкая куртка, зонт, закрытая обувь ☂️"
                        windSpeed > 4 -> "Умеренно и ветрено! Легкая ветровка или кардиган 🌬️"
                        condition.contains("clear") -> "Умеренно и ясно. Легкая кофта или рубашка с длинным рукавом 👔"
                        else -> "Умеренная погода. Легкая кофта или рубашка, можно снять при потеплении 👔"
                    }
                }
                // Комфортно
                temp < 20 -> {
                    when {
                        condition.contains("rain") -> "Комфортно, но дождь! Легкая куртка или дождевик, зонт ☂️"
                        windSpeed > 3 -> "Комфортно, но ветрено! Легкая кофта или ветровка 🌬️"
                        condition.contains("clear") && humidity < 50 -> "Комфортно и сухо! Легкая кофта или рубашка, можно футболку 👔"
                        else -> "Комфортная погода. Легкая кофта или рубашка 👔"
                    }
                }
                // Тепло
                temp < 25 -> {
                    when {
                        condition.contains("rain") -> "Тепло, но дождь! Легкая куртка или дождевик, зонт ☂️"
                        humidity > 70 -> "Тепло и влажно! Легкая дышащая одежда, можно взять легкую кофту 💧"
                        condition.contains("clear") -> "Тепло и ясно! Футболка или легкая блузка, шорты или легкие брюки 👕"
                        windSpeed > 4 -> "Тепло, но ветрено! Легкая одежда, можно легкую кофту 🌬️"
                        else -> "Тепло! Футболка или легкая блузка, шорты или легкие брюки 👕"
                    }
                }
                // Жарко
                temp < 30 -> {
                    when {
                        humidity > 75 -> "Жарко и влажно! Легкая дышащая одежда, головной убор, много воды ☀️💧"
                        condition.contains("clear") -> "Жарко и солнечно! Легкая одежда, головной убор, солнцезащитный крем ☀️"
                        windSpeed > 3 -> "Жарко, но ветерок! Легкая одежда, можно без куртки 🌬️"
                        else -> "Жарко! Легкая одежда, головной убор, солнцезащитный крем ☀️"
                    }
                }
                // Очень жарко
                temp < 35 -> {
                    when {
                        humidity > 80 -> "Очень жарко и влажно! Минимум одежды, дышащие материалы, головной убор, вода ☀️💧"
                        condition.contains("clear") -> "Очень жарко и солнечно! Легкая одежда, головной убор, солнцезащитный крем, избегайте прямого солнца ☀️"
                        else -> "Очень жарко! Легкая одежда, головной убор, много воды, избегайте перегрева ☀️"
                    }
                }
                // Экстремально жарко
                else -> {
                    when {
                        humidity > 75 -> "Экстремально жарко и влажно! Минимум одежды, дышащие материалы, головной убор, много воды, избегайте активности на солнце ☀️💧"
                        else -> "Экстремально жарко! Легкая одежда, головной убор, солнцезащитный крем, много воды, избегайте прямого солнца ☀️"
                    }
                }
            }
            
            // Дополнительные советы по одежде
            val additionalClothing = buildString {
                if (condition.contains("rain") || condition.contains("drizzle")) {
                    append(" Возьмите зонт и непромокаемую обувь! ☂️")
                }
                if (condition.contains("snow")) {
                    append(" Теплые ботинки с протектором обязательны! 🥾")
                }
                if (condition.contains("thunderstorm")) {
                    append(" Избегайте металлических аксессуаров во время грозы! ⚡")
                }
                if (windSpeed > 8) {
                    append(" Сильный ветер - закрепите головной убор! 🌬️")
                }
                if (humidity > 85 && temp > 20) {
                    append(" Высокая влажность - выбирайте дышащие материалы! 💧")
                }
                if (pressure < 1000 && condition.contains("rain")) {
                    append(" Низкое давление - возможен сильный дождь! 🌧️")
                }
            }
            
            findViewById<TextView>(R.id.tvClothingAdvice).text = clothingAdvice + additionalClothing
            
            // Эко-советы на основе всех параметров погоды
            val ecoAdvice = when {
                // Комбинации условий для более точных советов
                condition.contains("clear") && temp in 18..26 && windSpeed < 3 && humidity < 60 -> 
                    "Идеальная погода для велопрогулки! Оставьте машину дома, насладитесь поездкой 🚴"
                
                condition.contains("clear") && temp in 15..25 && windSpeed < 5 -> 
                    "Отличная погода для активного отдыха! Прогулка пешком или на велосипеде вместо транспорта 🚴🚶"
                
                condition.contains("rain") && temp > 5 -> 
                    "Дождевая вода - ценный ресурс! Соберите её для полива растений, это экономит водопроводную воду 🌧️💧"
                
                condition.contains("rain") && temp < 5 -> 
                    "Дождливая погода - отличное время проверить утеплители окон и дверей, чтобы экономить тепло 🏠♻️"
                
                temp > 28 && humidity < 50 && condition.contains("clear") -> 
                    "Жаркий солнечный день! Используйте жалюзи или шторы днем, чтобы снизить нагрузку на кондиционер ☀️💡"
                
                temp > 30 && humidity > 70 -> 
                    "Жарко и влажно! Выключайте кондиционер когда уходите, используйте вентилятор вместо кондиционера когда возможно ❄️💡"
                
                temp > 25 && condition.contains("clear") -> 
                    "Жаркий день! Экономьте энергию - выключайте кондиционер когда уходите, используйте естественную вентиляцию ❄️💡"
                
                temp < 0 && windSpeed < 3 -> 
                    "Морозная погода без ветра - отличное время проверить утеплители, заделать щели в окнах 🏠♻️"
                
                temp < 5 && windSpeed > 5 -> 
                    "Холодно и ветрено! Утеплите окна и двери, чтобы экономить тепло и снизить расходы на отопление 🏠♻️"
                
                temp < 5 -> 
                    "Холодная погода! Утеплите окна, заделайте щели, чтобы экономить тепло и снизить выбросы CO2 🏠♻️"
                
                windSpeed > 12 -> 
                    "Сильный ветер! Отличное время для ветряной энергетики. Дома используйте естественную вентиляцию вместо кондиционера 🌬️⚡"
                
                windSpeed > 8 && temp in 15..25 -> 
                    "Ветреная погода! Отличное время для прогулки - ветер освежает, не нужен кондиционер 🌬️🚶"
                
                condition.contains("clouds") && temp in 10..22 && windSpeed < 4 -> 
                    "Пасмурная комфортная погода! Идеально для прогулки пешком или на велосипеде вместо поездки на авто 🚶🚴"
                
                condition.contains("clouds") && temp > 20 -> 
                    "Пасмурно, но тепло! Прогулка пешком вместо транспорта - полезно для здоровья и экологии 🚶"
                
                humidity > 80 && temp in 15..25 -> 
                    "Влажная погода! Отличное время для полива растений дождевой водой, если есть возможность собрать её 💧🌱"
                
                pressure < 1000 && condition.contains("rain") -> 
                    "Низкое давление и дождь! Соберите дождевую воду для полива, проверьте утечки в доме 🌧️💧"
                
                pressure > 1020 && condition.contains("clear") && temp in 10..20 -> 
                    "Высокое давление и ясная погода! Идеально для активного отдыха на природе вместо использования транспорта 🌳🚴"
                
                temp in 20..28 && condition.contains("clear") && humidity < 60 -> 
                    "Отличная погода для пикника на природе! Используйте многоразовую посуду вместо одноразовой 🌳♻️"
                
                temp < 10 && condition.contains("snow") -> 
                    "Снежная погода! Используйте общественный транспорт или идите пешком - это безопаснее и экологичнее ❄️🚶"
                
                condition.contains("thunderstorm") -> 
                    "Гроза! Отличное время проверить энергопотребление дома - выключайте неиспользуемые приборы ⚡💡"
                
                temp in 12..18 && condition.contains("clear") && windSpeed < 3 -> 
                    "Идеальная погода для работы в саду или огороде! Компостируйте органические отходы 🌱♻️"
                
                humidity < 40 && temp > 20 -> 
                    "Сухая теплая погода! Поливайте растения утром или вечером, чтобы вода не испарялась быстро 💧🌱"
                
                temp in 5..15 && condition.contains("clouds") -> 
                    "Прохладная пасмурная погода! Отличное время для прогулки - не жарко, не холодно, идеально для пеших прогулок 🚶"
                
                else -> 
                    "Сегодня хорошая погода для прогулки на природе! Насладитесь свежим воздухом, это полезно для здоровья и экологии 🌳"
            }
            
            findViewById<TextView>(R.id.tvEcoAdvice).text = ecoAdvice

            Log.d(TAG, "Советы сгенерированы успешно")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка генерации советов: ${e.message}", e)
            findViewById<TextView>(R.id.tvClothingAdvice)?.text = "Оденьтесь по погоде! 👔"
            findViewById<TextView>(R.id.tvEcoAdvice)?.text = "Берегите природу! ♻️"
        }
    }

    private fun setupHourlyForecast(weather: WeatherResponse) {
        val layout = findViewById<LinearLayout>(R.id.layoutHourlyForecast)
        layout.removeAllViews()
        val currentIcon = "⛅" // временно фиксированная иконка

        // Создаем демо-данные для почасового прогноза на основе текущей погоды
        val baseTemp = weather.main.temp.toInt()
        val hours = listOf( // строчная "l" вместо "L"
            ForecastHour("Сейчас", baseTemp, currentIcon),
            ForecastHour("1 ч", baseTemp + 1, currentIcon),
            ForecastHour("2 ч", baseTemp, currentIcon),
            ForecastHour("3 ч", baseTemp - 1, "🌙"),        
            ForecastHour("4 ч", baseTemp - 2, "🌙"),
            ForecastHour("5 ч", baseTemp - 3, "🌙")
        )

        hours.forEach { forecast ->
            val hourView = LayoutInflater.from(this).inflate(
                R.layout.item_hourly_forecast,
                layout,
                false
            )

            val temp = if (useFahrenheit) celsiusToFahrenheit(forecast.temperature.toDouble()) else forecast.temperature.toDouble()
            val unit = if (useFahrenheit) "°F" else "°C"

            hourView.findViewById<TextView>(R.id.tvTime).text = forecast.time
            hourView.findViewById<TextView>(R.id.tvHourlyTemp).text = "${temp.toInt()}$unit"
            hourView.findViewById<TextView>(R.id.tvWeatherIcon).text = forecast.icon

            layout.addView(hourView)
        }

        Log.d(TAG, "Почасовой прогноз обновлен")
    }

    private fun setupDemoHourlyForecast() {
        val layout = findViewById<LinearLayout>(R.id.layoutHourlyForecast)
        layout.removeAllViews()

        val demoHours = listOf(
            ForecastHour("Сейчас", 21, "⛅"),
            ForecastHour("1 ч", 22, "⛅"),
            ForecastHour("2 ч", 20, "🌤️"),
            ForecastHour("3 ч", 19, "🌙"),
            ForecastHour("4 ч", 18, "🌙"),
            ForecastHour("5 ч", 17, "🌙")
        )

        demoHours.forEach { forecast ->
            val hourView = LayoutInflater.from(this).inflate(
                R.layout.item_hourly_forecast,
                layout,
                false
            )

            val temp = if (useFahrenheit) celsiusToFahrenheit(forecast.temperature.toDouble()) else forecast.temperature.toDouble()
            val unit = if (useFahrenheit) "°F" else "°C"

            hourView.findViewById<TextView>(R.id.tvTime).text = forecast.time
            hourView.findViewById<TextView>(R.id.tvHourlyTemp).text = "${temp.toInt()}$unit"
            hourView.findViewById<TextView>(R.id.tvWeatherIcon).text = forecast.icon

            layout.addView(hourView)
        }
    }

    private fun setupRealHourlyForecast() {
        val layout = findViewById<LinearLayout>(R.id.layoutHourlyForecast)
        layout.removeAllViews()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val useCurrentLocation = Prefs.getUseCurrentLocation(this@MainActivity)
                val httpResponse: retrofit2.Response<com.example.weathermood.data.ForecastResponse>
                
                if (useCurrentLocation && locationHelper.hasLocationPermission()) {
                    val locationData = locationHelper.getCurrentLocationOnce()
                    if (locationData != null) {
                        httpResponse = kotlinx.coroutines.withTimeout(10000) {
                            weatherApi.getHourlyForecastByCoordinates(
                                lat = locationData.latitude,
                                lon = locationData.longitude,
                                apiKey = Constants.API_KEY
                            )
                        }
                    } else {
                        val selectedCity = Prefs.getSelectedCity(this@MainActivity)
                        httpResponse = kotlinx.coroutines.withTimeout(10000) {
                            weatherApi.getHourlyForecast(
                                city = selectedCity,
                                apiKey = Constants.API_KEY
                            )
                        }
                    }
                } else {
                    val selectedCity = Prefs.getSelectedCity(this@MainActivity)
                    httpResponse = kotlinx.coroutines.withTimeout(10000) {
                        weatherApi.getHourlyForecast(
                            city = selectedCity,
                            apiKey = Constants.API_KEY
                        )
                    }
                }
                Log.d(TAG, "HTTP code forecast: ${'$'}{httpResponse.code()} success=${'$'}{httpResponse.isSuccessful}")
                if (!httpResponse.isSuccessful) {
                    val code = httpResponse.code()
                    val body = httpResponse.errorBody()?.string()
                    Log.e(TAG, "HTTP ${'$'}code при getHourlyForecast, errorBody=${'$'}body")
                    throw RuntimeException("Forecast HTTP ${'$'}code")
                }
                val response = httpResponse.body() ?: throw RuntimeException("Пустой ответ forecast")

                // Фильтруем только будущие часы
                val now = System.currentTimeMillis() / 1000
                Log.d(TAG, "Текущее время (unix): $now")
                
                val forecastItems = response.list
                    .also { list -> 
                        Log.d(TAG, "Всего прогнозов получено: ${list.size}")
                        list.take(3).forEach { 
                            Log.d(TAG, "Прогноз: dt=${it.dt}, now=$now, разница=${it.dt - now} сек")
                        }
                    }
                    .filter { 
                        val isFuture = it.dt > now
                        Log.d(TAG, "Прогноз dt=${it.dt}, будущее=$isFuture")
                        isFuture
                    }
                    .take(6) // Берем первые 6 будущих часов

                Log.d(TAG, "Отфильтрованных прогнозов: ${forecastItems.size}")

                withContext(Dispatchers.Main) {
                    forecastItems.forEachIndexed { index, forecast ->
                        val hourView = LayoutInflater.from(this@MainActivity).inflate( // убрали context.
                            R.layout.item_hourly_forecast,
                            layout,
                            false // добавили запятую
                        )

                        // Первый элемент показываем как "Сейчас", остальные - время
                        val time = if (index == 0) "Сейчас" else formatTime(forecast.dt)
                        val tempCelsius = forecast.main.temp
                        val temp = if (useFahrenheit) celsiusToFahrenheit(tempCelsius) else tempCelsius
                        val unit = if (useFahrenheit) "°F" else "°C"
                        val icon = getWeatherIcon(forecast.weather.first().icon)

                        hourView.findViewById<TextView>(R.id.tvTime).text = time
                        hourView.findViewById<TextView>(R.id.tvHourlyTemp).text = "${temp.toInt()}$unit"
                        hourView.findViewById<TextView>(R.id.tvWeatherIcon).text = icon

                        layout.addView(hourView) // строчная V
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // На случай ошибки покажите демо-данные
                withContext(Dispatchers.Main) {
                    setupDemoHourlyForecast() // ваш существующий метод
                }
            }
        }
    }

    private fun formatTime(timestamp: Long): String {
        val date = Date(timestamp * 1000)
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        return format.format(date)
    }

    private fun getWeatherIcon(iconCode: String): String {
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


    private fun tryLoadFromCache() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val selectedCity = Prefs.getSelectedCity(this@MainActivity, Constants.DEFAULT_CITY)
                val cachedWeather = database.weatherCacheDao().get(selectedCity)
                
                if (cachedWeather != null) {
                    Log.d(TAG, "📦 Загружаем данные из кэша")
                    
                    // Проверяем, не устарел ли кэш (более 1 часа)
                    val cacheAge = System.currentTimeMillis() - cachedWeather.timestamp
                    val cacheAgeMinutes = cacheAge / (1000 * 60)
                    
                    withContext(Dispatchers.Main) {
                        findViewById<TextView>(R.id.tvCity).text = "${cachedWeather.cityName} (кэш)"
                        
                        val temp = if (useFahrenheit) celsiusToFahrenheit(cachedWeather.temperature) else cachedWeather.temperature
                        val feelsLike = if (useFahrenheit) celsiusToFahrenheit(cachedWeather.feelsLike) else cachedWeather.feelsLike
                        val unit = if (useFahrenheit) "°F" else "°C"
                        
                        findViewById<TextView>(R.id.tvTemperature).text = "${temp.toInt()}$unit"
                        findViewById<TextView>(R.id.tvFeelsLike).text = "Ощущается как ${feelsLike.toInt()}$unit"
                        findViewById<TextView>(R.id.tvWeatherCondition).text = cachedWeather.weatherCondition
                        findViewById<TextView>(R.id.tvHumidity).text = "Влажность: ${cachedWeather.humidity}%"
                        
                        val windSpeed = if (useMph) {
                            val mph = cachedWeather.windSpeed * 2.23694
                            String.format("%.1f", mph) + " миль/ч"
                        } else {
                            String.format("%.1f", cachedWeather.windSpeed) + " м/с"
                        }
                        findViewById<TextView>(R.id.tvWind).text = "Ветер: $windSpeed"
                        
                        findViewById<TextView>(R.id.tvPrecipitation).text = "${cachedWeather.pressure} hPa"
                        
                        Toast.makeText(
                            this@MainActivity,
                            "Показаны данные из кэша ($cacheAgeMinutes мин назад)",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Log.d(TAG, "❌ Кэш пуст")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "Нет сохраненных данных. Проверьте подключение к интернету",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки из кэша: ${e.message}", e)
            }
        }
    }



    override fun onResume() {
        super.onResume()
        
        // Применяем тему (на случай если изменилась в настройках)
        applyTheme()
        
        // Перезагружаем настройки при возврате (на случай если изменились в ProfileActivity)
        val oldFahrenheit = useFahrenheit
        val oldMph = useMph
        useFahrenheit = Prefs.getUseFahrenheit(this)
        useMph = Prefs.getUseMph(this)
        
        // Если настройки изменились, обновляем UI
        if (oldFahrenheit != useFahrenheit || oldMph != useMph) {
            currentWeather?.let { updateUIWithWeatherData(it) }
            // Обновляем почасовой прогноз с новыми единицами измерения
            setupRealHourlyForecast()
        }
        
        // Обновляем список городов при возврате на экран
        val navView = findViewById<NavigationView>(R.id.nav_view)
        loadUserCitiesIntoMenu(navView)
        
        // Обновляем информацию о пользователе
        val headerView = navView.getHeaderView(0)
        val tvUserName = headerView.findViewById<TextView>(R.id.tvUserName)
        val tvUserEmail = headerView.findViewById<TextView>(R.id.tvUserEmail)
        val tvAuthButton = headerView.findViewById<TextView>(R.id.tvAuthButton)
        updateUserUI(tvUserName, tvUserEmail, tvAuthButton)
    }

    private fun saveMoodRating(rating: Float) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Получаем текущего пользователя
                val currentUser = userManager.getCurrentUser()
                val userId = currentUser?.userId ?: "anonymous"
                
                // Получаем текущую погоду и город
                val currentCity = Prefs.getSelectedCity(this@MainActivity)
                val weather = currentWeather
                
                val weatherCondition = weather?.weather?.firstOrNull()?.main
                val weatherDescription = weather?.weather?.firstOrNull()?.description
                
                val moodRating = MoodRatingEntity(
                    id = 0,
                    userId = userId,
                    rating = rating.toInt(),
                    weatherCondition = weatherCondition,
                    weatherDescription = weatherDescription,
                    temperature = weather?.main?.temp,
                    feelsLike = weather?.main?.feelsLike,
                    humidity = weather?.main?.humidity,
                    pressure = weather?.main?.pressure,
                    windSpeed = weather?.wind?.speed,
                    note = null,
                    cityId = currentCity,
                    cityName = weather?.cityName
                )
                database.moodRatingDao().insert(moodRating)
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Настроение сохранено!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Ошибка сохранения: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_mood_history -> {
                startActivity(Intent(this, com.example.weathermood.ui.MoodHistoryActivity::class.java))
            }
            R.id.nav_settings -> {
                startActivity(Intent(this, com.example.weathermood.ui.ProfileActivity::class.java))
            }
            R.id.nav_share -> {
                Toast.makeText(this, "Поделиться погодой", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_rate -> {
                Toast.makeText(this, "Оценить приложение", Toast.LENGTH_SHORT).show()
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
    
    private fun showWeatherDetailsDialog(weather: WeatherResponse) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_weather_details, null)
        
        // Переменная для отслеживания выбранного дня (0 - вчера, 1 - сегодня, 2 - завтра)
        var selectedDay = 1
        
        // Ссылки на кнопки
        val btnYesterday = dialogView.findViewById<TextView>(R.id.btnYesterday)
        val btnToday = dialogView.findViewById<TextView>(R.id.btnToday)
        val btnTomorrow = dialogView.findViewById<TextView>(R.id.btnTomorrow)
        
        // Функция для обновления данных в зависимости от выбранного дня
        fun updateDialogData(dayOffset: Int) {
            selectedDay = dayOffset
            
            // Обновляем стиль кнопок
            btnYesterday.alpha = if (dayOffset == 0) 1.0f else 0.6f
            btnYesterday.setTextColor(if (dayOffset == 0) 0xFFFFFFFF.toInt() else 0xFFFFFFFF.toInt())
            btnYesterday.setBackgroundColor(if (dayOffset == 0) 0x40FFFFFF.toInt() else 0x00000000.toInt())
            btnYesterday.setTypeface(null, if (dayOffset == 0) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            
            btnToday.alpha = if (dayOffset == 1) 1.0f else 0.6f
            btnToday.setTextColor(if (dayOffset == 1) 0xFFFFFFFF.toInt() else 0xFFFFFFFF.toInt())
            btnToday.setBackgroundColor(if (dayOffset == 1) 0x40FFFFFF.toInt() else 0x00000000.toInt())
            btnToday.setTypeface(null, if (dayOffset == 1) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            
            btnTomorrow.alpha = if (dayOffset == 2) 1.0f else 0.6f
            btnTomorrow.setTextColor(if (dayOffset == 2) 0xFFFFFFFF.toInt() else 0xFFFFFFFF.toInt())
            btnTomorrow.setBackgroundColor(if (dayOffset == 2) 0x40FFFFFF.toInt() else 0x00000000.toInt())
            btnTomorrow.setTypeface(null, if (dayOffset == 2) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            
            // Вычисляем дату для выбранного дня
            val calendar = java.util.Calendar.getInstance()
            calendar.add(java.util.Calendar.DAY_OF_YEAR, dayOffset - 1) // -1 для вчера, 0 для сегодня, +1 для завтра
            
            // Обновляем дату
            val dateFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("ru"))
            dialogView.findViewById<TextView>(R.id.dialogDate).text = dateFormat.format(calendar.time)
            
            // Погодные условия и иконка
            val weatherCondition = weather.weather.firstOrNull()
            val conditionText: String
            val weatherIcon: String
            
            if (weatherCondition != null) {
                when(weatherCondition.main.lowercase()) {
                    "clear" -> {
                        conditionText = "Ясно"
                        weatherIcon = "☀️"
                    }
                    "clouds" -> {
                        conditionText = "Переменная облачность"
                        weatherIcon = "⛅"
                    }
                    "rain" -> {
                        conditionText = "Дождь"
                        weatherIcon = "🌧️"
                    }
                    "snow" -> {
                        conditionText = "Снег"
                        weatherIcon = "❄️"
                    }
                    "thunderstorm" -> {
                        conditionText = "Гроза"
                        weatherIcon = "⛈️"
                    }
                    "drizzle" -> {
                        conditionText = "Морось"
                        weatherIcon = "🌦️"
                    }
                    else -> {
                        conditionText = weatherCondition.description ?: "Неизвестно"
                        weatherIcon = "🌤️"
                    }
                }
            } else {
                conditionText = "Неизвестно"
                weatherIcon = "🌤️"
            }
            
            // Устанавливаем текст погоды
            dialogView.findViewById<TextView>(R.id.dialogWeatherCondition).text = conditionText.replaceFirstChar { 
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() 
            }
            
            // Устанавливаем иконку погоды
            dialogView.findViewById<TextView>(R.id.dialogWeatherIcon).text = weatherIcon
            
            // Температура (с небольшим изменением для разных дней для демонстрации)
            val tempOffset = when(dayOffset) {
                0 -> -2.0 // Вчера было на 2 градуса холоднее
                2 -> 1.0  // Завтра будет на 1 градус теплее
                else -> 0.0
            }
            val adjustedTemp = weather.main.temp + tempOffset
            val temp = if (useFahrenheit) celsiusToFahrenheit(adjustedTemp) else adjustedTemp
            val unit = if (useFahrenheit) "°F" else "°C"
            dialogView.findViewById<TextView>(R.id.dialogTempCelsius).text = "${temp.toInt()}$unit"
            
            // Обновляем текст единицы измерения
            val tempUnitLabel = dialogView.findViewById<TextView>(R.id.dialogTempUnitLabel)
            if (tempUnitLabel != null) {
                tempUnitLabel.text = if (useFahrenheit) "Фаренгейта" else "Цельсия"
            }
            
            // Скорость ветра (с учетом настроек mph/m/s)
            if (useMph) {
                val mph = weather.wind.speed * 2.23694
                dialogView.findViewById<TextView>(R.id.dialogWindSpeed).text = "${String.format("%.1f", mph)} миль/ч"
            } else {
                dialogView.findViewById<TextView>(R.id.dialogWindSpeed).text = "${String.format("%.1f", weather.wind.speed)} м/с"
            }
            
            // УФ индекс (заглушка)
            dialogView.findViewById<TextView>(R.id.dialogUvIndex).text = "0.2"
            
            // Влажность (используем как вероятность осадков)
            dialogView.findViewById<TextView>(R.id.dialogPrecipitation).text = "${weather.main.humidity}%"
        }
        
        // Устанавливаем обработчики кликов для кнопок
        btnYesterday.setOnClickListener { updateDialogData(0) }
        btnToday.setOnClickListener { updateDialogData(1) }
        btnTomorrow.setOnClickListener { updateDialogData(2) }
        
        // Инициализируем диалог с данными для "Сегодня"
        updateDialogData(1)
        
        // Создаем и показываем диалог
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .show()
    }

    data class ForecastHour(val time: String, val temperature: Int, val icon: String)
}
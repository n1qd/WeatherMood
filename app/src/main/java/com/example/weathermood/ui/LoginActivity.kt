package com.example.weathermood.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.weathermood.MainActivity
import com.example.weathermood.data.Prefs
import com.weatherapp.R
import com.example.weathermood.auth.AuthService
import com.example.weathermood.auth.AuthUser
import com.example.weathermood.auth.FakeAuthService
import com.example.weathermood.auth.FirebaseAuthService
import com.example.weathermood.auth.UserManager
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    // Пытаемся использовать Firebase, но при ошибках будет fallback на FakeAuthService
    private lateinit var auth: AuthService
    private val firebaseAuth: AuthService by lazy { FirebaseAuthService(this) }
    private val fakeAuth: AuthService by lazy { FakeAuthService() }
    private var useFirebase = true
    private lateinit var userManager: UserManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        
        userManager = UserManager(this)
        
        // Инициализируем auth сервис (сначала пробуем Firebase)
        auth = firebaseAuth
        
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val btnAnon = findViewById<Button>(R.id.btnAnon)
        
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            lifecycleScope.launch {
                        try {
                            val user = auth.signIn(email, password)
                            if (user is AuthUser.LoggedIn) {
                                // Сохраняем пользователя в базу данных
                                userManager.saveUser(user)
                                
                                // Загружаем данные из Firestore (без блокировки при ошибках)
                                if (!user.isAnonymous) {
                                    try {
                                        Toast.makeText(this@LoginActivity, "Синхронизация данных...", Toast.LENGTH_SHORT).show()
                                        userManager.loadFromFirestore()
                                    } catch (e: Exception) {
                                        android.util.Log.e("LoginActivity", "Ошибка загрузки из Firestore: ${e.message}", e)
                                        // Продолжаем работу даже при ошибке синхронизации
                                    }
                                }
                                
                                Toast.makeText(this@LoginActivity, "Добро пожаловать!", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                                finish()
                            } else {
                                Toast.makeText(this@LoginActivity, "Неверный email или пароль", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(this@LoginActivity, "Ошибка входа: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
            }
        }
        
        btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        
        btnAnon.setOnClickListener {
            lifecycleScope.launch {
                try {
                    // Пытаемся войти через текущий auth сервис
                    val user = auth.signInAnonymously()
                    if (user is AuthUser.LoggedIn) {
                        userManager.saveUser(user)
                        val authType = if (useFirebase) "Firebase" else "локально"
                        Toast.makeText(this@LoginActivity, "Вход анонимно ($authType)", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    }
                } catch (e: Exception) {
                    // Если Firebase не работает, переключаемся на FakeAuthService
                    android.util.Log.e("LoginActivity", "Ошибка Firebase аутентификации, переключаюсь на локальный режим", e)
                    
                    if (useFirebase) {
                        // Переключаемся на локальную аутентификацию
                        useFirebase = false
                        auth = fakeAuth
                        
                        // Показываем понятное сообщение
                        Toast.makeText(
                            this@LoginActivity,
                            "⚠️ Firebase недоступен. Используется локальный режим.\nДанные не будут синхронизироваться между устройствами.",
                            Toast.LENGTH_LONG
                        ).show()
                        
                        // Пробуем еще раз с локальным auth
                        try {
                            val user = auth.signInAnonymously()
                            if (user is AuthUser.LoggedIn) {
                                userManager.saveUser(user)
                                startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                                finish()
                            }
                        } catch (e2: Exception) {
                            Toast.makeText(this@LoginActivity, "Ошибка входа: ${e2.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@LoginActivity, "Ошибка входа: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}

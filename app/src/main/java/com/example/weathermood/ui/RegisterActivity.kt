package com.example.weathermood.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.weathermood.MainActivity
import com.weatherapp.R
import com.example.weathermood.auth.AuthService
import com.example.weathermood.auth.AuthUser
import com.example.weathermood.auth.FakeAuthService
import com.example.weathermood.auth.FirebaseAuthService
import com.example.weathermood.auth.UserManager
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {
    private val auth: AuthService = FirebaseAuthService(this)
    private lateinit var userManager: UserManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        
        userManager = UserManager(this)
        
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etConfirmPassword = findViewById<EditText>(R.id.etConfirmPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val btnBack = findViewById<Button>(R.id.btnBack)
        
        btnRegister.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()
            
            if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (password != confirmPassword) {
                Toast.makeText(this, "Пароли не совпадают", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (password.length < 6) {
                Toast.makeText(this, "Пароль должен содержать минимум 6 символов", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            lifecycleScope.launch {
                try {
                    val user = auth.register(email, password)
                    if (user is AuthUser.LoggedIn) {
                        // Сохраняем нового пользователя в базу данных
                        userManager.saveUser(user)
                        Toast.makeText(this@RegisterActivity, "Регистрация успешна! Добро пожаловать!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                        finish()
                    } else {
                        // Регистрация не удалась - возможно email уже существует
                        Toast.makeText(this@RegisterActivity, "Этот email уже зарегистрирован или данные некорректны", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@RegisterActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        btnBack.setOnClickListener {
            finish()
        }
    }
}

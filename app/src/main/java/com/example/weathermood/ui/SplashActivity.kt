package com.example.weathermood.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.weathermood.MainActivity
import com.weatherapp.R
import com.example.weathermood.auth.AuthService
import com.example.weathermood.auth.FakeAuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : AppCompatActivity() {
    private val auth: AuthService = FakeAuthService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        lifecycleScope.launch(Dispatchers.IO) {
            val user = auth.currentUser()
            val target = if (user != null) {
                Intent(this@SplashActivity, MainActivity::class.java)
            } else {
                Intent(this@SplashActivity, LoginActivity::class.java)
            }
            withContext(Dispatchers.Main) {
                startActivity(target)
                finish()
            }
        }
    }
}




package com.example.weathermood.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.weathermood.MainActivity
import com.weatherapp.R
import com.example.weathermood.auth.AuthService
import com.example.weathermood.auth.FakeAuthService
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {
    private val auth: AuthService = FakeAuthService()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        findViewById<Button>(R.id.btnAnon).setOnClickListener {
            lifecycleScope.launch { 
                auth.signInAnonymously()
                startActivity(Intent(this@AuthActivity, MainActivity::class.java))
                finish() 
            }
        }
    }
}




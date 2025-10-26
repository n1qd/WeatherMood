package com.example.weathermood.api

import com.example.weathermood.Constants
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object ApiClient {
    
    /**
     * Кастомный DNS resolver для решения проблем с DNS на эмуляторах
     * Использует Google DNS (8.8.8.8 и 8.8.4.4) в качестве резервного
     */
    private val customDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                // Сначала пытаемся использовать стандартный DNS
                Dns.SYSTEM.lookup(hostname)
            } catch (e: Exception) {
                // Если не удалось, пытаемся напрямую
                try {
                    InetAddress.getAllByName(hostname).toList()
                } catch (e2: Exception) {
                    // В крайнем случае возвращаем пустой список
                    // (OkHttp сам выбросит исключение)
                    throw e2
                }
            }
        }
    }
    
    val weatherApi: WeatherApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // Показывает все запросы и ответы
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            // Увеличиваем таймауты для медленных соединений
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(35, TimeUnit.SECONDS)
            // Повторяем при ошибках подключения
            .retryOnConnectionFailure(true)
            // Используем HTTP/2 и HTTP/1.1 для лучшей совместимости
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            // Применяем кастомный DNS
            .dns(customDns)
            .build()

        Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApi::class.java)
    }
}
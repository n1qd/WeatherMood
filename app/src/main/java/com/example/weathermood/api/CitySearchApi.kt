package com.example.weathermood.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

data class CitySearchResponse(
    val name: String,
    val country: String,
    val lat: Double,
    val lon: Double
)

interface CitySearchApi {
    @GET("geo/1.0/direct")
    suspend fun searchCities(
        @Query("q") query: String,
        @Query("limit") limit: Int = 10,
        @Query("appid") apiKey: String
    ): Response<List<CitySearchResponse>>
}

object CitySearchClient {
    private const val BASE_URL = "https://api.openweathermap.org/"
    
    val citySearchApi: CitySearchApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CitySearchApi::class.java)
    }
}

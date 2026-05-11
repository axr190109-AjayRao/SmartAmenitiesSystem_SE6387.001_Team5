package com.smartamenities.app.api

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "http://10.0.2.2:8081/"

    val apiService: SmartAmenitiesApiService by lazy {
        val okHttpClient = OkHttpClient.Builder()
            .connectionPool(ConnectionPool(5, 10, TimeUnit.SECONDS))
            .retryOnConnectionFailure(true)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SmartAmenitiesApiService::class.java)
    }
}

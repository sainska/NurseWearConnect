package com.example.nursewearconnect.data.api

import com.example.nursewearconnect.data.api.interceptors.AuthInterceptor
import com.example.nursewearconnect.data.security.SecurityManager
import com.example.nursewearconnect.utils.Constants
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = Constants.SUPABASE_URL
    private const val SUPABASE_KEY = Constants.SUPABASE_ANON_KEY

    private var apiService: ApiService? = null

    fun getApiService(securityManager: SecurityManager): ApiService {
        return apiService ?: synchronized(this) {
            val interceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(interceptor)
                .addInterceptor(AuthInterceptor(securityManager, SUPABASE_KEY))
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            retrofit.create(ApiService::class.java).also { apiService = it }
        }
    }
}

package com.example.nursewearconnect.di

import android.content.Context
import androidx.room.Room
import com.example.nursewearconnect.data.api.ApiService
import com.example.nursewearconnect.data.local.AppDatabase
import com.example.nursewearconnect.data.repository.*
import com.example.nursewearconnect.data.security.SecurityManager
import com.example.nursewearconnect.utils.Constants
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.annotations.SupabaseInternal
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.ANDROID
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AppContainer(private val context: Context) {

    @OptIn(SupabaseInternal::class)
    val supabaseClient: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = Constants.SUPABASE_URL,
            supabaseKey = Constants.SUPABASE_ANON_KEY
        ) {
            install(Postgrest)
            install(Auth)
            install(Realtime)
            install(Storage)
            httpConfig {
                install(Logging) {
                    level = LogLevel.ALL
                    logger = Logger.ANDROID
                }
            }
        }
    }

    private val database: AppDatabase by lazy {
        Room.databaseBuilder(context, AppDatabase::class.java, "nursewear_db")
            .fallbackToDestructiveMigration()
            .build()
    }

    private val apiService: ApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val token = securityManager.getToken() ?: Constants.SUPABASE_ANON_KEY
                val request = chain.request().newBuilder()
                    .addHeader("apikey", Constants.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                chain.proceed(request)
            }
            .authenticator(com.example.nursewearconnect.data.api.interceptors.TokenAuthenticator(supabaseClient, securityManager))
            .build()

        Retrofit.Builder()
            .baseUrl(Constants.SUPABASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    private val meilisearchService: com.example.nursewearconnect.data.api.MeilisearchService by lazy {
        val client = OkHttpClient.Builder().build()
        Retrofit.Builder()
            .baseUrl("https://meilisearch.nursewearconnect.com/") // Item 46 Endpoint
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.example.nursewearconnect.data.api.MeilisearchService::class.java)
    }

    val securityManager: SecurityManager by lazy { SecurityManager(context) }

    val syncManager: com.example.nursewearconnect.data.sync.SyncManager by lazy {
        com.example.nursewearconnect.data.sync.SyncManager(context, database.syncActionDao())
    }

    val userRepository: UserRepository by lazy { UserRepository(apiService, securityManager, supabaseClient) }
    val productRepository: ProductRepository by lazy { 
        ProductRepository(apiService, database.productDao(), database.categoryDao(), database, supabaseClient)
    }
    val cartRepository: CartRepository by lazy { 
        CartRepository(securityManager, database.cartDao(), database.syncActionDao(), apiService, productRepository)
    }
    val orderRepository: OrderRepository by lazy { OrderRepository(apiService, supabaseClient) }
    val paymentRepository: PaymentRepository by lazy { PaymentRepository(apiService) }
    val adminRepository: AdminRepository by lazy { AdminRepository(apiService, supabaseClient) }
    val vendorRepository: VendorRepository by lazy { VendorRepository(apiService, adminRepository, supabaseClient) }
    val authRepository: AuthRepository by lazy { AuthRepository(supabaseClient, securityManager, apiService) }
}

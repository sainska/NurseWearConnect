package com.example.nursewearconnect.data.api.interceptors

import com.example.nursewearconnect.data.security.SecurityManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val securityManager: SecurityManager,
    private val supabaseKey: String
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = securityManager.getToken()

        val requestBuilder = originalRequest.newBuilder()
            .header("apikey", supabaseKey)

        if (!token.isNullOrEmpty()) {
            requestBuilder.header("Authorization", "Bearer $token")
        } else {
            // Log fallback only if not a public endpoint (optional but helpful)
            android.util.Log.w("AuthInterceptor", "No JWT found, falling back to anon key for: ${originalRequest.url}")
            requestBuilder.header("Authorization", "Bearer $supabaseKey")
        }

        val response = chain.proceed(requestBuilder.build())
        
        if (!response.isSuccessful) {
            val code = response.code
            val url = originalRequest.url
            val errorBody = response.peekBody(1024 * 1024).string()
            android.util.Log.e("AuthInterceptor", "HTTP $code Error for $url: $errorBody")
        }
        
        return response
    }
}

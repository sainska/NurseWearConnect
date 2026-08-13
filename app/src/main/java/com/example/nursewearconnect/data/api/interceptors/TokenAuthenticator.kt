package com.example.nursewearconnect.data.api.interceptors

import com.example.nursewearconnect.data.security.SecurityManager
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * An [Authenticator] that handles 401 Unauthorized responses by attempting to refresh
 * the Supabase session and retrying the request with a new JWT.
 */
class TokenAuthenticator(
    private val supabaseClient: SupabaseClient,
    private val securityManager: SecurityManager
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // If we've already tried to authenticate twice, stop to avoid infinite loops
        if (responseCount(response) >= 3) {
            android.util.Log.e("TokenAuthenticator", "Giving up after 3 authentication attempts")
            return null
        }

        synchronized(this) {
            val currentToken = securityManager.getToken()
            val authHeader = response.request.header("Authorization")
            val requestToken = authHeader?.removePrefix("Bearer ")?.trim()

            // If the token in the manager is already different from the one used in the failed request,
            // it means another request already triggered a refresh. Just retry with the new token.
            if (currentToken != null && currentToken != requestToken) {
                android.util.Log.d("TokenAuthenticator", "Token already refreshed by another request, retrying...")
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            // Otherwise, trigger a session refresh
            android.util.Log.d("TokenAuthenticator", "JWT expired (401), attempting session refresh...")
            
            return runBlocking {
                try {
                    supabaseClient.auth.refreshCurrentSession()
                    val newSession = supabaseClient.auth.currentSessionOrNull()
                    val newToken = newSession?.accessToken
                    
                    if (newToken != null) {
                        android.util.Log.d("TokenAuthenticator", "Session refreshed successfully")
                        securityManager.saveToken(newToken)
                        
                        response.request.newBuilder()
                            .header("Authorization", "Bearer $newToken")
                            .build()
                    } else {
                        android.util.Log.e("TokenAuthenticator", "Refresh returned no session")
                        null
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TokenAuthenticator", "Session refresh failed", e)
                    null
                }
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var priorResponse = response.priorResponse
        while (priorResponse != null) {
            result++
            priorResponse = priorResponse.priorResponse
        }
        return result
    }
}

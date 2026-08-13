package com.example.nursewearconnect.data.repository

import com.example.nursewearconnect.data.api.ApiService
import com.example.nursewearconnect.data.security.SecurityManager
import com.example.nursewearconnect.model.ProfileUpdateRequest
import io.github.jan.supabase.SupabaseClient
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.Apple
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking

class AuthRepository(
    private val supabaseClient: SupabaseClient,
    private val securityManager: SecurityManager,
    private val apiService: ApiService
) {
    private val _isLoggedIn = MutableStateFlow(securityManager.getToken() != null)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentUser = MutableStateFlow(supabaseClient.auth.currentSessionOrNull()?.user)
    val currentUser = _currentUser.asStateFlow()

    init {
        observeSession()
    }

    private fun observeSession() {
        supabaseClient.auth.sessionStatus
            .onEach { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val token = status.session.accessToken
                        val userId = status.session.user?.id
                        _currentUser.value = status.session.user
                        securityManager.saveToken(token)
                        userId?.let { securityManager.saveUserId(it) }
                        _isLoggedIn.value = true
                    }
                    is SessionStatus.NotAuthenticated -> {
                        _currentUser.value = null
                        if (_isLoggedIn.value) {
                            _isLoggedIn.value = false
                        }
                    }
                    else -> {}
                }
            }
            .launchIn(CoroutineScope(Dispatchers.Main))
    }

    suspend fun resendVerificationEmail(email: String, type: OtpType.Email = OtpType.Email.SIGNUP): Result<Unit> {
        return try {
            supabaseClient.auth.resendEmail(type, email)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshSession(): Result<Unit> {
        return try {
            supabaseClient.auth.refreshCurrentSession()
            val session = supabaseClient.auth.currentSessionOrNull()
            if (session != null) {
                securityManager.saveToken(session.accessToken)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to refresh session"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginWithGoogle(): Result<Unit> {
        return try {
            supabaseClient.auth.signInWith(Google)
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Google login failed: ${e.message}")
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun loginWithApple(): Result<Unit> {
        return try {
            supabaseClient.auth.signInWith(Apple)
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Apple login failed: ${e.message}")
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            android.util.Log.d("AuthRepository", "Starting login flow for: $email")
            
            // Attempt Supabase Auth Sign In
            try {
                supabaseClient.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                android.util.Log.d("AuthRepository", "signInWith finished")
            } catch (e: Exception) {
                android.util.Log.e("AuthRepository", "Supabase signInWith failed: ${e.message}")
                throw e // Re-throw to be caught by the outer catch
            }
            
            val session = supabaseClient.auth.currentSessionOrNull()
            if (session == null) {
                android.util.Log.e("AuthRepository", "Login successful but session is null")
                return Result.failure(Exception("Login failed: Session not established"))
            }

            // --- ENSURE VERIFIED EMAILS ONLY ---
            if (session.user?.emailConfirmedAt == null && session.user?.lastSignInAt == null) {
                android.util.Log.w("AuthRepository", "User email not confirmed: $email")
                // Sign out immediately to prevent unauthorized access
                supabaseClient.auth.signOut()
                securityManager.clearToken()
                _isLoggedIn.value = false
                return Result.failure(Exception("Please verify your email address before logging in. Check your inbox for the confirmation link."))
            }

            val token = session.accessToken
            val userId = session.user?.id
            
            android.util.Log.d("AuthRepository", "Session established. UserId: $userId")

            // 1. Get role from JWT Metadata (Fastest)
            var role = "student"
            try {
                role = session.user?.userMetadata?.get("role")?.toString()?.replace("\"", "")?.lowercase() ?: "student"
                android.util.Log.d("AuthRepository", "Metadata role found: $role")
            } catch (e: Exception) {
                android.util.Log.e("AuthRepository", "Error extracting metadata role: ${e.message}")
            }
            
            if (userId != null) {
                securityManager.saveToken(token)
                securityManager.saveUserId(userId)
                
                // 2. Try to get role from Profile Table (Most Accurate)
                try {
                    android.util.Log.d("AuthRepository", "Fetching profile for userId: $userId")
                    val profiles = apiService.getProfileByUserId("eq.$userId")
                    if (profiles.isNotEmpty()) {
                        val profile = profiles.first()
                        val rawDbRole = profile["role"]?.toString() ?: role
                        val dbRole = rawDbRole.replace("\"", "").replace("'", "").trim().lowercase()
                        
                        val name = profile["full_name"]?.toString()?.replace("\"", "") ?: ""
                        val status = (profile["status"]?.toString() ?: "active").replace("\"", "").lowercase()

                        android.util.Log.d("AuthRepository", "DB Profile found: role=$dbRole, status=$status")

                        if (status == "suspended") {
                            android.util.Log.w("AuthRepository", "User $userId is suspended")
                            securityManager.clearToken()
                            return Result.failure(Exception("Your account has been suspended. Please contact support."))
                        }
                        role = dbRole
                        securityManager.saveUserName(name)
                        securityManager.saveUserRole(dbRole)
                    } else {
                        android.util.Log.w("AuthRepository", "No DB profile found for $userId, using cleaned metadata role: $role")
                        val cleanedRole = role.replace("\"", "").replace("'", "").trim().lowercase()
                        securityManager.saveUserRole(cleanedRole)
                    }
                } catch (e: Exception) {
                    // Fallback to metadata role if DB fails
                    val cleanedRole = role.replace("\"", "").replace("'", "").trim().lowercase()
                    android.util.Log.e("AuthRepository", "Profile fetch failed, using metadata role: $cleanedRole. Error: ${e.message}")
                    securityManager.saveUserRole(cleanedRole)
                }
                
                // Ensure role is saved even if profile fetch was skipped or failed
                if (securityManager.getUserRole() == null) {
                    val finalCleanedRole = role.replace("\"", "").replace("'", "").trim().lowercase()
                    securityManager.saveUserRole(finalCleanedRole)
                }

                // Update FCM Token
                updateFcmToken(userId)
                
                // Track session start
                try {
                    apiService.handleUserLogin(mapOf("target_user_id" to userId))
                } catch (e: Exception) {
                    android.util.Log.e("AuthRepository", "Failed to log session start (non-fatal): ${e.message}")
                }
                
                // Audit Log: Login Success
                try {
                    apiService.logAction(mapOf(
                        "user_id" to userId,
                        "action" to "LOGIN_SUCCESS",
                        "details" to "User logged in successfully via email",
                        "severity" to "info"
                    ))
                } catch (e: Exception) {
                    android.util.Log.e("AuthRepository", "Failed to create audit log (non-fatal)")
                }

                _isLoggedIn.value = true
                android.util.Log.i("AuthRepository", "Login complete for $email as $role")
                Result.success(Unit)
            } else {
                Result.failure(Exception("Login failed: User ID missing from session"))
            }
        } catch (e: Exception) {
            val mappedError = com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)
            android.util.Log.e("AuthRepository", "Login exception for $email: ${e.message}", e)
            android.util.Log.e("AuthRepository", "Mapped error message: $mappedError")
            
            // Audit Log: Login Failure
            try {
                apiService.logAction(mapOf(
                    "user_id" to null,
                    "action" to "LOGIN_FAILURE",
                    "details" to "Login attempt failed for $email: ${e.message}",
                    "severity" to "warning"
                ))
            } catch (ex: Exception) {}
            Result.failure(Exception(mappedError))
        }
    }

    suspend fun checkEmailExists(email: String): Result<Boolean> {
        return try {
            val profiles = apiService.checkEmailExists("eq.$email")
            Result.success(profiles.isNotEmpty())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(
        email: String,
        password: String,
        fullName: String,
        phoneNumber: String,
        role: String,
        businessName: String? = null,
        location: String? = null,
        businessDescription: String? = null,
        institution: String? = null,
        licenseUrl: String? = null,
        referralCode: String? = null
    ): Result<Unit> {
        val normalizedRole = role.lowercase()
        return try {
            android.util.Log.d("AuthRepository", "Starting registration for: $email as $normalizedRole. Referral: $referralCode")
            val metadata = mutableMapOf<String, String>(
                "full_name" to fullName,
                "phone_number" to phoneNumber,
                "role" to normalizedRole,
                "status" to if (normalizedRole == "vendor") "pending" else "active"
            )
            
            institution?.let { metadata["institution"] = it }
            referralCode?.let { metadata["referred_by"] = it }

            if (normalizedRole == "vendor") {
                businessName?.let { metadata["business_name"] = it }
                location?.let { metadata["location"] = it }
                businessDescription?.let { 
                    metadata["business_description"] = it 
                    metadata["bio"] = it
                }
                licenseUrl?.let { 
                    metadata["business_license_url"] = it
                    metadata["document_status"] = "pending"
                }
            }

            supabaseClient.auth.signUpWith(Email) {
                this.email = email
                this.password = password
                data = kotlinx.serialization.json.buildJsonObject {
                    metadata.forEach { (k, v) ->
                        put(k, kotlinx.serialization.json.JsonPrimitive(v))
                    }
                }
            }
            
            val session = supabaseClient.auth.currentSessionOrNull()
            val user = session?.user
            val userId = user?.id
            
            // Audit Log: Registration Attempt
            try {
                apiService.logAction(mapOf(
                    "user_id" to userId,
                    "action" to "USER_REGISTERED",
                    "details" to "New $normalizedRole account created for $email. Status: ${if (normalizedRole == "vendor") "pending_approval" else "awaiting_verification"}",
                    "severity" to "info"
                ))
            } catch (ex: Exception) {}

            if (session?.accessToken != null) {
                securityManager.saveToken(session.accessToken)
                securityManager.saveUserRole(normalizedRole)
                userId?.let { securityManager.saveUserId(it) }
                _isLoggedIn.value = true
                Result.success(Unit)
            } else {
                // Return success but indicate that verification email is sent (handled by UI check for session)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Registration exception: ${e.message}", e)
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    fun logout() {
        try {
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            GlobalScope.launch {
                try {
                    supabaseClient.auth.signOut()
                } catch (e: Exception) {
                    // Ignore
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        val userId = securityManager.getUserId()
        
        // Track session end
        userId?.let {
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            GlobalScope.launch {
                try {
                    apiService.handleUserLogout(mapOf("target_user_id" to it))
                } catch (e: Exception) {
                    android.util.Log.e("AuthRepository", "Failed to log session end", e)
                }
            }
        }

        securityManager.clearToken()
        _isLoggedIn.value = false
        
        // Audit Log: Logout
        userId?.let {
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            GlobalScope.launch {
                try {
                    apiService.logAction(mapOf(
                        "user_id" to it,
                        "action" to "LOGOUT",
                        "details" to "User logged out of the application",
                        "severity" to "info"
                    ))
                } catch (e: Exception) {}
            }
        }
    }

    suspend fun uploadLicense(userId: String, bytes: ByteArray, extension: String): Result<String> {
        return try {
            val bucket = supabaseClient.storage["licenses"]
            val fileName = "license_${java.util.UUID.randomUUID()}.$extension"
            val path = "$userId/$fileName"
            
            bucket.upload(path, bytes) {
                upsert = true
            }
            
            Result.success(bucket.publicUrl(path))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getUserId(): String? = securityManager.getUserId()

    fun getUserRole(): String {
        return securityManager.getUserRole() ?: "student"
    }

    suspend fun refreshUserRole(): String {
        val userId = securityManager.getUserId() ?: return "student"
        return try {
            val profiles = apiService.getProfileByUserId("eq.$userId")
            val role = profiles.firstOrNull()?.get("role")?.toString() ?: "student"
            securityManager.saveUserRole(role)
            role
        } catch (e: Exception) {
            getUserRole()
        }
    }

    suspend fun requestPasswordReset(email: String): Result<Unit> {
        return try {
            // Generate a 4-digit code
            val code = (1000..9999).random().toString()
            
            // Set expiration to 15 minutes from now
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val expiresAt = sdf.format(java.util.Date(System.currentTimeMillis() + 15 * 60 * 1000))

            val data = mapOf(
                "email" to email,
                "code" to code,
                "expires_at" to expiresAt,
                "is_used" to false
            )

            // Insert into the custom password_resets table to trigger the Edge Function
            apiService.createPasswordReset(data)
            
            // Audit Log
            try {
                apiService.logAction(mapOf(
                    "user_id" to null,
                    "action" to "CUSTOM_PASSWORD_RESET_REQUEST",
                    "details" to "Custom 4-digit OTP reset requested for $email",
                    "severity" to "info"
                ))
            } catch (e: Exception) {}
            
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Reset request exception: ${e.message}", e)
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun verifyOtp(email: String, otp: String): Result<Unit> {
        return try {
            // 1. Check our custom table for the valid code
            val resets = apiService.getPasswordReset(email, otp)
            
            if (resets.isEmpty()) {
                return Result.failure(Exception("Invalid or expired code. Please try again."))
            }

            val reset = resets.first()
            val expiresAtStr = reset["expires_at"]?.toString()
            if (expiresAtStr != null) {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val expiresAt = sdf.parse(expiresAtStr.split("+")[0].split(".")[0])?.time ?: 0L
                if (System.currentTimeMillis() > expiresAt) {
                    return Result.failure(Exception("The code has expired. Please request a new one."))
                }
            }

            // 2. We'll mark it as used in updatePassword
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Verify OTP exception: ${e.message}", e)
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun verifyEmailToken(email: String, token: String, type: String): Result<Unit> {
        return try {
            val otpType = when(type.lowercase()) {
                "signup" -> io.github.jan.supabase.auth.OtpType.Email.SIGNUP
                "recovery" -> io.github.jan.supabase.auth.OtpType.Email.RECOVERY
                "invite" -> io.github.jan.supabase.auth.OtpType.Email.INVITE
                "magiclink" -> io.github.jan.supabase.auth.OtpType.Email.MAGIC_LINK
                else -> io.github.jan.supabase.auth.OtpType.Email.SIGNUP
            }
            supabaseClient.auth.verifyEmailOtp(
                type = otpType,
                token = token,
                email = email
            )
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Verify Email Token exception: ${e.message}", e)
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun updatePassword(email: String, otp: String, newPassword: String): Result<Unit> {
        return try {
            // 1. Mark the custom reset record as used
            apiService.markPasswordResetUsed(email, otp)

            // 2. Since this is a custom OTP flow, we'd typically use a Service Role RPC 
            // to update the user's password because they aren't authenticated yet.
            val params = mapOf(
                "target_email" to email,
                "otp_code" to otp,
                "new_password" to newPassword
            )
            apiService.resetPasswordWithOtp(params)
            
            // Audit Log
            try {
                apiService.logAction(mapOf(
                    "user_id" to null,
                    "action" to "PASSWORD_RESET_SUCCESS",
                    "details" to "User $email successfully updated their password via custom OTP",
                    "severity" to "info"
                ))
            } catch (e: Exception) {}
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Password update exception: ${e.message}", e)
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    private fun updateFcmToken(userId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                apiService.updateProfile("eq.$userId", ProfileUpdateRequest(fcmToken = token))
                android.util.Log.d("AuthRepository", "Actual FCM Token updated for user $userId: $token")
            } catch (e: Exception) {
                android.util.Log.e("AuthRepository", "Failed to update actual FCM Token: ${e.message}")
                // Fallback to simulation only if Firebase fails and we are in debug
                if (com.example.nursewearconnect.BuildConfig.DEBUG) {
                    val simulatedToken = "fcm_fallback_${userId.take(8)}"
                    apiService.updateProfile("eq.$userId", ProfileUpdateRequest(fcmToken = simulatedToken))
                }
            }
        }
    }
}

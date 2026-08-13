package com.example.nursewearconnect.data.repository

import com.example.nursewearconnect.data.api.ApiService
import com.example.nursewearconnect.data.security.SecurityManager
import com.example.nursewearconnect.model.ProfileUpdateRequest
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserRepository(
    private val apiService: ApiService,
    private val securityManager: SecurityManager,
    private val supabaseClient: SupabaseClient
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _userProfile = MutableStateFlow<Map<String, Any>?>(null)
    val userProfile: StateFlow<Map<String, Any>?> = _userProfile

    private val TAG = "UserRepository"

    /**
     * Executes a network call safely with standardized logging and error mapping.
     */
    private suspend fun <T> safeCall(action: String, block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: Exception) {
            val error = com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)
            android.util.Log.e(TAG, "Error during $action: $error", e)
            Result.failure(Exception(error))
        }
    }

    fun initFromCache() {
        val cachedName = securityManager.getUserName()
        val cachedRole = securityManager.getUserRole()
        if (cachedName != null || cachedRole != null) {
            val profile = mutableMapOf<String, Any>()
            if (cachedName != null) profile["full_name"] = cachedName
            if (cachedRole != null) profile["role"] = cachedRole
            _userProfile.value = profile
        }
    }

    init {
        initFromCache()
    }

    suspend fun fetchProfile(userId: String): Result<Map<String, Any>?> =
        safeCall("fetch profile") {
            val profiles = apiService.getProfileByUserId("eq.$userId")
            if (profiles.isEmpty()) {
                throw Exception("Profile record not found for user: $userId")
            }
            
            val profile = profiles.first().toMutableMap()
            
            // Ensure email is present
            if (profile["email"] == null) {
                profile["email"] = supabaseClient.auth.currentUserOrNull()?.email ?: ""
            }
            
            val normalizedRole = (profile["role"]?.toString() ?: securityManager.getUserRole() ?: "student").lowercase()
            val normalizedName = profile["full_name"]?.toString() ?: securityManager.getUserName() ?: ""

            profile["role"] = normalizedRole
            profile["full_name"] = normalizedName

            _userProfile.value = profile
            
            securityManager.saveUserRole(normalizedRole)
            securityManager.saveUserName(normalizedName)
            
            profile
        }

    suspend fun updateProfile(userId: String, data: ProfileUpdateRequest): Result<Unit> =
        safeCall("update profile") {
            apiService.updateProfile("eq.$userId", data)
            fetchProfile(userId)
            
            // Audit Log
            try {
                val updatedFields = mutableListOf<String>()
                if (data.fullName != null) updatedFields.add("full_name")
                if (data.phoneNumber != null) updatedFields.add("phone_number")
                if (data.address != null) updatedFields.add("address")
                if (data.measurements != null) updatedFields.add("measurements")
                if (data.avatarUrl != null) updatedFields.add("avatar_url")

                apiService.logAction(mapOf(
                    "user_id" to userId,
                    "action" to "PROFILE_UPDATE",
                    "details" to "User updated: ${updatedFields.joinToString(", ")}",
                    "severity" to "info"
                ))
            } catch (e: Exception) {}
        }

    suspend fun uploadFile(userId: String, bytes: ByteArray, bucketName: String, extension: String): Result<String> {
        return try {
            val bucket = supabaseClient.storage[bucketName]
            val fileName = "${bucketName}_${java.util.UUID.randomUUID()}.$extension"
            val path = "$userId/$fileName"
            
            // Upload the file to Supabase Storage
            bucket.upload(path, bytes) {
                upsert = true
            }
            
            // Get the public URL
            val publicUrl = bucket.publicUrl(path)
            
            // Only update profile if it's an avatar upload
            if (bucketName == "avatars") {
                updateProfile(userId, ProfileUpdateRequest(avatarUrl = publicUrl))
            }
            
            Result.success(publicUrl)
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun uploadImage(userId: String, bytes: ByteArray, bucketName: String): Result<String> {
        return uploadFile(userId, bytes, bucketName, "jpg")
    }

    suspend fun getAllUsers(): List<Map<String, Any>> {
        return try {
            apiService.getAllProfiles()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getNotifications(userId: String): List<Map<String, Any>> {
        return try {
            apiService.getNotifications("eq.$userId")
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getMessages(userId: String): List<Map<String, Any>> {
        return try {
            val filter = "(sender_id.eq.$userId,receiver_id.eq.$userId)"
            apiService.getMessages(filter)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun sendMessage(messageData: Map<String, Any>): Result<Map<String, Any>> {
        return try {
            val response = apiService.sendMessage(messageData)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    fun getMessagesRealtime(userId: String): Flow<PostgresAction> {
        val channel = supabaseClient.channel("user_messages_$userId")
        return channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "messages"
            filter("sender_id", FilterOperator.EQ, userId) // This is a simplification; Realtime filters have limitations with 'OR'
        }
    }

    fun getProfileRealtime(userId: String): Flow<PostgresAction> {
        val channel = supabaseClient.channel("user_profile_$userId")
        return channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "profiles"
            filter("id", FilterOperator.EQ, userId)
        }
    }

    fun getNotificationsRealtime(userId: String): Flow<PostgresAction> {
        val channel = supabaseClient.channel("user_notifications_$userId")
        return channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "notifications"
            filter("user_id", FilterOperator.EQ, userId)
        }
    }

    fun getUserRole(): String? {
        return securityManager.getUserRole()
    }

    fun getUserName(): String? {
        return securityManager.getUserName()
    }

    fun getUserId(): String? {
        return securityManager.getUserId()
    }

    fun logout() {
        securityManager.clearToken()
        _userProfile.value = null
    }

    suspend fun getActiveSessions(userId: String): List<Map<String, Any>> {
        return try {
            apiService.getActiveSessions("eq.$userId")
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getUserReviews(userId: String): List<Map<String, Any>> {
        return try {
            apiService.getUserReviews("eq.$userId")
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getUserAddresses(userId: String): List<Map<String, Any>> {
        return try {
            apiService.getUserAddresses("eq.$userId")
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addAddress(userId: String, addressData: Map<String, Any>): Result<Map<String, Any>> {
        return safeCall("add address") {
            val dataWithUser = addressData.toMutableMap()
            dataWithUser["user_id"] = userId
            val response = apiService.addAddress(dataWithUser)
            
            // If this is set as default, we might need to handle unsetting others 
            // but the SQL trigger will handle that more reliably.
            
            apiService.logAction(mapOf(
                "user_id" to userId,
                "action" to "ADDRESS_ADDED",
                "details" to "User added new address: ${addressData["name"]}",
                "severity" to "info"
            ))
            response
        }
    }

    suspend fun updateAddress(addressId: String, addressData: Map<String, Any>): Result<Map<String, Any>> {
        return safeCall("update address") {
            val response = apiService.updateAddress("eq.$addressId", addressData)
            
            getUserId()?.let { userId ->
                apiService.logAction(mapOf(
                    "user_id" to userId,
                    "action" to "ADDRESS_UPDATED",
                    "details" to "User updated address: $addressId",
                    "severity" to "info"
                ))
            }
            response
        }
    }

    suspend fun deleteAddress(addressId: String): Result<Unit> {
        return safeCall("delete address") {
            apiService.deleteAddress("eq.$addressId")
            
            getUserId()?.let { userId ->
                apiService.logAction(mapOf(
                    "user_id" to userId,
                    "action" to "ADDRESS_DELETED",
                    "details" to "User deleted address: $addressId",
                    "severity" to "info"
                ))
            }
            Unit
        }
    }

    suspend fun setDefaultAddress(userId: String, addressId: String): Result<Unit> {
        return safeCall("set default address") {
            // Logic: Set all user's addresses to is_default = false, then this one to true.
            // A better way is a single RPC or a SQL trigger. 
            // For now, we'll do two calls or rely on a SQL trigger if we write one.
            // Let's assume we'll write a SQL trigger to handle "only one default".
            apiService.updateAddress("eq.$addressId", mapOf("is_default" to true))
            Unit
        }
    }

    suspend fun getUserFavorites(userId: String): List<Map<String, Any>> {
        return try {
            apiService.getUserFavorites("eq.$userId")
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun toggleFavorite(userId: String, productId: String, isFavorite: Boolean): Result<Unit> {
        return try {
            if (isFavorite) {
                apiService.addFavorite(mapOf("user_id" to userId, "product_id" to productId))
            } else {
                apiService.removeFavorite("eq.$userId", "eq.$productId")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun revokeSession(sessionId: String): Result<Unit> {
        return try {
            apiService.revokeSession("eq.$sessionId")
            
            // Audit Log: Session Revocation
            getUserId()?.let { userId ->
                try {
                    apiService.logAction(mapOf(
                        "user_id" to userId,
                        "action" to "SESSION_REVOKED",
                        "details" to "User revoked session: $sessionId",
                        "severity" to "warning"
                    ))
                } catch (e: Exception) {}
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        securityManager.setBiometricEnabled(enabled)
        getUserId()?.let { userId ->
            repositoryScope.launch {
                updateProfile(userId, ProfileUpdateRequest(biometricEnabled = enabled))
            }
        }
    }

    fun isBiometricEnabled(): Boolean = securityManager.isBiometricEnabled()

    fun hasPromptedBiometrics(): Boolean = securityManager.hasPromptedBiometrics()

    fun setPromptedBiometrics(prompted: Boolean) {
        securityManager.setPromptedBiometrics(prompted)
    }

    suspend fun setNotificationsEnabled(userId: String, enabled: Boolean): Result<Unit> {
        return updateProfile(userId, ProfileUpdateRequest(notificationsEnabled = enabled))
    }

    suspend fun markNotificationAsRead(notificationId: Int): Result<Unit> {
        return try {
            apiService.updateNotification("eq.$notificationId", ProfileUpdateRequest(isRead = true))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun getAdminContact(): Result<Map<String, String>> {
        return try {
            val response = apiService.getAdminContact()
            if (response.isNotEmpty()) {
                Result.success(response.first())
            } else {
                Result.failure(Exception("Admin contact not found"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun getLoyaltyHistory(userId: String): List<Map<String, Any>> {
        return try {
            apiService.getLoyaltyHistory("eq.$userId")
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getLoyaltyTiers(): List<Map<String, Any>> {
        return try {
            apiService.getLoyaltyTiers()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun createSubscription(params: Map<String, Any>): Result<Unit> = try {
        apiService.createSubscription(params)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
    }

    suspend fun getUserSubscriptions(userId: String): List<Map<String, Any>> = try {
        apiService.getUserSubscriptions("eq.$userId")
    } catch (e: Exception) {
        emptyList()
    }

    suspend fun updateSubscription(id: String, status: String): Result<Unit> = try {
        apiService.updateSubscription("eq.$id", mapOf("status" to status))
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
    }
}

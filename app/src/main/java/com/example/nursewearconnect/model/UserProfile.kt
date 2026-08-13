package com.example.nursewearconnect.model

import com.google.gson.annotations.SerializedName

data class UserProfile(
    @SerializedName("id")
    val id: String? = null,
    @SerializedName("full_name")
    val fullName: String? = null,
    @SerializedName("email")
    val email: String? = null,
    @SerializedName("phone_number")
    val phoneNumber: String? = null,
    @SerializedName("role")
    val role: String? = null,
    @SerializedName("status")
    val status: String? = null,
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,
    @SerializedName("institution")
    val institution: String? = null,
    @SerializedName("address")
    val address: String? = null,
    @SerializedName("business_name")
    val businessName: String? = null,
    @SerializedName("location")
    val location: String? = null,
    @SerializedName("bio")
    val bio: String? = null,
    @SerializedName("business_description")
    val businessDescription: String? = null,
    @SerializedName("bank_code")
    val bankCode: String? = null,
    @SerializedName("bank_account_number")
    val bankAccountNumber: String? = null,
    @SerializedName("paystack_recipient_code")
    val paystackRecipientCode: String? = null,
    @SerializedName("commission_rate")
    val commissionRate: Double? = null,
    @SerializedName("status_notes")
    val statusNotes: String? = null,
    @SerializedName("rejection_reason")
    val rejectionReason: String? = null,
    @SerializedName("fcm_token")
    val fcmToken: String? = null,
    @SerializedName("rating")
    val rating: Double? = null,
    @SerializedName("reviews_count")
    val reviewsCount: Int? = null,
    @SerializedName("is_verified_vendor")
    val isVerifiedVendor: Boolean? = null,
    @SerializedName("total_sales_count")
    val totalSalesCount: Int? = null,
    @SerializedName("biometric_enabled")
    val biometricEnabled: Boolean? = null,
    @SerializedName("notifications_enabled")
    val notificationsEnabled: Boolean? = null,
    @SerializedName("measurements")
    val measurements: Map<String, Any>? = null,
    @SerializedName("business_license_url")
    val businessLicenseUrl: String? = null,
    @SerializedName("document_status")
    val documentStatus: String? = null,
    @SerializedName("loyalty_points")
    val loyaltyPoints: Int = 0,
    @SerializedName("loyalty_tier")
    val loyaltyTier: String? = "bronze",
    @SerializedName("wallet_balance")
    val walletBalance: Double? = 0.0,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null,
    @SerializedName("referral_code")
    val referralCode: String? = null,
    @SerializedName("referred_by")
    val referredBy: String? = null
)

data class ProfileUpdateRequest(
    @SerializedName("full_name")
    val fullName: String? = null,
    @SerializedName("phone_number")
    val phoneNumber: String? = null,
    @SerializedName("institution")
    val institution: String? = null,
    @SerializedName("address")
    val address: String? = null,
    @SerializedName("business_name")
    val businessName: String? = null,
    @SerializedName("location")
    val location: String? = null,
    @SerializedName("bio")
    val bio: String? = null,
    @SerializedName("business_description")
    val businessDescription: String? = null,
    @SerializedName("bank_code")
    val bankCode: String? = null,
    @SerializedName("bank_account_number")
    val bankAccountNumber: String? = null,
    @SerializedName("avatar_url")
    val avatarUrl: String? = null,
    @SerializedName("business_license_url")
    val businessLicenseUrl: String? = null,
    @SerializedName("document_status")
    val documentStatus: String? = null,
    @SerializedName("measurements")
    val measurements: Map<String, Any>? = null,
    @SerializedName("status")
    val status: String? = null,
    @SerializedName("role")
    val role: String? = null,
    @SerializedName("status_notes")
    val statusNotes: String? = null,
    @SerializedName("rejection_reason")
    val rejectionReason: String? = null,
    @SerializedName("fcm_token")
    val fcmToken: String? = null,
    @SerializedName("biometric_enabled")
    val biometricEnabled: Boolean? = null,
    @SerializedName("notifications_enabled")
    val notificationsEnabled: Boolean? = null,
    @SerializedName("is_read")
    val isRead: Boolean? = null,
    @SerializedName("referred_by")
    val referredBy: String? = null
)

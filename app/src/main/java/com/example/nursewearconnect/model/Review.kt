package com.example.nursewearconnect.model

import com.google.gson.annotations.SerializedName

data class Review(
    val id: String,
    @SerializedName("product_id")
    val productId: String,
    @SerializedName("user_id")
    val userId: String,
    val rating: Int,
    val comment: String?,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("user_profile")
    val userProfile: UserProfile? = null,
    val images: List<String> = emptyList()
)

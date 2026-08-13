package com.example.nursewearconnect.model

import com.google.gson.annotations.SerializedName

data class Bundle(
    val id: String,
    val name: String,
    val description: String?,
    @SerializedName("discount_percent")
    val discountPercent: Double,
    @SerializedName("is_active")
    val isActive: Boolean = true,
    @SerializedName("created_at")
    val createdAt: String? = null,
    @SerializedName("bundle_items")
    val items: List<BundleItem> = emptyList()
)

data class BundleItem(
    @SerializedName("bundle_id")
    val bundleId: String,
    @SerializedName("product_id")
    val productId: String,
    val products: Product? = null // For joined queries
)

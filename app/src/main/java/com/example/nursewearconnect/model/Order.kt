package com.example.nursewearconnect.model

import com.google.gson.annotations.SerializedName

data class Order(
    @SerializedName("id")
    val id: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("vendor_id")
    val vendorId: String? = null,
    @SerializedName("total_amount")
    val totalAmount: Double,
    @SerializedName("discount_amount")
    val discountAmount: Double = 0.0,
    @SerializedName("final_amount")
    val finalAmount: Double,
    @SerializedName("status")
    val status: String, // pending, processing, shipped, delivered, cancelled
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("order_items")
    val items: List<OrderItem>? = null
)

data class OrderItem(
    @SerializedName("id")
    val id: String,
    @SerializedName("order_id")
    val orderId: String,
    @SerializedName("product_id")
    val productId: String,
    @SerializedName("quantity")
    val quantity: Int,
    @SerializedName("unit_price")
    val unitPrice: Double,
    @SerializedName("size")
    val size: String? = null,
    @SerializedName("color")
    val color: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null,
    // Joined field for UI
    @SerializedName("product")
    val product: Product? = null
)

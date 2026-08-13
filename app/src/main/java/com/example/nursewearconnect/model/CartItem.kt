package com.example.nursewearconnect.model

data class CartItem(
    val product: Product,
    val size: String,
    val color: ProductColor?,
    val quantity: Int,
    val embroideryName: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

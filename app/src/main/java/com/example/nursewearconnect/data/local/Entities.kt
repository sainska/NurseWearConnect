package com.example.nursewearconnect.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.nursewearconnect.model.ProductColor

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val gender: String,
    val priceKes: Double,
    val rating: Double,
    val reviewsCount: Int,
    val stockCount: Int,
    val tag: String?,
    val images: List<String>,
    val description: String,
    val material: String,
    val features: List<String>,
    val inStock: Boolean,
    val isActive: Boolean,
    val availableSizes: List<String>,
    val availableColors: List<ProductColor>,
    val subCategory: String?,
    val vendorId: String?,
    val vendorName: String?,
    val vendorBusinessName: String?,
    val vendorRating: Double?,
    val vendorAvatar: String?,
    val flashSaleEnd: String?,
    val flashSalePrice: Double?
)

@Entity(tableName = "sync_actions")
data class SyncActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionType: String, // e.g., "ADD_TO_CART", "REMOVE_FROM_CART", "UPDATE_CART"
    val payloadJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String?
)

@Entity(tableName = "cart_items")
data class CartItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val productId: String,
    val quantity: Int,
    val size: String,
    val colorName: String?,
    val colorHex: Long?,
    val embroideryName: String?,
    val userId: String, // To handle logout/login transitions
    val updatedAt: Long = System.currentTimeMillis()
)

package com.example.nursewearconnect.model

import com.google.gson.annotations.SerializedName
import kotlin.jvm.Transient

data class Product(
    val id: String = "",
    val name: String = "Product",
    val category: String = "General",
    val gender: String = "Unisex", // "Male", "Female", or "Unisex"
    @SerializedName("price_kes")
    val priceKes: Double = 0.0,
    val rating: Double = 5.0,
    @SerializedName("reviews_count")
    val reviewsCount: Int = 0,
    @SerializedName("stock_count")
    val stockCount: Int = 0,
    val tag: String? = null,
    val images: List<String> = emptyList(),
    val description: String = "",
    val material: String = "High-quality, breathable fabric designed for all-day comfort.",
    val features: List<String> = emptyList(),
    @SerializedName("in_stock")
    val inStock: Boolean = true,
    @SerializedName("is_active")
    val isActive: Boolean = true,
    @SerializedName("available_sizes")
    val availableSizes: List<String> = listOf("XS", "S", "M", "L", "XL", "XXL"),
    @SerializedName("available_colors")
    val availableColors: List<ProductColor> = listOf(
        ProductColor("Navy", 0xFF1E3A8A),
        ProductColor("Black", 0xFF000000),
        ProductColor("Teal", 0xFF0D9488)
    ),
    @SerializedName("sub_category")
    val subCategory: String? = null,
    @SerializedName("measurement_guide")
    val measurementGuide: Map<String, String>? = null,
    @SerializedName("vendor_id")
    val vendorId: String? = null,
    @SerializedName("featured")
    val featured: Boolean = false,
    @SerializedName("vendor_name")
    val vendorName: String? = null,
    @SerializedName("vendor_business_name")
    val vendorBusinessName: String? = null,
    @SerializedName("vendor_rating")
    val vendorRating: Double? = null,
    @SerializedName("vendor_avatar")
    val vendorAvatar: String? = null,
    @SerializedName("updated_at")
    val updatedAt: String? = null,
    @SerializedName("flash_sale_end")
    val flashSaleEnd: String? = null,
    @SerializedName("flash_sale_price")
    val flashSalePrice: Double? = null
)

data class ProductColor(
    val name: String,
    val hex: Long
)

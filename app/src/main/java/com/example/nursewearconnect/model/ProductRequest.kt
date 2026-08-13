package com.example.nursewearconnect.model

import com.google.gson.annotations.SerializedName

/**
 * Request model for creating or updating a product.
 * This model only contains fields that exist in the 'products' table,
 * excluding view-only fields like 'vendor_name' to avoid PGRST204 errors.
 */
data class ProductRequest(
    @SerializedName("id")
    val id: String? = null,
    @SerializedName("name")
    val name: String,
    @SerializedName("category")
    val category: String,
    @SerializedName("gender")
    val gender: String,
    @SerializedName("price_kes")
    val priceKes: Double,
    @SerializedName("stock_count")
    val stockCount: Int = 0,
    @SerializedName("tag")
    val tag: String? = null,
    @SerializedName("images")
    val images: List<String>,
    @SerializedName("description")
    val description: String = "",
    @SerializedName("material")
    val material: String = "High-quality fabric",
    @SerializedName("features")
    val features: List<String> = emptyList(),
    @SerializedName("in_stock")
    val inStock: Boolean = true,
    @SerializedName("is_active")
    val isActive: Boolean = true,
    @SerializedName("available_sizes")
    val availableSizes: List<String> = listOf("XS", "S", "M", "L", "XL", "XXL"),
    @SerializedName("available_colors")
    val availableColors: List<ProductColor> = emptyList(),
    @SerializedName("sub_category")
    val subCategory: String? = null,
    @SerializedName("measurement_guide")
    val measurementGuide: Map<String, String>? = null,
    @SerializedName("vendor_id")
    val vendorId: String? = null,
    @SerializedName("category_id")
    val categoryId: String? = null,
    @SerializedName("featured")
    val featured: Boolean = false
)

fun Product.toRequest() = ProductRequest(
    id = if (id.isBlank()) null else id,
    name = name,
    category = category,
    gender = gender,
    priceKes = priceKes,
    stockCount = stockCount,
    tag = tag,
    images = images,
    description = description,
    material = material,
    features = features,
    inStock = inStock,
    isActive = isActive,
    availableSizes = availableSizes,
    availableColors = availableColors,
    subCategory = subCategory,
    measurementGuide = measurementGuide,
    vendorId = vendorId,
    categoryId = null, // Set if category mapping logic is implemented
    featured = this.featured || tag?.contains("Featured", ignoreCase = true) == true
)

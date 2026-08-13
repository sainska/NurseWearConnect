package com.example.nursewearconnect.data.repository

import com.example.nursewearconnect.data.api.ApiService
import com.example.nursewearconnect.data.local.ProductDao
import com.example.nursewearconnect.data.local.ProductEntity
import com.example.nursewearconnect.model.Product
import com.example.nursewearconnect.model.ProductColor
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class ProductRepository(
    private val apiService: ApiService,
    private val productDao: ProductDao,
    private val categoryDao: com.example.nursewearconnect.data.local.CategoryDao,
    private val appDatabase: com.example.nursewearconnect.data.local.AppDatabase,
    private val supabaseClient: io.github.jan.supabase.SupabaseClient
) {
    /**
     * Listen for real-time changes on the products table to trigger cache refreshes
     */
    fun getProductsRealtime(): kotlinx.coroutines.flow.Flow<io.github.jan.supabase.realtime.PostgresAction> {
        val channel = supabaseClient.channel("public_catalog_sync")
        return channel.postgresChangeFlow<io.github.jan.supabase.realtime.PostgresAction>(schema = "public") {
            table = "products"
        }
    }
    /**
     * Clear all local caches (useful for logout)
     */
    suspend fun clearCache() {
        appDatabase.clearAllCaches()
    }
    /**
     * Observable flow of products. This acts as the "Single Source of Truth".
     * When there is no internet, this emits the already preloaded data from the local database.
     */
    val products: Flow<List<Product>> = productDao.getAllProducts().map { entities ->
        entities.map { it.toDomain() }
    }

    /**
     * Observable flow of categories from the local database.
     */
    val categories: Flow<List<com.example.nursewearconnect.model.Category>> = categoryDao.getAllCategories().map { entities ->
        entities.map { it.toDomain() }
    }

    /**
     * Fetches real data from the remote database and updates the local cache.
     * If there is internet connectivity, data is refreshed.
     * If offline, the UI continues to show cached data via the [products] flow.
     */
    suspend fun refreshProducts(): Result<List<Product>> {
        return try {
            val fetchedProducts = apiService.getProducts()
            // Update local DB - this will automatically trigger updates in the [products] flow
            productDao.refreshProducts(fetchedProducts.map { it.toEntity() })
            Result.success(fetchedProducts)
        } catch (e: Exception) {
            // Return failure but local data remains available via the Flow
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun getFeaturedProducts(): List<Product> {
        return try {
            apiService.getFeaturedProducts()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getCategories(): Result<List<com.example.nursewearconnect.model.Category>> = try {
        val fetched = apiService.getCategories()
        categoryDao.insertCategories(fetched.map { it.toEntity() })
        Result.success(fetched)
    } catch (e: Exception) {
        Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
    }

    suspend fun addCategory(name: String, description: String? = null, iconName: String? = null, adminId: String? = null): Result<Unit> = try {
        val categoryData = mutableMapOf("name" to name)
        description?.let { categoryData["description"] = it }
        iconName?.let { categoryData["icon_name"] = it }
        apiService.addCategory(categoryData)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
    }

    suspend fun deleteCategory(name: String, adminId: String? = null): Result<Unit> = try {
        apiService.deleteCategory("eq.$name")
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
    }

    suspend fun getCoupons(code: String? = null): Result<List<Map<String, Any>>> = try {
        Result.success(apiService.getCoupons(code))
    } catch (e: Exception) {
        Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
    }

    suspend fun getCouponUsageStats(): Result<List<Map<String, Any>>> = try {
        Result.success(apiService.getCouponUsageStats())
    } catch (e: Exception) {
        Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
    }

    suspend fun addCoupon(coupon: Map<String, Any>, adminId: String? = null): Result<Unit> = try {
        apiService.addCoupon(coupon)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
    }

    suspend fun deleteCoupon(id: String, adminId: String? = null): Result<Unit> = try {
        apiService.deleteCoupon("eq.$id")
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
    }

    suspend fun cleanupExpiredCoupons(): Result<Unit> = try {
        apiService.checkMarketingExpirations()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
    }

    suspend fun getBanners(status: String? = null, vendorId: String? = null): Result<List<Map<String, Any>>> = try {
        val statusParam = status?.let { "eq.$it" }
        val vendorParam = vendorId?.let { "eq.$it" }
        Result.success(apiService.getBanners(statusParam, vendorParam))
    } catch (e: Exception) {
        Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
    }

    suspend fun updateBanner(id: String, updates: Map<String, Any>): Result<Unit> = try {
        apiService.updateBanner("eq.$id", updates)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
    }

    suspend fun addBanner(banner: Map<String, Any>, adminId: String? = null): Result<Unit> = try {
        apiService.addBanner(banner)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
    }

    suspend fun deleteBanner(id: String, adminId: String? = null): Result<Unit> = try {
        apiService.deleteBanner("eq.$id")
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
    }

    suspend fun searchProducts(query: String): Result<List<Product>> {
        return try {
            // Server-side full-text search using Supabase Postgrest
            // The 'fts' filter uses the search index if created in SQL
            val results = apiService.searchProducts("fts.$query")
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }
    suspend fun getProductReviews(productId: String): Result<List<Map<String, Any>>> {
        return try {
            Result.success(apiService.getProductReviews("eq.$productId"))
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun addReview(productId: String, userId: String, rating: Int, comment: String): Result<Unit> {
        return try {
            val reviewData = mapOf(
                "product_id" to productId,
                "user_id" to userId,
                "rating" to rating,
                "comment" to comment
            )
            apiService.addReview(reviewData)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun getActiveFlashSales(): Result<List<Map<String, Any>>> = try {
        Result.success(apiService.getActiveFlashSales())
    } catch (e: Exception) {
        Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
    }

    suspend fun visualSearchProducts(visualTags: List<String>): Result<List<Product>> = try {
        Result.success(apiService.visualSearchProducts(mapOf("p_visual_tags" to visualTags)))
    } catch (e: Exception) {
        Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
    }

    suspend fun joinWaitlist(productId: String): Result<Unit> = try {
        val response = apiService.joinWaitlist(mapOf("p_product_id" to productId))
        if (response.isSuccessful) Result.success(Unit)
        else Result.failure(Exception("Failed to join waitlist"))
    } catch (e: Exception) {
        Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
    }

    suspend fun getActiveBundles(): Result<List<com.example.nursewearconnect.model.Bundle>> = try {
        Result.success(apiService.getActiveBundles())
    } catch (e: Exception) {
        Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
    }

    /**
     * Uploads an image to Supabase Storage and returns the public URL.
     */
    suspend fun uploadImage(bucketName: String, path: String, bytes: ByteArray): Result<String> = try {
        val bucket = supabaseClient.storage[bucketName]
        bucket.upload(path, bytes) {
            upsert = true
        }
        Result.success(bucket.publicUrl(path))
    } catch (e: Exception) {
        Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
    }
}

fun ProductEntity.toDomain() = Product(
    id = id,
    name = name,
    category = category,
    gender = gender,
    priceKes = priceKes,
    rating = rating,
    reviewsCount = reviewsCount,
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
    vendorId = vendorId,
    vendorName = vendorName,
    vendorBusinessName = vendorBusinessName,
    vendorRating = vendorRating,
    vendorAvatar = vendorAvatar,
    flashSaleEnd = flashSaleEnd,
    flashSalePrice = flashSalePrice
)

fun Product.toEntity() = ProductEntity(
    id = id,
    name = name,
    category = category,
    gender = gender,
    priceKes = priceKes,
    rating = rating,
    reviewsCount = reviewsCount,
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
    vendorId = vendorId,
    vendorName = vendorName,
    vendorBusinessName = vendorBusinessName,
    vendorRating = vendorRating,
    vendorAvatar = vendorAvatar,
    flashSaleEnd = flashSaleEnd,
    flashSalePrice = flashSalePrice
)

fun com.example.nursewearconnect.data.local.CategoryEntity.toDomain() = com.example.nursewearconnect.model.Category(
    id = id,
    name = name
)

fun com.example.nursewearconnect.model.Category.toEntity() = com.example.nursewearconnect.data.local.CategoryEntity(
    id = id,
    name = name,
    icon = null
)

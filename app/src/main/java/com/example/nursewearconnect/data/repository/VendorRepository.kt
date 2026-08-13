package com.example.nursewearconnect.data.repository

import com.example.nursewearconnect.data.api.ApiService
import com.example.nursewearconnect.model.Product
import com.example.nursewearconnect.model.toRequest
import com.example.nursewearconnect.utils.AppUtils
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.Flow

/**
 * Repository for vendor-related operations, including product management,
 * order processing, and realtime synchronization.
 */
class VendorRepository(
    private val apiService: ApiService,
    private val adminRepository: AdminRepository,
    private val supabaseClient: SupabaseClient
) {

    /**
     * Executes a network call safely with standardized logging and error mapping.
     */
    private suspend fun <T> safeCall(action: String, block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: Exception) {
            val error = AppUtils.mapThrowable(e)
            android.util.Log.e(TAG, "Error during $action: $error", e)
            Result.failure(Exception(error))
        }
    }

    /**
     * Subscribes to realtime updates for products belonging to a specific vendor.
     */
    fun getVendorProductsRealtime(vendorId: String): Flow<PostgresAction> {
        val channel = supabaseClient.channel("vendor_products_$vendorId")
        return channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "products"
            filter("vendor_id", FilterOperator.EQ, vendorId)
        }
    }

    /**
     * Subscribes to realtime updates for orders assigned to a specific vendor.
     */
    fun getVendorOrdersRealtime(vendorId: String): Flow<PostgresAction> {
        val channel = supabaseClient.channel("vendor_orders_$vendorId")
        return channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "orders"
            filter("vendor_id", FilterOperator.EQ, vendorId)
        }
    }

    /**
     * Retrieves all products for the specified vendor.
     */
    suspend fun getVendorProducts(vendorId: String): Result<List<Product>> =
        safeCall("fetch vendor products") {
            apiService.getVendorProducts("eq.$vendorId")
        }

    /**
     * Adds a new product to the database and logs the action.
     */
    suspend fun addProduct(product: Product): Result<Product> =
        safeCall("add product") {
            val added = apiService.addProduct(product.toRequest())
            product.vendorId?.let {
                adminRepository.logAction(it, "ADD_PRODUCT", "Vendor added product: ${product.name}", "info")
            }
            added
        }

    /**
     * Updates an existing product's details and logs the action.
     */
    suspend fun updateProduct(product: Product): Result<Product> =
        safeCall("update product") {
            val updated = apiService.updateProduct("eq.${product.id}", product.toRequest()).firstOrNull()
                ?: throw Exception("Failed to update product on server")
            
            product.vendorId?.let {
                adminRepository.logAction(it, "UPDATE_PRODUCT", "Vendor updated product: ${product.name}", "info")
            }
            updated
        }

    /**
     * Soft deletes a product by marking it as inactive and logs the action.
     */
    suspend fun deleteProduct(productId: String, vendorId: String? = null): Result<Unit> =
        safeCall("delete product") {
            apiService.updateProductInventoryRpc(mapOf(
                "p_product_id" to productId,
                "p_is_active" to false
            ))
            
            vendorId?.let {
                adminRepository.logAction(it, "DELETE_PRODUCT", "Vendor deactivated product ID: $productId", "warning")
            }
        }

    /**
     * Updates the stock count for a product.
     */
    suspend fun updateProductStock(productId: String, newStock: Int, vendorId: String? = null): Result<Unit> =
        safeCall("update product stock") {
            apiService.updateProductInventoryRpc(mapOf(
                "p_product_id" to productId,
                "p_stock_count" to newStock
            ))
            
            vendorId?.let {
                adminRepository.logAction(it, "UPDATE_STOCK", "Updated stock for product $productId to $newStock", "info")
            }
        }

    /**
     * Retrieves all orders for the specified vendor.
     */
    suspend fun getVendorOrders(vendorId: String): Result<List<Map<String, Any>>> =
        safeCall("fetch vendor orders") {
            apiService.getVendorOrders("eq.$vendorId")
        }

    /**
     * Retrieves comprehensive analytics for the vendor dashboard.
     */
    suspend fun getVendorAnalytics(vendorId: String, days: Int = 30): Result<Map<String, Any>> =
        safeCall("fetch vendor analytics") {
            apiService.getVendorDashboardStats(mapOf(
                "p_vendor_id" to vendorId,
                "p_days" to days
            ))
        }

    /**
     * Retrieves revenue breakdown for different time periods.
     */
    suspend fun getVendorRevenueBreakdown(vendorId: String): Result<List<Map<String, Any>>> =
        safeCall("fetch vendor revenue breakdown") {
            apiService.getVendorRevenueBreakdown(mapOf("p_vendor_id" to vendorId))
        }

    /**
     * Updates the status of an order item and triggers follow-up notifications if necessary.
     */
    suspend fun updateOrderItemStatus(orderItemId: String, orderId: String, status: String, vendorId: String? = null, userId: String? = null): Result<Unit> =
        safeCall("update order item status") {
            apiService.updateOrderItemStatus("eq.$orderItemId", mapOf("status" to status))
            
            vendorId?.let {
                adminRepository.logAction(it, "UPDATE_ORDER_ITEM_STATUS", "Vendor updated order item $orderItemId (Order $orderId) to $status", "info")
            }

            // Trigger FCM via internal notification system (assuming Supabase Edge Function handles the broadcast)
            userId?.let {
                val title = "Order Update"
                val body = "An item in your order #$orderId is now $status."
                adminRepository.createNotification(it, title, body, "order_update")
            }

            if (status.equals("delivered", ignoreCase = true)) {
                triggerReviewPrompt(orderId)
            }
        }

    /**
     * Sends a review prompt notification to the user who placed the order.
     */
    private suspend fun triggerReviewPrompt(orderId: String) {
        try {
            val order = apiService.getAllOrders(idSearch = "eq.$orderId").firstOrNull()
            val userId = order?.get("user_id")?.toString()
            
            if (userId != null) {
                apiService.createNotification(mapOf(
                    "user_id" to userId,
                    "title" to "Rate your purchase!",
                    "body" to "Your order #$orderId has been delivered. Please share your feedback to help others!",
                    "category" to "REVIEW_PROMPT",
                    "priority_level" to "normal"
                ))
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to send review prompt for order $orderId", e)
        }
    }

    companion object {
        private const val TAG = "VendorRepository"
    }
}

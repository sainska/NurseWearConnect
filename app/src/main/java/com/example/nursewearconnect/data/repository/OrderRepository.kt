package com.example.nursewearconnect.data.repository

import com.example.nursewearconnect.data.api.ApiService
import com.example.nursewearconnect.model.CartItem
import com.example.nursewearconnect.utils.AppUtils
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class OrderResult {
    data class Success(val orderId: String) : OrderResult()
    data class Error(val message: String) : OrderResult()
    object Loading : OrderResult()
}

class OrderRepository(
    private val apiService: ApiService,
    private val supabaseClient: SupabaseClient
) {
    private val _orderState = MutableStateFlow<OrderResult?>(null)
    val orderState: StateFlow<OrderResult?> = _orderState.asStateFlow()

    suspend fun placeOrder(
        userId: String,
        cartItems: List<CartItem>,
        totalAmount: Double,
        shippingAddress: String,
        addressId: String? = null,
        discountAmount: Double = 0.0,
        couponCode: String? = null,
        digitalReceiptEnabled: Boolean = true,
        fittingRequested: Boolean = false,
        fittingDate: String? = null,
        fittingSlot: String? = null
    ): OrderResult {
        _orderState.value = OrderResult.Loading
        return try {
            // 2. Create Order
            val vendorId = cartItems.firstOrNull()?.product?.vendorId ?: "admin"
            
            val orderData = mutableMapOf<String, Any?>(
                "user_id" to userId,
                "vendor_id" to if (vendorId == "admin" || vendorId.isBlank()) null else vendorId,
                "total_amount" to totalAmount + discountAmount,
                "discount_amount" to discountAmount,
                "final_amount" to totalAmount,
                "shipping_address" to shippingAddress,
                "currency" to "KES",
                "status" to "pending",
                "digital_receipt_enabled" to digitalReceiptEnabled,
                "is_fitting_service" to fittingRequested
            )
            
            if (!couponCode.isNullOrBlank()) {
                orderData["coupon_code"] = couponCode
            }

            val response = apiService.createOrder(orderData = orderData)
            val orderId = response.firstOrNull()?.get("id")?.toString() ?: throw Exception("Failed to create order: No ID returned")

            // Request fitting service if selected
            if (fittingRequested && fittingDate != null && fittingSlot != null) {
                try {
                    // Align with RPC signature from try_before_you_buy.sql
                    // p_order_id, p_scheduled_at, p_address_id, p_instructions
                    val fittingParams = mutableMapOf<String, Any?>(
                        "p_order_id" to orderId,
                        "p_scheduled_at" to fittingDate,
                        "p_address_id" to addressId,
                        "p_instructions" to "Slot: $fittingSlot"
                    )
                    apiService.requestFittingService(fittingParams as Map<String, Any>)
                } catch (e: Exception) {
                    // Log error but don't fail the whole order
                }
            }
            
            // Audit Log: Order Created
            try {
                apiService.logAction(mapOf(
                    "user_id" to userId,
                    "action" to "ORDER_CREATED",
                    "details" to "Order #$orderId created for KES $totalAmount",
                    "severity" to "info"
                ))
            } catch (e: Exception) {}
            
            // Insert order items
            cartItems.forEach { item ->
                val itemVendorId = item.product.vendorId
                val itemData = mutableMapOf<String, Any?>(
                    "order_id" to orderId,
                    "product_id" to item.product.id,
                    "quantity" to item.quantity,
                    "unit_price" to item.product.priceKes,
                    "price_at_purchase" to item.product.priceKes,
                    "size" to item.size,
                    "color" to if (item.color != null) mapOf("name" to item.color.name, "hex" to item.color.hex) else "Default",
                    "vendor_id" to (if (itemVendorId == "admin" || itemVendorId.isNullOrBlank()) null else itemVendorId)
                )
                if (item.embroideryName != null) {
                    itemData["embroidery_name"] = item.embroideryName
                    itemData["has_embroidery"] = true
                }
                apiService.createOrderItem(itemData = itemData)
            }

            // Create notification for customer
            try {
                apiService.createNotification(mapOf(
                    "user_id" to userId,
                    "title" to "Order Placed",
                    "body" to "Your order #$orderId has been placed successfully. Please complete payment.",
                    "category" to "order",
                    "priority_level" to "high"
                ))
            } catch (e: Exception) {
                // Non-critical, ignore
            }

            val result = OrderResult.Success(orderId)
            _orderState.value = result
            result
        } catch (e: Exception) {
            val result = OrderResult.Error(AppUtils.mapThrowable(e))
            _orderState.value = result
            result
        }
    }

    suspend fun getUserOrders(filter: String): Result<List<Map<String, Any>>> {
        return try {
            Result.success(apiService.getUserOrders(filter))
        } catch (e: Exception) {
            Result.failure(Exception(AppUtils.mapThrowable(e)))
        }
    }

    fun getUserOrdersRealtime(userId: String): Flow<PostgresAction> {
        val channel = supabaseClient.channel("user_orders_$userId")
        return channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "orders"
            filter("user_id", FilterOperator.EQ, userId)
        }
    }

    suspend fun getOrderStatusHistory(orderId: String): Result<List<Map<String, Any>>> {
        return try {
            // Ensure the Supabase 'eq.' operator is prepended to the filter
            val filter = if (orderId.startsWith("eq.")) orderId else "eq.$orderId"
            Result.success(apiService.getOrderStatusHistory(filter))
        } catch (e: Exception) {
            Result.failure(Exception(AppUtils.mapThrowable(e)))
        }
    }

    suspend fun sendReceiptEmail(orderId: String, email: String): Result<Unit> {
        return try {
            apiService.sendReceiptEmail(mapOf("orderId" to orderId, "email" to email))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun initiateReturn(params: Map<String, Any>): Result<Unit> {
        return try {
            val response = apiService.initiateReturn(params)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception(response.message()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getReturnRequests(userId: String): Result<List<Map<String, Any>>> {
        return try {
            // Ensure the Supabase 'eq.' operator is prepended to the filter
            val filter = if (userId.startsWith("eq.")) userId else "eq.$userId"
            Result.success(apiService.getReturnRequests(filter))
        } catch (e: Exception) {
            Result.failure(Exception(AppUtils.mapThrowable(e)))
        }
    }

    suspend fun processRefund(params: Map<String, Any>): Result<Unit> {
        return try {
            val response = apiService.processRefund(params)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception(response.message()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getOrderDetails(orderId: String): Result<Map<String, Any>?> {
        return try {
            val response = apiService.getAllOrders(idSearch = "eq.$orderId")
            Result.success(response.firstOrNull())
        } catch (e: Exception) {
            Result.failure(Exception(AppUtils.mapThrowable(e)))
        }
    }

    suspend fun updateOrderTracking(orderId: String, trackingNumber: String, courierName: String, status: String? = null): Result<Unit> {
        return try {
            val params = mutableMapOf<String, Any?>(
                "p_order_id" to orderId,
                "p_tracking_number" to trackingNumber,
                "p_courier_name" to courierName
            )
            if (status != null) params["p_status"] = status
            val response = apiService.updateOrderTracking(params)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception(response.message()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

}

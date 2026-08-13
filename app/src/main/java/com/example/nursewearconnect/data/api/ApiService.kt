package com.example.nursewearconnect.data.api

import com.example.nursewearconnect.model.Product
import com.example.nursewearconnect.model.ProfileUpdateRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query
import retrofit2.http.Path

@JvmSuppressWildcards
interface ApiService {
    // Products
    @GET("rest/v1/catalog_products")
    suspend fun getProducts(): List<Product>

    @GET("rest/v1/catalog_products")
    suspend fun getProductById(@Query("id") filter: String): List<Product>

    // Categories
    @GET("rest/v1/categories")
    suspend fun getCategories(): List<com.example.nursewearconnect.model.Category>
    
    @POST("rest/v1/categories")
    suspend fun addCategory(@Body category: Map<String, @JvmSuppressWildcards String>): com.example.nursewearconnect.model.Category

    @DELETE("rest/v1/categories")
    suspend fun deleteCategory(@Query("name") filter: String): Map<String, @JvmSuppressWildcards String>

    // Coupons
    @GET("rest/v1/coupons")
    suspend fun getCoupons(@Query("code") code: String? = null): List<Map<String, @JvmSuppressWildcards Any>>

    @GET("rest/v1/coupon_usage_stats?select=*")
    suspend fun getCouponUsageStats(): List<Map<String, @JvmSuppressWildcards Any>>

    @POST("rest/v1/coupons")
    @retrofit2.http.Headers("Prefer: return=representation")
    suspend fun addCoupon(@Body coupon: Map<String, @JvmSuppressWildcards Any>): Map<String, @JvmSuppressWildcards Any>

    @DELETE("rest/v1/coupons")
    suspend fun deleteCoupon(@Query("id") filter: String): Map<String, @JvmSuppressWildcards String>

    // Banners
    @GET("rest/v1/banners")
    suspend fun getBanners(@Query("status") status: String? = null, @Query("vendor_id") vendorId: String? = null): List<Map<String, @JvmSuppressWildcards Any>>

    @PATCH("rest/v1/banners")
    suspend fun updateBanner(@Query("id") filter: String, @Body updates: Map<String, @JvmSuppressWildcards Any>): Map<String, @JvmSuppressWildcards Any>

    @POST("rest/v1/banners")
    suspend fun addBanner(@Body banner: Map<String, @JvmSuppressWildcards Any>): Map<String, @JvmSuppressWildcards Any>

    @DELETE("rest/v1/banners")
    suspend fun deleteBanner(@Query("id") filter: String): Map<String, @JvmSuppressWildcards String>

    @GET("rest/v1/catalog_products?featured=eq.true")
    suspend fun getFeaturedProducts(): List<Product>

    // Orders
    @POST("rest/v1/orders")
    suspend fun createOrder(
        @Header("Prefer") prefer: String = "return=representation",
        @Body orderData: Map<String, Any?>
    ): List<Map<String, Any>>

    @POST("rest/v1/order_items")
    suspend fun createOrderItem(
        @Header("Prefer") prefer: String = "return=representation",
        @Body itemData: Map<String, Any?>
    ): List<Map<String, Any>>

    @GET("rest/v1/orders?select=*,profiles!orders_user_id_fkey(full_name),order_items(*,products(name,images))")
    suspend fun getUserOrders(@Query("user_id") filter: String): List<Map<String, Any>>

    // Payment (These might need a separate Edge Function or external service)
    @POST("functions/v1/stk-push")
    suspend fun initiateStkPush(@Body data: Map<String, Any>): Map<String, Any>

    @POST("functions/v1/validate-inventory")
    suspend fun validateInventory(@Body data: Map<String, String>): Map<String, Any>

    @GET("functions/v1/payment-status")
    suspend fun checkPaymentStatus(@Query("checkoutId") checkoutId: String): Map<String, Any>

    // Paystack Integration
    @POST("functions/v1/paystack-initialize")
    suspend fun initializePaystack(@Body data: Map<String, Any>): Map<String, Any>

    @GET("functions/v1/paystack-verify")
    suspend fun verifyPaystack(@Query("reference") reference: String): Map<String, Any>

    @POST("functions/v1/send-receipt")
    suspend fun sendReceiptEmail(@Body data: Map<String, String>): Map<String, Any>

    // User Profile
    @GET("rest/v1/profiles?select=id")
    suspend fun checkEmailExists(@Query("email") filter: String): List<Map<String, Any>>

    @GET("rest/v1/profiles?select=*,wallets(balance,currency)")
    suspend fun getProfileByUserId(@Query("id") filter: String): List<Map<String, Any>>

    @GET("rest/v1/profiles")
    suspend fun getAllProfiles(): List<Map<String, Any>>

    @PATCH("rest/v1/profiles")
    suspend fun updateProfile(@Query("id") filter: String, @Body data: ProfileUpdateRequest): List<Map<String, Any>>

    @GET("rest/v1/profiles?status=eq.pending&role=eq.vendor")
    suspend fun getPendingVendors(): List<Map<String, Any>>
    
    @GET("rest/v1/profiles?or=(status.eq.pending,status.eq.pending_corrections)&role=eq.vendor")
    suspend fun getVendorApplications(): List<Map<String, Any>>

    @GET("rest/v1/profiles?role=eq.admin&select=email,phone_number")
    suspend fun getAdminContact(): List<Map<String, String>>

    // Messages
    @GET("rest/v1/messages?select=*")
    suspend fun getMessages(@Query("or") filter: String): List<Map<String, Any>>

    @POST("rest/v1/messages")
    suspend fun sendMessage(@Body messageData: Map<String, Any>): Map<String, Any>

    // Notifications
    @GET("rest/v1/notifications?select=*")
    suspend fun getNotifications(@Query("user_id") filter: String): List<Map<String, Any>>

    @POST("rest/v1/notifications")
    suspend fun createNotification(@Body notification: Map<String, Any>): Map<String, Any>

    @PATCH("rest/v1/notifications")
    suspend fun updateNotification(@Query("id") filter: String, @Body data: ProfileUpdateRequest): Map<String, Any>

    // Vendor Operations
    @GET("rest/v1/products")
    suspend fun getVendorProducts(@Query("vendor_id") filter: String): List<Product>

    @POST("rest/v1/products")
    @retrofit2.http.Headers("Prefer: return=representation")
    suspend fun addProduct(@Body product: com.example.nursewearconnect.model.ProductRequest): Product

    @PATCH("rest/v1/products")
    @retrofit2.http.Headers("Prefer: return=representation")
    suspend fun updateProduct(@Query("id") id: String, @Body product: com.example.nursewearconnect.model.ProductRequest): List<Product>

    @DELETE("rest/v1/products")
    suspend fun deleteProduct(@Query("id") id: String): Map<String, String>

    @GET("rest/v1/vendor_order_feed?select=*")
    suspend fun getVendorOrders(@Query("vendor_id") filter: String): List<Map<String, Any>>

    @PATCH("rest/v1/order_items")
    suspend fun updateOrderItemStatus(@Query("id") filter: String, @Body updates: Map<String, String>): Map<String, Any>

    @PATCH("rest/v1/orders")
    suspend fun updateOrderStatus(@Query("id") filter: String, @Body status: Map<String, String>): Map<String, Any>

    @POST("rest/v1/rpc/get_vendor_dashboard_stats")
    suspend fun getVendorDashboardStats(@Body params: Map<String, @JvmSuppressWildcards Any>): Map<String, Any>

    @POST("rest/v1/rpc/get_vendor_revenue_breakdown")
    suspend fun getVendorRevenueBreakdown(@Body params: Map<String, @JvmSuppressWildcards String>): List<Map<String, Any>>

    @POST("rest/v1/rpc/update_product_inventory_v2")
    suspend fun updateProductInventoryRpc(@Body params: Map<String, @JvmSuppressWildcards Any?>)

    // Logistics and Tracking
    @POST("rest/v1/rpc/update_order_tracking")
    suspend fun updateOrderTracking(@Body params: Map<String, @JvmSuppressWildcards Any?>): retrofit2.Response<Unit>

    // Payouts
    @GET("rest/v1/payouts?select=*,profiles:vendor_id(full_name,business_name)")
    suspend fun getPayouts(): List<Map<String, Any>>

    @POST("rest/v1/payouts")
    suspend fun createPayout(@Body payoutData: Map<String, Any>): Map<String, Any>

    @PATCH("rest/v1/payouts")
    suspend fun updatePayoutStatus(@Query("id") filter: String, @Body status: Map<String, String>): Map<String, Any>

    @POST("functions/v1/paystack-payout")
    suspend fun processPaystackPayout(@Body data: Map<String, String>): Map<String, Any>

    @POST("functions/v1/paystack-refund")
    suspend fun processPaystackRefund(@Body data: Map<String, @JvmSuppressWildcards Any>): Map<String, Any>

    @GET("rest/v1/orders?select=*,profiles!orders_user_id_fkey(full_name),order_items(*,products(name,vendor_id)),order_status_history(*)")
    suspend fun getAllOrders(
        @Query("status") status: String? = null,
        @Query("created_at") gteDate: String? = null,
        @Query("created_at") lteDate: String? = null,
        @Query("id") idSearch: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
        @Query("order") order: String = "created_at.desc"
    ): List<Map<String, Any>>

    @GET("rest/v1/v_admin_order_summary?select=*")
    suspend fun getAdminOrderSummary(
        @Query("status") status: String? = null,
        @Query("created_at") gteDate: String? = null,
        @Query("created_at") lteDate: String? = null,
        @Query("id") idSearch: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
        @Query("order") order: String = "created_at.desc"
    ): List<Map<String, Any>>

    @GET("rest/v1/order_status_history?select=*")
    suspend fun getOrderStatusHistory(@Query("order_id") orderId: String): List<Map<String, Any>>

    @GET("rest/v1/admin_detailed_sales_report?select=*")
    suspend fun getAdminDetailedSalesReport(): List<Map<String, Any>>

    @GET("rest/v1/system_logs?select=*,profiles!system_logs_user_id_fkey(full_name)")
    suspend fun getSystemLogs(
        @Query("action") action: String? = null,
        @Query("created_at") gteDate: String? = null,
        @Query("created_at") lteDate: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
        @Query("order") order: String = "created_at.desc"
    ): List<Map<String, Any>>

    @DELETE("rest/v1/system_logs")
    suspend fun clearSystemLogs(@Query("id") filter: String = "neq.0"): Map<String, String>

    @POST("rest/v1/password_resets")
    suspend fun createPasswordReset(
        @Body data: Map<String, Any>,
        @Header("Prefer") prefer: String = "return=representation"
    ): List<Map<String, Any>>

    @GET("rest/v1/password_resets")
    suspend fun getPasswordReset(
        @Query("email") email: String,
        @Query("code") code: String,
        @Query("is_used") isUsed: String = "eq.false",
        @Query("select") select: String = "*"
    ): List<Map<String, Any>>

    @PATCH("rest/v1/password_resets")
    suspend fun markPasswordResetUsed(
        @Query("email") email: String,
        @Query("code") code: String,
        @Body data: Map<String, Boolean> = mapOf("is_used" to true),
        @Header("Prefer") prefer: String = "return=representation"
    ): List<Map<String, Any>>

    @POST("rest/v1/rpc/reset_password_with_otp")
    suspend fun resetPasswordWithOtp(@Body data: Map<String, String>): Map<String, Any>

    @POST("rest/v1/system_logs")
    suspend fun logAction(
        @Body logData: Map<String, Any?>,
        @Header("Prefer") prefer: String = "return=minimal"
    )

    // Session Management
    @GET("rest/v1/user_sessions?select=*")
    suspend fun getActiveSessions(@Query("user_id") filter: String): List<Map<String, Any>>

    @DELETE("rest/v1/user_sessions")
    suspend fun revokeSession(@Query("id") filter: String): Map<String, String>

    @GET("rest/v1/catalog_products")
    suspend fun searchProducts(@Query("name") query: String): List<Product>

    // Product Reviews
    @GET("rest/v1/reviews?select=*,profiles!reviews_user_id_fkey(full_name)")
    suspend fun getProductReviews(@Query("product_id") filter: String): List<Map<String, Any>>

    @GET("rest/v1/reviews?select=*")
    suspend fun getUserReviews(@Query("user_id") filter: String): List<Map<String, Any>>

    @GET("rest/v1/addresses?select=*")
    suspend fun getUserAddresses(@Query("user_id") filter: String): List<Map<String, Any>>

    @POST("rest/v1/addresses")
    suspend fun addAddress(@Body addressData: Map<String, Any>): Map<String, Any>

    @PATCH("rest/v1/addresses")
    suspend fun updateAddress(@Query("id") idFilter: String, @Body addressData: Map<String, Any>): Map<String, Any>

    @DELETE("rest/v1/addresses")
    suspend fun deleteAddress(@Query("id") idFilter: String): Map<String, String>

    @GET("rest/v1/favorites?select=*")
    suspend fun getUserFavorites(@Query("user_id") filter: String): List<Map<String, Any>>

    @POST("rest/v1/favorites")
    suspend fun addFavorite(@Body data: Map<String, String>): Map<String, Any>

    @DELETE("rest/v1/favorites")
    suspend fun removeFavorite(@Query("user_id") userFilter: String, @Query("product_id") productFilter: String): retrofit2.Response<Unit>

    @POST("rest/v1/reviews")
    suspend fun addReview(@Body reviewData: Map<String, @JvmSuppressWildcards Any>): Map<String, @JvmSuppressWildcards Any>

    @POST("rest/v1/rpc/process_wallet_payment")
    suspend fun processWalletPayment(@Body params: Map<String, Any>): Map<String, Any>

    @POST("rest/v1/rpc/admin_delete_user")
    suspend fun adminDeleteUser(@Body params: Map<String, String>): Map<String, Any>

    @POST("rest/v1/profiles")
    suspend fun createProfile(@Body profile: Map<String, @JvmSuppressWildcards Any>): Map<String, Any>

    @DELETE("rest/v1/profiles")
    suspend fun deleteProfile(@Query("user_id") userId: String): Map<String, String>

    @POST("rest/v1/rpc/handle_user_logout")
    suspend fun handleUserLogout(@Body params: Map<String, String>)

    @POST("rest/v1/rpc/handle_user_login")
    suspend fun handleUserLogin(@Body params: Map<String, String>)

    @POST("rest/v1/rpc/get_live_users")
    suspend fun getLiveUsers(): List<Map<String, Any>>

    // Reports and Financials
    @POST("rest/v1/rpc/get_financial_summary")
    suspend fun getFinancialSummary(@Body params: Map<String, String>): List<Map<String, Any>>

    @POST("rest/v1/rpc/get_inventory_health")
    suspend fun getInventoryHealth(@Body params: Map<String, String?>): List<Map<String, Any>>

    @POST("rest/v1/rpc/get_demand_forecasting")
    suspend fun getDemandForecasting(@Body params: Map<String, String?>): List<Map<String, Any>>

    @POST("rest/v1/rpc/get_financial_summary_v2")
    suspend fun getFinancialSummaryV2(@Body params: Map<String, String?>): List<Map<String, Any>>

    @GET("rest/v1/v_inventory_health_by_category?select=*")
    suspend fun getInventoryHealthByCategory(): List<Map<String, Any>>

    @POST("rest/v1/rpc/get_active_flash_sales")
    suspend fun getActiveFlashSales(@Body params: Map<String, String> = emptyMap()): List<Map<String, Any>>

    @POST("rest/v1/rpc/get_admin_sales_trends")
    suspend fun getAdminSalesTrends(@Body params: Map<String, Int>): List<Map<String, Any>>

    @POST("rest/v1/rpc/get_admin_inventory_health")
    suspend fun getAdminInventoryHealth(@Body params: Map<String, String> = emptyMap()): Map<String, Any>

    @POST("rest/v1/rpc/visual_search_products")
    suspend fun visualSearchProducts(@Body params: Map<String, List<String>>): List<Product>

    @POST("rest/v1/rpc/join_product_waitlist")
    suspend fun joinWaitlist(@Body params: Map<String, String>): retrofit2.Response<Unit>

    // Subscriptions
    @POST("rest/v1/rpc/create_subscription")
    suspend fun createSubscription(@Body params: Map<String, @JvmSuppressWildcards Any?>): Map<String, Any>

    @GET("rest/v1/subscriptions?select=*,products(name,price_kes,images)")
    suspend fun getUserSubscriptions(@Query("user_id") userId: String): List<Map<String, Any>>

    @PATCH("rest/v1/subscriptions")
    suspend fun updateSubscription(@Query("id") id: String, @Body updates: Map<String, Any>): Map<String, Any>

    @POST("rest/v1/rpc/get_sales_trends")
    suspend fun getSalesTrends(@Body params: Map<String, String?>): List<Map<String, Any>>

    @POST("rest/v1/rpc/check_marketing_expirations")
    suspend fun checkMarketingExpirations(): Map<String, Any>

    @GET("rest/v1/coupon_performance?select=*")
    suspend fun getCouponPerformance(): List<Map<String, Any>>

    @GET("rest/v1/vendor_rankings?select=*")
    suspend fun getVendorRankings(): List<Map<String, Any>>

    @GET("rest/v1/stock_alerts?select=*,products(name)")
    suspend fun getStockAlerts(@Query("vendor_id") filter: String? = null): List<Map<String, Any>>

    @GET("rest/v1/v_vendor_performance?select=*")
    suspend fun getVendorPerformance(): List<Map<String, Any>>

    @POST("rest/v1/generated_reports")
    suspend fun logReportGeneration(@Body reportData: Map<String, Any>): Map<String, Any>

    @GET("rest/v1/generated_reports?select=*")
    suspend fun getGeneratedReports(): List<Map<String, Any>>

    // Loyalty and Rewards
    @GET("rest/v1/v_loyalty_history?select=*")
    suspend fun getLoyaltyHistory(@Query("user_id") filter: String): List<Map<String, Any>>

    @GET("rest/v1/loyalty_tiers?select=*")
    suspend fun getLoyaltyTiers(): List<Map<String, Any>>

    // Cart Sync
    @GET("rest/v1/cart_items?select=*")
    suspend fun getCartItems(@Query("user_id") userId: String): List<Map<String, Any>>

    @POST("rest/v1/rpc/sync_cart_items")
    suspend fun syncCartItems(@Body params: Map<String, @JvmSuppressWildcards Any>): retrofit2.Response<Unit>

    @POST("rest/v1/cart_items")
    suspend fun addCartItem(@Body item: Map<String, @JvmSuppressWildcards Any>): Map<String, Any>

    @PATCH("rest/v1/cart_items")
    suspend fun updateCartItem(@Query("id") filter: String, @Body item: Map<String, @JvmSuppressWildcards Any>): Map<String, Any>

    @DELETE("rest/v1/cart_items")
    suspend fun deleteCartItem(@Query("id") filter: String): retrofit2.Response<Unit>

    @DELETE("rest/v1/cart_items")
    suspend fun clearRemoteCart(@Query("user_id") filter: String): retrofit2.Response<Unit>

    // Returns and Refunds
    @POST("rest/v1/return_requests")
    suspend fun initiateReturn(@Body params: Map<String, @JvmSuppressWildcards Any>): retrofit2.Response<Unit>

    @GET("rest/v1/return_requests?select=*")
    suspend fun getReturnRequests(@Query("user_id") userId: String): List<Map<String, Any>>

    @POST("rest/v1/rpc/process_refund")
    suspend fun processRefund(@Body params: Map<String, @JvmSuppressWildcards Any>): retrofit2.Response<Unit>

    // Try Before You Buy (Home Fitting)
    @POST("rest/v1/rpc/request_fitting_service")
    suspend fun requestFittingService(@Body params: Map<String, @JvmSuppressWildcards Any>): retrofit2.Response<Unit>

    @GET("rest/v1/fitting_appointments?select=*,profiles!fitting_appointments_user_id_fkey(full_name),addresses(*)")
    suspend fun getFittingAppointments(@Query("user_id") userId: String): List<Map<String, Any>>

    // Bundles
    @GET("rest/v1/bundles?select=*,bundle_items(*,products(*))")
    suspend fun getActiveBundles(@Query("is_active") isActive: String = "eq.true"): List<com.example.nursewearconnect.model.Bundle>
}

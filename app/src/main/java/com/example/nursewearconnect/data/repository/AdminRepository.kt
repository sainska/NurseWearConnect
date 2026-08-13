package com.example.nursewearconnect.data.repository

import com.example.nursewearconnect.data.api.ApiService
import com.example.nursewearconnect.model.ProfileUpdateRequest
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.Flow

class AdminRepository(private val apiService: ApiService, private val supabaseClient: SupabaseClient) {
    
    fun getSystemLogsRealtime(): Flow<PostgresAction> {
        val channel = supabaseClient.channel("system_logs_realtime")
        return channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "system_logs"
        }
    }

    fun getOrdersRealtime(): Flow<PostgresAction> {
        val channel = supabaseClient.channel("admin_orders_realtime")
        return channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "orders"
        }
    }

    suspend fun getPendingVendors(): Result<List<Map<String, Any>>> {
        return try {
            Result.success(apiService.getVendorApplications())
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun getSalesTrends(days: Int = 30): Result<List<Map<String, Any>>> {
        return try {
            Result.success(apiService.getAdminSalesTrends(mapOf("p_days" to days)))
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun getInventoryHealth(): Result<Map<String, Any>> {
        return try {
            Result.success(apiService.getAdminInventoryHealth())
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun approveVendor(vendorId: String, adminId: String): Result<Unit> {
        return try {
            apiService.updateProfile("eq.$vendorId", ProfileUpdateRequest(status = "active"))
            logAction(adminId, "APPROVE_VENDOR", "Approved vendor profile: $vendorId")
            createNotification(
                vendorId,
                "Application Approved!",
                "Congratulations! Your vendor application has been approved. You can now start adding products to your shop.",
                "system"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun rejectVendor(vendorId: String, adminId: String, notes: String? = null): Result<Unit> {
        return try {
            val updateData = ProfileUpdateRequest(
                status = "rejected",
                statusNotes = notes
            )
            apiService.updateProfile("eq.$vendorId", updateData)
            logAction(adminId, "REJECT_VENDOR", "Rejected vendor profile: $vendorId. Notes: ${notes ?: "None"}")
            
            createNotification(
                vendorId,
                "Update on your Application",
                if (notes != null) "Your application needs corrections: $notes" else "Your vendor application has been rejected. Please contact support for more details.",
                "system"
            )
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun requestVendorCorrections(vendorId: String, adminId: String, notes: String): Result<Unit> {
        return try {
            val updateData = ProfileUpdateRequest(
                status = "pending_corrections",
                statusNotes = notes
            )
            apiService.updateProfile("eq.$vendorId", updateData)
            logAction(adminId, "VENDOR_CORRECTION_REQUESTED", "Requested corrections for vendor $vendorId. Notes: $notes")
            
            createNotification(
                vendorId,
                "Corrections Required: Your Vendor Application",
                "Your application needs updates before approval: $notes",
                "system"
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun createNotification(userId: String, title: String, body: String, category: String = "general"): Result<Unit> {
        return try {
            val notificationData = mapOf(
                "user_id" to userId,
                "title" to title,
                "body" to body,
                "category" to category,
                "priority_level" to "normal",
                "is_read" to false
            )
            apiService.createNotification(notification = notificationData)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun getAllOrders(
        status: String? = null,
        startDate: String? = null,
        endDate: String? = null,
        searchQuery: String? = null,
        limit: Int? = null,
        offset: Int? = null
    ): Result<List<Map<String, Any>>> {
        return try {
            val statusParam = status?.let { "eq.$it" }
            val gteDate = startDate?.let { "gte.$it" }
            val lteDate = endDate?.let { "lte.$it" }
            
            // Using optimized summary view for admin order list
            val orders = apiService.getAdminOrderSummary(
                status = statusParam,
                gteDate = gteDate,
                lteDate = lteDate,
                idSearch = searchQuery?.let { "ilike.*$it*" },
                limit = limit,
                offset = offset
            )
            Result.success(orders)
        } catch (e: Exception) {
            // Fallback to direct orders query if view fails
            try {
                Result.success(apiService.getAllOrders(
                    status = status?.let { "eq.$it" },
                    gteDate = startDate?.let { "gte.$it" },
                    lteDate = endDate?.let { "lte.$it" },
                    limit = limit,
                    offset = offset
                ))
            } catch (ex: Exception) {
                Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
            }
        }
    }

    suspend fun getSystemLogs(
        startDate: String? = null,
        endDate: String? = null,
        limit: Int? = null,
        offset: Int? = null
    ): Result<List<Map<String, Any>>> {
        return try {
            val gteDate = startDate?.let { "gte.$it" }
            val lteDate = endDate?.let { "lte.$it" }

            Result.success(apiService.getSystemLogs(
                gteDate = gteDate,
                lteDate = lteDate,
                limit = limit,
                offset = offset
            ))
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun clearSystemLogs(): Result<Unit> {
        return try {
            apiService.clearSystemLogs()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun logAction(userId: String, action: String, details: String, severity: String = "info"): Result<Unit> {
        return try {
            val logData = mapOf(
                "user_id" to userId,
                "action" to action,
                "details" to details,
                "severity" to severity
            )
            apiService.logAction(logData)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun getAllUsers(): Result<List<Map<String, Any>>> {
        return try {
            Result.success(apiService.getAllProfiles())
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun getLiveUsers(): Result<List<Map<String, Any>>> {
        return try {
            Result.success(apiService.getLiveUsers())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createUser(profile: Map<String, Any>): Result<Unit> {
        return try {
            apiService.createProfile(profile)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteUser(userId: String): Result<Unit> {
        return try {
            apiService.adminDeleteUser(mapOf("target_user_id" to userId))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserStatus(userId: String, status: String, adminId: String): Result<Unit> {
        return try {
            apiService.updateProfile("eq.$userId", com.example.nursewearconnect.model.ProfileUpdateRequest(status = status))
            logAction(adminId, "USER_STATUS_CHANGE", "User $userId status set to $status")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Payouts Management
    suspend fun getPayouts(): Result<List<Map<String, Any>>> {
        return try {
            Result.success(apiService.getPayouts())
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun createPayout(vendorId: String, amount: Int, adminId: String, mpesaNumber: String? = null): Result<Unit> {
        return try {
            val payoutData = mutableMapOf<String, Any>(
                "vendor_id" to vendorId,
                "amount" to amount,
                "status" to "pending"
            )
            mpesaNumber?.let { payoutData["account_number"] = it } // Using account_number as a generic field
            apiService.createPayout(payoutData)
            logAction(adminId, "CREATE_PAYOUT", "Created payout for vendor $vendorId: KSh $amount to $mpesaNumber")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun updatePayoutStatus(payoutId: String, status: String, reference: String?, adminId: String): Result<Unit> {
        return try {
            val updateData = mutableMapOf("status" to status)
            reference?.let { updateData["reference_number"] = it }
            if (status == "paid") {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                updateData["processed_at"] = sdf.format(java.util.Date())
            }
            apiService.updatePayoutStatus("eq.$payoutId", updateData)
            logAction(adminId, "UPDATE_PAYOUT", "Updated payout $payoutId to $status. Ref: $reference")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun processPaystackPayout(payoutId: String, adminId: String): Result<Unit> {
        return try {
            apiService.processPaystackPayout(mapOf("payoutId" to payoutId))
            logAction(adminId, "PROCESS_PAYSTACK_PAYOUT", "Triggered real Paystack transfer for payout $payoutId")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun processPaystackRefund(orderId: String, amount: Double?, reason: String, adminId: String): Result<Unit> {
        return try {
            val params = mutableMapOf<String, Any>(
                "orderId" to orderId,
                "reason" to reason
            )
            amount?.let { params["amount"] = it }
            apiService.processPaystackRefund(params)
            logAction(adminId, "PROCESS_PAYSTACK_REFUND", "Triggered Paystack refund for order $orderId")
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun getDetailedSalesReport(): Result<List<Map<String, Any>>> {
        return try {
            Result.success(apiService.getAdminDetailedSalesReport())
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    // Reporting and Financial Summary
    suspend fun getFinancialSummary(startDate: String, endDate: String): Result<Map<String, Any>> {
        return try {
            val params = mapOf(
                "p_start_date" to startDate,
                "p_end_date" to endDate
            )
            val results = apiService.getFinancialSummary(params)
            if (results.isNotEmpty()) {
                Result.success(results.first())
            } else {
                Result.failure(Exception("No summary data found for the selected period."))
            }
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun logReportGeneration(
        reportType: String,
        format: String,
        adminId: String,
        startDate: String? = null,
        endDate: String? = null
    ): Result<Unit> {
        return try {
            val reportData = mutableMapOf<String, Any>(
                "report_type" to reportType,
                "format" to format,
                "generated_by" to adminId
            )
            startDate?.let { reportData["start_date"] = it }
            endDate?.let { reportData["end_date"] = it }

            apiService.logReportGeneration(reportData)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun getGeneratedReports(): Result<List<Map<String, Any>>> {
        return try {
            Result.success(apiService.getGeneratedReports())
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun getInventoryHealth(vendorId: String? = null): Result<Map<String, Any>> {
        return try {
            val params = mapOf("p_vendor_id" to vendorId)
            val result = apiService.getInventoryHealth(params)
            if (result.isNotEmpty()) Result.success(result.first())
            else Result.failure(Exception("No inventory data found"))
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun getDemandForecasting(vendorId: String? = null): Result<List<Map<String, Any>>> {
        return try {
            val params = mapOf("p_vendor_id" to vendorId)
            Result.success(apiService.getDemandForecasting(params))
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun getFinancialSummaryV2(startDate: String? = null, endDate: String? = null): Result<Map<String, Any>> {
        return try {
            val params = mapOf("p_start_date" to startDate, "p_end_date" to endDate)
            val result = apiService.getFinancialSummaryV2(params)
            if (result.isNotEmpty()) Result.success(result.first())
            else Result.failure(Exception("No financial summary data found"))
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun getInventoryHealthByCategory(): Result<List<Map<String, Any>>> {
        return try {
            Result.success(apiService.getInventoryHealthByCategory())
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun getSalesTrends(
        startDate: String? = null,
        endDate: String? = null,
        interval: String = "day",
        vendorId: String? = null
    ): Result<List<Map<String, Any>>> {
        return try {
            val params = mutableMapOf<String, String?>(
                "p_start_date" to startDate,
                "p_end_date" to endDate,
                "p_interval" to interval,
                "p_vendor_id" to vendorId
            )
            Result.success(apiService.getSalesTrends(params))
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun getCouponPerformance(): Result<List<Map<String, Any>>> {
        return try {
            Result.success(apiService.getCouponPerformance())
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun getVendorRankings(): Result<List<Map<String, Any>>> {
        return try {
            Result.success(apiService.getVendorRankings())
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun bulkUpdateOrderStatus(orderIds: List<String>, status: String, adminId: String): Result<Unit> {
        return try {
            val filter = "in.(${orderIds.joinToString(",")})"
            apiService.updateOrderStatus(filter, mapOf("status" to status))
            orderIds.forEach { id ->
                logAction(adminId, "BULK_ORDER_UPDATE", "Order $id status updated to $status", "info")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStockAlerts(vendorId: String? = null): Result<List<Map<String, Any>>> {
        return try {
            Result.success(apiService.getStockAlerts(vendorId?.let { "eq.$it" }))
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    suspend fun getVendorPerformance(): Result<List<Map<String, Any>>> {
        return try {
            Result.success(apiService.getVendorPerformance())
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }

    /**
     * Uploads a generated report file to Supabase Storage and records it in the database.
     */
    suspend fun saveGeneratedReport(
        reportType: String,
        format: String,
        file: java.io.File,
        adminId: String,
        metadata: Map<String, Any> = emptyMap()
    ): Result<String> {
        return try {
            val fileName = "reports/${reportType.lowercase()}_${System.currentTimeMillis()}.${format.lowercase()}"
            
            // 1. Upload to Storage
            val bucket = supabaseClient.storage["deployments"] // Reusing deployments or a new 'reports' bucket
            bucket.upload(fileName, file.readBytes())
            val fileUrl = bucket.publicUrl(fileName)

            // 2. Insert Record
            val reportData = mutableMapOf<String, Any>(
                "report_type" to reportType,
                "format" to format,
                "file_url" to fileUrl,
                "generated_by" to adminId,
                "metadata" to metadata
            )
            // Add verification code if present in metadata
            metadata["verification_code"]?.let { reportData["verification_code"] = it }
            
            apiService.logReportGeneration(reportData)
            
            Result.success(fileUrl)
        } catch (e: Exception) {
            Result.failure(Exception(com.example.nursewearconnect.utils.AppUtils.mapThrowable(e)))
        }
    }
}

package com.example.nursewearconnect.data.repository

import com.example.nursewearconnect.data.api.ApiService
import com.example.nursewearconnect.utils.AppUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

enum class TransactionType {
    ORDER, WALLET_TOPUP, SUBSCRIPTION, FITTING_SERVICE
}

sealed class PaymentStatus {
    object Idle : PaymentStatus()
    object Loading : PaymentStatus()
    data class Success(val checkoutId: String) : PaymentStatus()
    data class PaystackInitialized(val authorizationUrl: String, val reference: String, val isStk: Boolean = false) : PaymentStatus()
    data class Completed(val transactionId: String) : PaymentStatus()
    data class Error(val message: String, val type: TransactionType = TransactionType.ORDER) : PaymentStatus()
}

class PaymentRepository(private val apiService: ApiService) {
    private val _paymentState = MutableStateFlow<PaymentStatus>(PaymentStatus.Idle)
    val paymentState: StateFlow<PaymentStatus> = _paymentState.asStateFlow()

    suspend fun initiateMpesaPayment(
        orderId: String?,
        phoneNumber: String,
        amount: Double,
        email: String? = null,
        userId: String? = null,
        checkoutPayload: Map<String, Any>? = null
    ): PaymentStatus {
        _paymentState.value = PaymentStatus.Loading
        return try {
            // Step 1: Validate Inventory (Optional if we do it later, but good for UX)
            if (orderId != null && orderId.length > 10) {
                val validation = apiService.validateInventory(mapOf("orderId" to orderId))
                if (validation["valid"] == false) {
                    val errorMsg = validation["message"]?.toString() ?: "Inventory validation failed."
                    val result = PaymentStatus.Error(errorMsg, TransactionType.ORDER)
                    _paymentState.value = result
                    return result
                }
            }

            // Step 2: Proceed to STK Push (Direct via Paystack Charge API)
            val formattedPhone = AppUtils.formatMpesaPhoneNumber(phoneNumber)
            val paymentData = mutableMapOf<String, Any>(
                "phoneNumber" to formattedPhone,
                "amount" to amount,
                "email" to (email ?: "customer@nursewearconnect.com"),
                "userId" to (userId ?: ""),
                "type" to "order_payment"
            )
            orderId?.let { paymentData["orderId"] = it }
            checkoutPayload?.let { paymentData.putAll(it) }

            android.util.Log.d("PaymentRepository", "Initiating STK Push")
            val response = apiService.initiateStkPush(paymentData)
            val checkoutId = response["CheckoutRequestID"] as? String ?: ""

            if (checkoutId.isNotEmpty()) {
                val result = PaymentStatus.Success(checkoutId)
                _paymentState.value = result
                result
            } else {
                val result = PaymentStatus.Error("Failed to initiate STK Push: No reference returned", TransactionType.ORDER)
                _paymentState.value = result
                result
            }
        } catch (e: Exception) {
            val result = PaymentStatus.Error(AppUtils.mapThrowable(e), TransactionType.ORDER)
            _paymentState.value = result
            result
        }
    }

    suspend fun initiateWalletTopUp(userId: String, phoneNumber: String, amount: Double, email: String? = null): PaymentStatus {
        _paymentState.value = PaymentStatus.Loading
        return try {
            val formattedPhone = AppUtils.formatMpesaPhoneNumber(phoneNumber)
            val paymentData = mutableMapOf<String, Any>(
                "userId" to userId,
                "orderId" to "topup_${System.currentTimeMillis()}", // unique id for trace
                "phoneNumber" to formattedPhone,
                "amount" to amount,
                "type" to "wallet_topup",
                "email" to (email ?: "customer@nursewearconnect.com")
            )

            android.util.Log.d("PaymentRepository", "Initiating Wallet Top-up STK for user $userId")
            val response = apiService.initiateStkPush(paymentData)
            val checkoutId = response["CheckoutRequestID"] as? String ?: ""

            if (checkoutId.isNotEmpty()) {
                val result = PaymentStatus.Success(checkoutId)
                _paymentState.value = result
                
                // Start polling for Wallet Top-up status
                CoroutineScope(Dispatchers.IO).launch {
                    pollPaymentStatus(checkoutId, type = TransactionType.WALLET_TOPUP)
                }

                result
            }
 else {
                val result = PaymentStatus.Error("Failed to initiate STK Push", TransactionType.WALLET_TOPUP)
                _paymentState.value = result
                result
            }
        } catch (e: Exception) {
            val result = PaymentStatus.Error(AppUtils.mapThrowable(e), TransactionType.WALLET_TOPUP)
            _paymentState.value = result
            result
        }
    }

    suspend fun initializePaystackWalletTopUp(userId: String, email: String, amount: Double): PaymentStatus {
        _paymentState.value = PaymentStatus.Loading
        return try {
            val data = mapOf(
                "userId" to userId,
                "email" to email,
                "amount" to amount,
                "type" to "wallet_topup",
                "payment_method" to "card"
            )
            
            val response = apiService.initializePaystack(data)
            val authUrl = response["authorization_url"] as? String ?: ""
            val reference = response["reference"] as? String ?: ""

            if (authUrl.isNotEmpty()) {
                val result = PaymentStatus.PaystackInitialized(authUrl, reference, isStk = false)
                _paymentState.value = result
                result
            } else {
                val result = PaymentStatus.Error("Failed to initialize Paystack", TransactionType.WALLET_TOPUP)
                _paymentState.value = result
                result
            }
        } catch (e: Exception) {
            val result = PaymentStatus.Error(AppUtils.mapThrowable(e), TransactionType.WALLET_TOPUP)
            _paymentState.value = result
            result
        }
    }

    suspend fun checkStatus(checkoutId: String): Map<String, Any> {
        return try {
            val status = apiService.checkPaymentStatus(checkoutId)
            val resultCode = status["ResultCode"]?.toString()
            if (resultCode == "0") {
                _paymentState.value = PaymentStatus.Completed(status["MpesaReceiptNumber"]?.toString() ?: "TRANS_OK")
            } else if (resultCode != null && resultCode != "PENDING" && resultCode != "1032") {
                _paymentState.value = PaymentStatus.Error(status["ResultDesc"]?.toString() ?: "Payment Failed", TransactionType.ORDER)
            } else if (resultCode == "1032") {
                _paymentState.value = PaymentStatus.Error("Transaction cancelled by user.", TransactionType.ORDER)
            }
            status
        } catch (e: Exception) {
            mapOf("status" to "ERROR", "message" to (e.message ?: "Failed to check status"))
        }
    }

    suspend fun pollPaymentStatus(checkoutId: String, maxAttempts: Int = 12, initialDelay: Long = 5000, type: TransactionType = TransactionType.ORDER) {
        var attempts = 0
        while (attempts < maxAttempts) {
            val status = checkStatus(checkoutId)
            val resultCode = status["ResultCode"]?.toString()

            if (resultCode != null && resultCode != "PENDING") {
                return 
            }

            attempts++
            if (attempts < maxAttempts) {
                delay(initialDelay)
            }
        }

        // Timeout case
        if (_paymentState.value is PaymentStatus.Success) {
            _paymentState.value = PaymentStatus.Error("Payment verification timed out. If you were charged, please contact support with your M-Pesa message.", type)
        }
    }

    suspend fun initializePaystackPayment(
        orderId: String?,
        email: String,
        amount: Double,
        userId: String? = null,
        checkoutPayload: Map<String, Any>? = null
    ): PaymentStatus {
        _paymentState.value = PaymentStatus.Loading
        return try {
            // Inventory Validation
            if (orderId != null && orderId.length > 10) {
                val validation = apiService.validateInventory(mapOf("orderId" to orderId))
                if (validation["valid"] == false) {
                    val errorMsg = validation["message"]?.toString() ?: "Inventory validation failed."
                    val result = PaymentStatus.Error(errorMsg, TransactionType.ORDER)
                    _paymentState.value = result
                    return result
                }
            }

            val data = mutableMapOf<String, Any>(
                "email" to email,
                "amount" to amount,
                "type" to "order_payment"
            )
            orderId?.let { data["orderId"] = it }
            userId?.let { data["userId"] = it }
            checkoutPayload?.let { data.putAll(it) }
            
            android.util.Log.d("PaymentRepository", "Initiating Paystack API call")
            val response = apiService.initializePaystack(data)
            val authUrl = response["authorization_url"] as? String ?: ""
            val reference = response["reference"] as? String ?: ""

            if (authUrl.isNotEmpty() && reference.isNotEmpty()) {
                android.util.Log.i("PaymentRepository", "Paystack initialized successfully: $reference")
                val result = PaymentStatus.PaystackInitialized(authUrl, reference, isStk = false)
                _paymentState.value = result
                result
            } else {
                android.util.Log.e("PaymentRepository", "Paystack response missing URL or reference: $response")
                val result = PaymentStatus.Error("Failed to initialize Paystack: Invalid provider response", TransactionType.ORDER)
                _paymentState.value = result
                result
            }
        } catch (e: Exception) {
            android.util.Log.e("PaymentRepository", "Exception in Paystack initialization: ${e.message}")
            val result = PaymentStatus.Error(AppUtils.mapThrowable(e), TransactionType.ORDER)
            _paymentState.value = result
            result
        }
    }

    suspend fun verifyPaystackPayment(reference: String): PaymentStatus {
        _paymentState.value = PaymentStatus.Loading
        return try {
            val response = apiService.verifyPaystack(reference)
            val status = response["status"] as? String ?: "failed"
            val gatewayResponse = response["gateway_response"] as? String ?: ""

            if (status == "success") {
                val result = PaymentStatus.Completed(reference)
                _paymentState.value = result
                
                try {
                    apiService.logAction(mapOf(
                        "action" to "PAYMENT_VERIFIED_PAYSTACK",
                        "details" to "Paystack payment verified for reference $reference",
                        "severity" to "info"
                    ))
                } catch (e: Exception) { }
                
                result
            } else {
                val result = PaymentStatus.Error("Payment verification failed: $gatewayResponse", TransactionType.ORDER)
                _paymentState.value = result
                result
            }
        } catch (e: Exception) {
            val result = PaymentStatus.Error(AppUtils.mapThrowable(e), TransactionType.ORDER)
            _paymentState.value = result
            result
        }
    }

    suspend fun pollPaystackStatus(reference: String, maxAttempts: Int = 15, initialDelay: Long = 5000, type: TransactionType = TransactionType.ORDER) {
        var attempts = 0
        while (attempts < maxAttempts) {
            // We use a silent verification here to avoid UI flickering between Error and Loading
            try {
                val response = apiService.verifyPaystack(reference)
                if (response["status"] == "success") {
                    _paymentState.value = PaymentStatus.Completed(reference)
                    return 
                }
            } catch (e: Exception) { }

            attempts++
            if (attempts < maxAttempts) {
                delay(initialDelay)
            }
        }

        if (_paymentState.value !is PaymentStatus.Completed) {
            _paymentState.value = PaymentStatus.Error("Payment verification timed out. If you were charged, please contact support with your reference: $reference", type)
        }
    }

    suspend fun processWalletPayment(orderId: String, userId: String, amount: Double): PaymentStatus {
        _paymentState.value = PaymentStatus.Loading
        return try {
            val params = mapOf(
                "p_order_id" to orderId,
                "p_user_id" to userId,
                "p_amount" to amount
            )
            val response = apiService.processWalletPayment(params)
            val success = response["success"] as? Boolean ?: false
            val message = response["message"]?.toString() ?: "Unknown error"

            if (success) {
                val transactionId = response["transaction_id"]?.toString() ?: orderId
                val result = PaymentStatus.Completed(transactionId)
                _paymentState.value = result
                result
            } else {
                val result = PaymentStatus.Error(message, TransactionType.ORDER)
                _paymentState.value = result
                result
            }
        } catch (e: Exception) {
            val result = PaymentStatus.Error(AppUtils.mapThrowable(e), TransactionType.ORDER)
            _paymentState.value = result
            result
        }
    }
}

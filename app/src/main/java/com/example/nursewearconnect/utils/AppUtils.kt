package com.example.nursewearconnect.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.nursewearconnect.ui.theme.*
import java.io.ByteArrayOutputStream
import java.text.NumberFormat
import java.util.*
import java.util.regex.Pattern

object AppUtils {

    /**
     * Currency Configuration (Item 38)
     */
    enum class Currency(val code: String, val symbol: String, val rateToKes: Double) {
        KES("KES", "KSh", 1.0),
        USD("USD", "$", 0.0077), // 1 / 130
        EUR("EUR", "€", 0.0071),
        UGX("UGX", "USh", 28.5),
        TZS("TZS", "TSh", 19.2)
    }

    /**
     * Formats amount based on selected currency.
     */
    fun formatCurrency(amount: Double?, currency: Currency = Currency.KES, customRates: Map<String, Double> = emptyMap()): String {
        val finalAmount = amount ?: 0.0
        val rate = customRates[currency.code] ?: currency.rateToKes
        val converted = finalAmount * rate
        
        return when (currency) {
            Currency.KES -> {
                val formatted = NumberFormat.getNumberInstance(Locale.US).apply {
                    minimumFractionDigits = 0 // Kes usually shown without decimals for clean UI
                    maximumFractionDigits = 0
                }.format(converted)
                "KSh $formatted" // Use KSh as requested
            }
            else -> {
                val format = NumberFormat.getCurrencyInstance(Locale.US)
                val currencyInstance = java.util.Currency.getInstance(currency.code)
                format.currency = currencyInstance
                format.format(converted)
            }
        }
    }

    /**
     * Currency: formatKES(amount) → "KES 3,500.00"
     * Legacy support, calls formatCurrency.
     */
    fun formatKES(amount: Double?): String = formatCurrency(amount, Currency.KES)

    /**
     * toKES(usd) → Convert USD to KES (multiply by ~130)
     */
    fun toKES(usd: Double): Double {
        return usd * 130.0
    }

    /**
     * calcCart(items) → Calculate {subtotal, shipping, vat(16%), discount, total}
     * Assuming price is in KES already.
     */
    data class CartTotals(
        val subtotal: Double,
        val shipping: Double,
        val vat: Double,
        val discount: Double,
        val total: Double
    )

    fun calcCart(items: List<Pair<Double, Int>>, discountAmount: Double = 0.0): CartTotals {
        val subtotal = items.sumOf { it.first * it.second }
        // Free shipping over 5000 KES as per guide logic
        val shipping = if (subtotal > 5000 || subtotal == 0.0) 0.0 else 400.0
        val vat = subtotal * 0.16
        val total = subtotal + shipping + vat - discountAmount
        return CartTotals(subtotal, shipping, vat, discountAmount, total)
    }

    /**
     * Validation: isEmail(str)
     */
    fun isEmail(email: String?): Boolean {
        if (email.isNullOrBlank()) return false
        val emailPattern = "^[A-Za-z0-9+_.-]+@(.+)$"
        return Pattern.compile(emailPattern).matcher(email).matches()
    }

    /**
     * Normalizes Kenyan phone numbers to 254XXXXXXXXX format for M-Pesa.
     * Handles 07..., 01..., +254..., and 254... formats.
     */
    fun formatMpesaPhoneNumber(phone: String): String {
        val clean = phone.replace("+", "").replace(" ", "").replace("-", "")
        return when {
            clean.startsWith("0") -> "254" + clean.substring(1)
            clean.startsWith("254") -> clean
            clean.length == 9 -> "254" + clean
            else -> clean
        }
    }

    /**
     * isKEPhone(str) → validates +254 or 0 prefix with 9 digits
     */
    fun isKEPhone(phone: String?): Boolean {
        if (phone.isNullOrBlank()) return false
        // Matches +254 followed by 9 digits starting with 7 or 1, or 0 followed by 9 digits starting with 7 or 1
        val kePattern = "^(?:\\+254|0)[17]\\d{8}$"
        return Pattern.compile(kePattern).matcher(phone).matches()
    }

    /**
     * passwordStrength(pw) → 0-4 score
     */
    fun passwordStrength(password: String?): Int {
        if (password == null || password.length < 8) return 0
        var score = 0
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isLowerCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++
        return score
    }

    /**
     * Sanitization: strip HTML tags
     */
    fun sanitize(str: String?): String {
        if (str == null) return ""
        return str.replace(Regex("<[^>]*>"), "")
    }

    /**
     * truncate(str, maxLength) → add ellipsis
     */
    fun truncate(str: String?, maxLength: Int): String {
        if (str == null) return ""
        if (str.length <= maxLength) return str
        return str.substring(0, maxLength).trim() + "..."
    }

    /**
     * Date formatting: timeAgo(date)
     */
    fun timeAgo(timeInMillis: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timeInMillis
        
        return when {
            diff < 60000 -> "Just now"
            diff < 3600000 -> "${diff / 60000}m ago"
            diff < 86400000 -> "${diff / 3600000}h ago"
            else -> "${diff / 86400000}d ago"
        }
    }

    /**
     * formatCountdown(isoString) -> returns "HH:MM:SS" or null if expired
     */
    fun formatCountdown(isoString: String?): String? {
        if (isoString.isNullOrBlank()) return null
        return try {
            // Using SimpleDateFormat for better compatibility across Android versions
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val date = sdf.parse(isoString.split("+")[0].split(".")[0]) ?: return null
            val expiry = date.time
            val now = System.currentTimeMillis()
            val diff = expiry - now
            
            if (diff <= 0) return null
            
            val hours = diff / 3600000
            val minutes = (diff % 3600000) / 60000
            val seconds = (diff % 60000) / 1000
            
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } catch (e: Exception) {
            android.util.Log.e("AppUtils", "Error parsing countdown date: $isoString", e)
            null
        }
    }

    /**
     * timeAgo(isoString) -> converts Supabase/ISO timestamp to relative string
     */
    fun timeAgo(isoString: String?): String {
        if (isoString.isNullOrBlank()) return "Never"
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            // Parse base part of ISO string
            val basePart = isoString.split("+")[0].split(".")[0]
            val date = sdf.parse(basePart) ?: return isoString.split("T").firstOrNull() ?: "Unknown"
            timeAgo(date.time)
        } catch (e: Exception) {
            try {
                // Fallback for simple date strings
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                val date = sdf.parse(isoString)
                if (date != null) timeAgo(date.time) else isoString.split("T").firstOrNull() ?: "Unknown"
            } catch (e2: Exception) {
                isoString.split("T").firstOrNull() ?: "Unknown"
            }
        }
    }

    /**
     * Checks if a Supabase timestamp string is within a given date range.
     */
    fun isDateInRange(dateStr: String?, start: Long?, end: Long?): Boolean {
        if (dateStr == null) return false
        if (start == null && end == null) return true
        return try {
            // Supabase format: 2023-10-27T10:00:00+00:00
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val date = sdf.parse(dateStr.split("T")[0])?.time ?: return true
            
            // Normalize start and end to start of day if they aren't already
            // but usually DateRangePicker gives start of day UTC or local.
            val startMatch = start == null || date >= start
            val endMatch = end == null || date <= end
            startMatch && endMatch
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Error Mapping: Converts technical exceptions to user-friendly messages.
     * This method is designed to be idempotent; if it receives an already mapped
     * friendly message, it will return it as is.
     */
    fun formatDateShort(dateString: String?): String {
        if (dateString.isNullOrEmpty()) return ""
        return try {
            val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val date = inputFormat.parse(dateString)
            val outputFormat = java.text.SimpleDateFormat("MMM dd", java.util.Locale.US)
            if (date != null) outputFormat.format(date) else ""
        } catch (e: Exception) {
            dateString.takeLast(5)
        }
    }

    fun exportAndShareData(context: android.content.Context, data: String, fileName: String) {
        try {
            val exportDir = java.io.File(context.getExternalFilesDir(null), "Exports")
            if (!exportDir.exists()) exportDir.mkdirs()
            
            val file = java.io.File(exportDir, fileName)
            file.writeText(data)
            
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = if (fileName.endsWith(".csv")) "text/csv" else "application/pdf"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share $fileName"))
        } catch (e: Exception) {
            android.util.Log.e("AppUtils", "Export error: ${e.message}")
        }
    }

    fun mapThrowable(t: Throwable): String {
        val originalMessage = t.message ?: ""
        val message = originalMessage.lowercase()
        
        // Log the actual error for debugging
        android.util.Log.e("AppUtils", "Mapping exception: ${t.javaClass.simpleName} - $originalMessage", t)

        // 0. Idempotency check: If the message is already one of our mapped friendly messages, return it.
        if (originalMessage.contains("Please check your internet connection") || 
            originalMessage.contains("No internet connection") ||
            originalMessage.contains("Server is currently unreachable")) {
            return originalMessage
        }

        return when {
            message.contains("invalid_credentials") -> 
                "Invalid email or password. Please try again."
            message.contains("email_not_confirmed") -> 
                "Please confirm your email address before logging in."
            message.contains("user_already_exists") -> 
                "An account with this email already exists."
            
            // 1. Specific Network and Connectivity Issues
            t is java.net.UnknownHostException || message.contains("unable to resolve host") ->
                "No internet connection. Please check your mobile data or Wi-Fi and try again."
            
            t is java.net.ConnectException || message.contains("failed to connect") ||
            (message.contains("connect") && !message.contains("nursewearconnect")) -> 
                "Server is currently unreachable. Please ensure you have an active internet connection."
            
            t is java.net.SocketTimeoutException || message.contains("timeout") ->
                "Connection timed out. Your internet might be slow. Please try again in a moment."

            message.contains("cleartext") ->
                "Security error: Cleartext connection not allowed. Please use a secure network."
            
            message.contains("rate_limit") || message.contains("429") ->
                "Too many attempts. Please wait a moment and try again later."
            
            message.contains("insufficient_funds") ->
                "Transaction failed: Your wallet balance is insufficient."
            
            // 2. Session/OTP Expiry
            message.contains("otp") && (message.contains("expired") || message.contains("invalid")) ->
                if (message.contains("invalid")) "Invalid recovery code. Please check and try again."
                else "The recovery code has expired. Please request a new one."
            
            message.contains("invalid") && (message.contains("otp") || message.contains("token")) ->
                "Invalid recovery code. Please check and try again."

            message.contains("session") && message.contains("expired") ->
                "Your session has expired. Please log in again to continue."
            
            message.contains("forbidden") || message.contains("403") ->
                "Access denied. You don't have permission to perform this action."
            
            message.contains("not_found") || message.contains("404") ->
                "Requested resource not found. Please refresh and try again."
            
            message.contains("review_eligibility") || message.contains("only delivered orders") ->
                "Reviews are only allowed for products you have successfully received."
            
            message.contains("recursion") ->
                "The system is experiencing high load. Please try again in a few seconds."
                
            message.contains("price_at_purchase") || message.contains("null value") ->
                "Data synchronization error. Please restart the app or contact support."

            // 3. General Fallback
            originalMessage.isNotEmpty() && !message.contains("{") && !message.contains("exception") && 
            !message.contains("http") && !message.contains("supabase") && !message.contains("ktor") &&
            !message.contains("column") && !message.contains("table") ->
                originalMessage

            else -> "A connection error occurred. Please check your internet and try again."
        }
    }

    /**
     * Supabase Image Transformation: Appends width/height parameters for edge-side resizing.
     * Example: https://.../image.jpg -> https://.../image.jpg?width=300&height=300&resize=cover
     */
    fun getOptimizedImageUrl(url: String?, width: Int = 500, height: Int = 500): String {
        if (url.isNullOrBlank()) return ""
        // Skip if not a Supabase storage URL or if parameters already exist
        if (!url.contains("supabase.co/storage/v1/object/public/") || url.contains("?")) return url
        
        return "$url?width=$width&height=$height&resize=cover"
    }

    /**
     * Image Optimization: Compress and resize image before upload
     */
    fun optimizeImage(bytes: ByteArray, maxWidth: Int = 1024, maxHeight: Int = 1024, quality: Int = 80): ByteArray {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

        // Calculate sample size
        options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight)
        options.inJustDecodeBounds = false

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return bytes
        
        // Final resize if needed to match exact maxWidth/maxHeight constraints
        val resizedBitmap = if (bitmap.width > maxWidth || bitmap.height > maxHeight) {
            val scale = Math.min(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height)
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return outputStream.toByteArray()
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Order status: colors and progress
     */
    val ORDER_STATUS_COLORS = mapOf(
        "Pending" to Color(0xFFF59E0B), // Amber
        "Processing" to Color(0xFF3B82F6), // Blue
        "In Transit" to Color(0xFF8B5CF6), // Purple
        "Delivered" to Color(0xFF10B981), // Emerald
        "Cancelled" to Color(0xFFEF4444)  // Red
    )

    val ORDER_PROGRESS = mapOf(
        "Pending" to 0.1f,
        "Processing" to 0.4f,
        "In Transit" to 0.7f,
        "Delivered" to 1.0f,
        "Cancelled" to 0.0f
    )

    /**
     * Standard colors for OutlinedTextField to ensure visibility across the app.
     * Updated for full Dark Mode / Accessibility (Item 44).
     */
    @Composable
    fun standardOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        errorBorderColor = MaterialTheme.colorScheme.error,
        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    /**
     * Standard colors for TextField to ensure visibility across the app.
     * Updated for full Dark Mode / Accessibility (Item 44).
     */
    @Composable
    fun standardTextFieldColors() = TextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
        unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
        errorIndicatorColor = MaterialTheme.colorScheme.error,
        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    /**
     * Shows a system notification.
     */
    fun showSystemNotification(context: android.content.Context, title: String, body: String) {
        val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "in_app_realtime_channel"
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(channelId, "Realtime Updates", android.app.NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }
        
        val intent = android.content.Intent(context, com.example.nursewearconnect.MainActivity::class.java).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 0, intent,
            android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.example.nursewearconnect.R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setColor(0xFF0D9488.toInt())
            .build()
            
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    /**
     * PDF Printing: Triggers system print manager for a given PDF file
     */
    fun printPdf(context: android.content.Context, file: java.io.File, jobName: String) {
        val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as android.print.PrintManager
        val printAdapter = object : android.print.PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: android.print.PrintAttributes?,
                newAttributes: android.print.PrintAttributes?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: android.os.Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = android.print.PrintDocumentInfo.Builder(jobName)
                    .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out android.print.PageRange>?,
                destination: android.os.ParcelFileDescriptor?,
                cancellationSignal: android.os.CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                try {
                    val input = java.io.FileInputStream(file)
                    val output = java.io.FileOutputStream(destination?.fileDescriptor)
                    val buf = ByteArray(1024)
                    var bytesRead: Int
                    while (input.read(buf).also { bytesRead = it } > 0) {
                        output.write(buf, 0, bytesRead)
                    }
                    callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                    input.close()
                    output.close()
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.message)
                }
            }
        }
        printManager.print(jobName, printAdapter, null)
    }
}

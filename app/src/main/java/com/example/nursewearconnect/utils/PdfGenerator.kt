package com.example.nursewearconnect.utils

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.pdf.PdfDocument
import android.os.Environment
import com.example.nursewearconnect.model.CartItem
import com.example.nursewearconnect.ui.viewmodel.PriceBreakdown
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfGenerator {

    private fun getAppIconBitmap(context: Context): Bitmap? {
        return try {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else {
                val bitmap = Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun generateQRCode(text: String, width: Int, height: Int): Bitmap? {
        return try {
            val bitMatrix: BitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun generateReceipt(
        context: Context,
        orderId: String,
        cartItems: List<CartItem>,
        priceBreakdown: PriceBreakdown,
        customerName: String,
        address: String,
        verificationUrl: String? = null
    ): File? {
        val pdfDocument = PdfDocument()
        
        // Brand Colors
        val brandColor = Color.parseColor("#0D9488") // Brand600
        val slate800 = Color.parseColor("#1E293B")
        val slate500 = Color.parseColor("#64748B")
        val slate200 = Color.parseColor("#E2E8F0")

        val titlePaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 24f
            color = brandColor
        }
        val headerPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 18f
            color = slate800
        }
        val textPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 14f
            color = slate800
        }
        val secondaryTextPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textSize = 12f
            color = slate500
        }
        val boldTextPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 14f
            color = slate800
        }
        val linePaint = Paint().apply {
            color = slate200
            strokeWidth = 1f
        }

        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        var yPosition = 60f

        // Draw App Logo
        getAppIconBitmap(context)?.let { logo ->
            val scaledLogo = Bitmap.createScaledBitmap(logo, 50, 50, true)
            canvas.drawBitmap(scaledLogo, 40f, 40f, null)
        }

        canvas.drawText("NURSE WEAR CONNECT", 100f, 75f, titlePaint)
        
        // Verification QR Code (Top Right)
        verificationUrl?.let { url ->
            generateQRCode(url, 80, 80)?.let { qrBitmap ->
                canvas.drawBitmap(qrBitmap, 475f, 40f, null)
                canvas.drawText("Verify Receipt", 475f, 130f, Paint().apply {
                    textSize = 10f
                    color = slate500
                    textAlign = Paint.Align.LEFT
                })
            }
        }

        yPosition = 150f
        canvas.drawText("DIGITAL RECEIPT", 40f, yPosition, headerPaint)
        yPosition += 30f

        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        canvas.drawText("Date: ${dateFormat.format(Date())}", 40f, yPosition, textPaint)
        canvas.drawText("Order ID: #$orderId", 350f, yPosition, textPaint)
        yPosition += 40f

        // Customer Details Box
        canvas.drawRect(40f, yPosition, 555f, yPosition + 70f, Paint().apply { color = Color.parseColor("#F8FAFC"); style = Paint.Style.FILL })
        yPosition += 20f
        canvas.drawText("BILL TO:", 55f, yPosition, boldTextPaint.apply { textSize = 11f; color = brandColor })
        yPosition += 20f
        canvas.drawText(customerName, 55f, yPosition, boldTextPaint.apply { textSize = 14f; color = slate800 })
        yPosition += 18f
        canvas.drawText(address, 55f, yPosition, textPaint)
        yPosition += 45f

        // Table Header
        canvas.drawRect(40f, yPosition - 20f, 555f, yPosition + 10f, Paint().apply { color = slate800; style = Paint.Style.FILL })
        canvas.drawText("ITEM DESCRIPTION", 50f, yPosition, boldTextPaint.apply { color = Color.WHITE; textSize = 11f })
        canvas.drawText("QTY", 350f, yPosition, boldTextPaint)
        canvas.drawText("PRICE", 420f, yPosition, boldTextPaint)
        canvas.drawText("TOTAL", 500f, yPosition, boldTextPaint)
        yPosition += 30f

        boldTextPaint.color = slate800 // Reset color
        boldTextPaint.textSize = 14f

        // Items
        cartItems.forEach { item ->
            val itemName = item.product.name
            canvas.drawText(itemName, 50f, yPosition, boldTextPaint.apply { textSize = 13f })
            canvas.drawText(item.quantity.toString(), 350f, yPosition, textPaint)
            canvas.drawText("%,d".format(item.product.priceKes), 420f, yPosition, textPaint)
            canvas.drawText("%,d".format(item.product.priceKes * item.quantity), 500f, yPosition, textPaint)
            
            yPosition += 16f
            var detailText = "Size: ${item.size}"
            if (item.embroideryName != null) {
                detailText += " | Embroidery: ${item.embroideryName}"
            }
            canvas.drawText(detailText, 50f, yPosition, secondaryTextPaint)
            
            yPosition += 25f
            canvas.drawLine(40f, yPosition - 10f, 555f, yPosition - 10f, linePaint)
            
            if (yPosition > 650) { 
                canvas.drawText("... continued on next page", 50f, yPosition, secondaryTextPaint)
                return@forEach
            }
        }

        yPosition += 20f

        // Summary Alignment
        val summaryX = 350f
        val valueX = 500f

        canvas.drawText("Subtotal (excl. VAT):", summaryX, yPosition, textPaint)
        canvas.drawText("KSh %,d".format(priceBreakdown.itemsSubtotal + priceBreakdown.embroideryTotal - priceBreakdown.tax), valueX, yPosition, textPaint)
        yPosition += 20f

        canvas.drawText("VAT (16%):", summaryX, yPosition, textPaint)
        canvas.drawText("KSh %,d".format(priceBreakdown.tax), valueX, yPosition, textPaint)
        yPosition += 20f

        if (priceBreakdown.discountAmount > 0) {
            canvas.drawText("Discount:", summaryX, yPosition, textPaint)
            canvas.drawText("- KSh %,d".format(priceBreakdown.discountAmount), valueX, yPosition, textPaint)
            yPosition += 20f
        }

        canvas.drawText("Shipping:", summaryX, yPosition, textPaint)
        canvas.drawText(if (priceBreakdown.shippingCost == 0) "FREE" else "KSh %,d".format(priceBreakdown.shippingCost), valueX, yPosition, textPaint)
        yPosition += 40f

        // Total
        canvas.drawRect(summaryX - 10f, yPosition - 30f, 555f, yPosition + 15f, Paint().apply { color = brandColor; alpha = 20; style = Paint.Style.FILL })
        canvas.drawText("TOTAL AMOUNT:", summaryX, yPosition, boldTextPaint.apply { textSize = 15f; color = brandColor })
        canvas.drawText("KSh %,d".format(priceBreakdown.finalTotal), valueX, yPosition, boldTextPaint.apply { textSize = 16f })

        // Footer
        yPosition = 780f
        canvas.drawLine(40f, yPosition, 555f, yPosition, linePaint)
        yPosition += 25f
        val footerText = "Thank you for shopping with Nurse Wear Connect! For support, contact us at +254 700 000 000"
        val textWidth = textPaint.measureText(footerText)
        canvas.drawText(footerText, (595f - textWidth) / 2f, yPosition, secondaryTextPaint)

        pdfDocument.finishPage(page)

        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val file = File(directory, "Receipt_$orderId.pdf")

        return try {
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    fun generateFinancialReport(
        context: Context,
        reportTitle: String,
        data: List<Map<String, Any>>,
        summary: Map<String, Double>,
        verificationUrl: String? = null
    ): File? {
        val pdfDocument = PdfDocument()
        
        val brandColor = Color.parseColor("#0D9488")
        val slate800 = Color.parseColor("#1E293B")
        val slate500 = Color.parseColor("#64748B")
        val slate200 = Color.parseColor("#E2E8F0")

        val titlePaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 22f
            color = brandColor
        }
        val headerPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 16f
            color = slate800
        }
        val textPaint = Paint().apply {
            textSize = 12f
            color = slate800
        }
        val boldTextPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 12f
            color = slate800
        }
        val secondaryTextPaint = Paint().apply {
            textSize = 10f
            color = slate500
        }
        val linePaint = Paint().apply {
            color = slate200
            strokeWidth = 1f
        }

        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        var yPosition = 60f

        // Header
        canvas.drawText("NURSE WEAR CONNECT", 40f, yPosition, titlePaint)
        
        // Verification QR Code
        verificationUrl?.let { url ->
            generateQRCode(url, 75, 75)?.let { qrBitmap ->
                canvas.drawBitmap(qrBitmap, 480f, 40f, null)
                canvas.drawText("Scan to Verify", 480f, 125f, secondaryTextPaint)
            }
        }

        yPosition += 30f
        canvas.drawText(reportTitle.uppercase(), 40f, yPosition, headerPaint)
        yPosition += 20f
        val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        canvas.drawText("Generated on: ${dateFormat.format(Date())}", 40f, yPosition, secondaryTextPaint)
        yPosition += 40f

        // Summary Section
        canvas.drawRect(40f, yPosition, 555f, yPosition + 80f, Paint().apply { color = Color.parseColor("#F1F5F9"); style = Paint.Style.FILL })
        yPosition += 25f
        canvas.drawText("FINANCIAL SUMMARY", 55f, yPosition, boldTextPaint.apply { color = brandColor })
        yPosition += 25f
        
        var xOffset = 55f
        summary.forEach { (label, value) ->
            canvas.drawText(label, xOffset, yPosition, secondaryTextPaint)
            canvas.drawText("KSh %,.0f".format(value), xOffset, yPosition + 15f, boldTextPaint.apply { color = slate800 })
            xOffset += 120f
        }
        yPosition += 60f

        // Table Header
        canvas.drawRect(40f, yPosition - 15f, 555f, yPosition + 10f, Paint().apply { color = slate800; style = Paint.Style.FILL })
        canvas.drawText("DATE", 50f, yPosition, boldTextPaint.apply { color = Color.WHITE; textSize = 10f })
        canvas.drawText("REFERENCE / ITEM", 120f, yPosition, boldTextPaint.apply { color = Color.WHITE })
        canvas.drawText("VENDOR", 320f, yPosition, boldTextPaint.apply { color = Color.WHITE })
        canvas.drawText("AMOUNT", 480f, yPosition, boldTextPaint.apply { color = Color.WHITE })
        yPosition += 30f

        // Table Content
        data.forEach { item ->
            if (yPosition > 780f) {
                canvas.drawText("... Report continues on next page (Data truncated)", 50f, yPosition, secondaryTextPaint)
                return@forEach
            }

            val date = item["created_at"]?.toString()?.take(10) ?: item["order_date"]?.toString()?.take(10) ?: ""
            val ref = item["id"]?.toString()?.take(8) ?: item["order_id"]?.toString()?.take(8) ?: "N/A"
            val vendor = item["vendor_name"]?.toString() ?: "System"
            val amount = (item["amount"] as? Number)?.toDouble() ?: (item["total_amount"] as? Number)?.toDouble() ?: 0.0

            canvas.drawText(date, 50f, yPosition, textPaint)
            canvas.drawText(ref, 120f, yPosition, textPaint)
            canvas.drawText(vendor, 320f, yPosition, textPaint)
            canvas.drawText("KSh %,.0f".format(amount), 480f, yPosition, boldTextPaint)
            
            yPosition += 20f
            canvas.drawLine(40f, yPosition - 10f, 555f, yPosition - 10f, linePaint)
        }

        // Footer
        yPosition = 810f
        canvas.drawText("Confidential Financial Document", 40f, yPosition, secondaryTextPaint)
        val pageText = "Page 1 of 1"
        canvas.drawText(pageText, 555f - secondaryTextPaint.measureText(pageText), yPosition, secondaryTextPaint)

        pdfDocument.finishPage(page)

        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val sanitizedTitle = reportTitle.replace(" ", "_")
        val file = File(directory, "NurseWear_${sanitizedTitle}_$timestamp.pdf")

        return try {
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }
}

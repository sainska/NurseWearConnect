package com.example.nursewearconnect.utils

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExcelGenerator {

    /**
     * Generates a CSV file that Excel can open.
     * We use CSV as it doesn't require heavy dependencies like Apache POI.
     */
    fun generateFinancialReport(
        context: Context,
        reportTitle: String,
        data: List<Map<String, Any>>,
        summary: Map<String, Double>
    ): File? {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${reportTitle.replace(" ", "_")}_$timestamp.csv"
        val file = File(directory, fileName)

        return try {
            val outputStream = FileOutputStream(file)
            val writer = outputStream.bufferedWriter()

            // Header
            writer.write("NURSE WEAR CONNECT - FINANCIAL REPORT\n")
            writer.write("Report: $reportTitle\n")
            writer.write("Generated on: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date())}\n\n")

            // Summary Section
            writer.write("FINANCIAL SUMMARY\n")
            summary.forEach { (label, value) ->
                writer.write("$label,${value}\n")
            }
            writer.write("\n")

            // Table Header
            writer.write("DATE,REFERENCE,VENDOR,AMOUNT\n")

            // Table Data
            data.forEach { item ->
                val date = item["created_at"]?.toString()?.take(10) ?: item["order_date"]?.toString()?.take(10) ?: ""
                val ref = item["id"]?.toString() ?: item["order_id"]?.toString() ?: "N/A"
                val vendor = item["vendor_name"]?.toString() ?: "System"
                val amount = (item["amount"] as? Number)?.toDouble() ?: (item["total_amount"] as? Number)?.toDouble() ?: 0.0

                writer.write("$date,\"$ref\",\"$vendor\",$amount\n")
            }

            writer.flush()
            writer.close()
            outputStream.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

package com.example.nursewearconnect.utils

import com.example.nursewearconnect.model.Product

object SizeFinder {

    data class SizeResult(
        val recommendedSize: String,
        val confidence: String, // "High", "Medium", "Low"
        val fitNote: String,
        val measurements: Map<String, Double>
    )

    /**
     * Medical Uniform Size Chart (Standard)
     * Values are in inches.
     */
    private val WOMEN_SIZE_CHART = listOf(
        SizeEntry("XXS", 31.0..32.0, 23.0..24.0, 33.0..34.0),
        SizeEntry("XS", 33.0..34.0, 25.0..26.0, 35.0..36.0),
        SizeEntry("S", 35.0..36.0, 27.0..28.0, 37.0..38.0),
        SizeEntry("M", 37.0..39.0, 29.0..31.0, 39.0..41.0),
        SizeEntry("L", 40.0..43.0, 32.0..35.0, 42.0..45.0),
        SizeEntry("XL", 44.0..47.0, 36.0..39.0, 46.0..49.0),
        SizeEntry("XXL", 48.0..51.0, 40.0..43.0, 50.0..53.0)
    )

    private val MEN_SIZE_CHART = listOf(
        SizeEntry("XS", 33.0..35.0, 24.0..26.0, 33.0..35.0),
        SizeEntry("S", 36.0..38.0, 27.0..29.0, 36.0..38.0),
        SizeEntry("M", 39.0..41.0, 30.0..32.0, 39.0..41.0),
        SizeEntry("L", 42.0..45.0, 33.0..35.0, 42.0..45.0),
        SizeEntry("XL", 46.0..49.0, 36.0..38.0, 46.0..49.0),
        SizeEntry("XXL", 50.0..53.0, 39.0..41.0, 50.0..53.0)
    )

    private data class SizeEntry(
        val size: String,
        val bustRange: ClosedFloatingPointRange<Double>,
        val waistRange: ClosedFloatingPointRange<Double>,
        val hipsRange: ClosedFloatingPointRange<Double>
    )

    fun calculateRecommendedSize(
        gender: String,
        bust: Double,
        waist: Double,
        hips: Double
    ): SizeResult {
        val chart = if (gender.lowercase() == "male") MEN_SIZE_CHART else WOMEN_SIZE_CHART
        
        // Find best match based on bust mostly for tops, but we'll consider all 3
        var bestMatch: String = "M" // Default
        var fitNote = ""
        var confidence = "High"

        val matches = chart.filter { 
            bust in it.bustRange || waist in it.waistRange || hips in it.hipsRange
        }

        if (matches.isEmpty()) {
            // Find closest
            val closest = chart.minByOrNull { 
                Math.abs(it.bustRange.start - bust) + Math.abs(it.waistRange.start - waist) 
            }
            bestMatch = closest?.size ?: "M"
            confidence = "Low"
            fitNote = "Your measurements are outside our standard range. We recommend $bestMatch but please check the specific product guide."
        } else if (matches.size == 1) {
            bestMatch = matches[0].size
            confidence = "High"
            fitNote = "Perfect match! This size should fit you comfortably."
        } else {
            // Between sizes
            val larger = matches.last()
            bestMatch = larger.size
            confidence = "Medium"
            fitNote = "You are between sizes. We recommend $bestMatch for a more relaxed professional fit, or size down for a slim fit."
        }

        return SizeResult(
            recommendedSize = bestMatch,
            confidence = confidence,
            fitNote = fitNote,
            measurements = mapOf("bust" to bust, "waist" to waist, "hips" to hips)
        )
    }

    /**
     * Validates if a product's size matches the user's recommended size.
     */
    fun getProductFitStatus(product: Product, recommendedSize: String): String {
        if (product.availableSizes.isEmpty()) return "Standard Fit"
        
        return when {
            product.availableSizes.contains(recommendedSize) -> "Recommended Fit"
            product.availableSizes.any { it.contains(recommendedSize) } -> "Close Match"
            else -> "Size Unavailable"
        }
    }
    
    fun parseMeasurement(value: Any?): Double {
        val str = value?.toString()?.replace("\"", "")?.replace("in", "")?.replace("cm", "")?.trim() ?: "0"
        return str.toDoubleOrNull() ?: 0.0
    }
}

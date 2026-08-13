package com.example.nursewearconnect.data.api

import retrofit2.http.Body
import retrofit2.http.POST

interface MeilisearchService {
    /**
     * Performs a global sub-millisecond search across products.
     * Item 46 Implementation.
     */
    @POST("indexes/products/search")
    suspend fun searchProducts(
        @Body request: MeilisearchRequest
    ): MeilisearchResponse
    
    /**
     * Performs a global sub-millisecond search across orders.
     * Item 46 Implementation.
     */
    @POST("indexes/orders/search")
    suspend fun searchOrders(
        @Body request: MeilisearchRequest
    ): MeilisearchResponse
}

data class MeilisearchRequest(
    val q: String,
    val limit: Int = 20,
    val filter: String? = null
)

data class MeilisearchResponse(
    val hits: List<Map<String, Any>>,
    val query: String,
    val processingTimeMs: Int,
    val limit: Int,
    val offset: Int,
    val estimatedTotalHits: Int
)

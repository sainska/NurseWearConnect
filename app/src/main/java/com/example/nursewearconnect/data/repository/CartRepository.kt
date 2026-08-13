package com.example.nursewearconnect.data.repository

import com.example.nursewearconnect.data.api.ApiService
import com.example.nursewearconnect.data.local.CartDao
import com.example.nursewearconnect.data.local.CartItemEntity
import com.example.nursewearconnect.data.local.SyncActionDao
import com.example.nursewearconnect.data.local.SyncActionEntity
import com.example.nursewearconnect.data.security.SecurityManager
import com.example.nursewearconnect.model.CartItem
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class CartRepository(
    private val securityManager: SecurityManager,
    private val cartDao: CartDao,
    private val syncActionDao: SyncActionDao,
    private val apiService: ApiService,
    private val productRepository: ProductRepository
) {
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems
    private val scope = CoroutineScope(Dispatchers.IO)
    private val gson = Gson()

    init {
        scope.launch {
            val userId = securityManager.getUserId()
            if (userId != null) {
                observeAndHydrateCart(userId)
                processPendingSyncActions()
                syncWithRemote(userId)
            } else {
                _cartItems.value = securityManager.getCart()
            }
        }
    }

    private suspend fun processPendingSyncActions() {
        val pending = syncActionDao.getPendingActions()
        for (action in pending) {
            try {
                when (action.actionType) {
                    "SYNC_CART" -> {
                        val items = gson.fromJson(action.payloadJson, Array<CartItem>::class.java).toList()
                        performRemoteSync(items)
                    }
                    "CLEAR_CART" -> {
                        val userId = action.payloadJson
                        apiService.clearRemoteCart("eq.$userId")
                    }
                }
                syncActionDao.deleteSyncAction(action)
            } catch (e: Exception) {
                // Keep in queue for next time
                break 
            }
        }
    }

    private suspend fun observeAndHydrateCart(userId: String) {
        // Combine the local cart items and the products from the repository to ensure full details are always shown
        combine(cartDao.getCartItems(userId), productRepository.products) { entities, products ->
            entities.map { entity ->
                val actualProduct = products.find { it.id == entity.productId }
                if (actualProduct != null) {
                    entity.toDomain(actualProduct)
                } else {
                    entity.toLoadingDomain()
                }
            }
        }.collect { hydratedItems ->
            _cartItems.value = hydratedItems
        }
    }

    private suspend fun syncWithRemote(userId: String) {
        try {
            val response = apiService.getCartItems("eq.$userId")
            // Merge logic would go here if we were doing deep sync
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addToCart(newItem: CartItem) {
        val currentList = _cartItems.value.toMutableList()
        val existingItemIndex = currentList.indexOfFirst { 
            it.product.id == newItem.product.id && 
            it.size == newItem.size && 
            it.color?.name == newItem.color?.name &&
            it.embroideryName == newItem.embroideryName
        }

        if (existingItemIndex != -1) {
            val existingItem = currentList[existingItemIndex]
            currentList[existingItemIndex] = existingItem.copy(
                quantity = existingItem.quantity + newItem.quantity,
                updatedAt = System.currentTimeMillis()
            )
        } else {
            currentList.add(newItem.copy(updatedAt = System.currentTimeMillis()))
        }

        _cartItems.value = currentList
        saveCartState(currentList)
    }

    private fun saveCartState(items: List<CartItem>) {
        scope.launch {
            val userId = securityManager.getUserId()
            if (userId != null) {
                // Save to Room
                val entities = items.map { it.toEntity(userId) }
                cartDao.clearCart(userId)
                entities.forEach { cartDao.insertCartItem(it) }

                // Save to Supabase (Sync)
                try {
                    performRemoteSync(items)
                } catch (e: Exception) {
                    // Queue for later if offline
                    syncActionDao.insertSyncAction(SyncActionEntity(
                        actionType = "SYNC_CART",
                        payloadJson = gson.toJson(items)
                    ))
                }
            } else {
                securityManager.saveCart(items)
            }
        }
    }

    private suspend fun performRemoteSync(items: List<CartItem>) {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US)
        val syncData = items.map { 
            mapOf(
                "product_id" to it.product.id,
                "quantity" to it.quantity,
                "size" to it.size,
                "color_name" to it.color?.name,
                "color_hex" to it.color?.hex,
                "embroidery_name" to it.embroideryName,
                "updated_at" to sdf.format(java.util.Date(it.updatedAt))
            )
        }
        apiService.syncCartItems(mapOf("p_items" to syncData))
    }

    fun removeFromCart(cartItem: CartItem) {
        val updatedList = _cartItems.value.filter { it != cartItem }
        _cartItems.value = updatedList
        saveCartState(updatedList)
    }

    fun updateQuantity(cartItem: CartItem, quantity: Int) {
        if (quantity <= 0) {
            removeFromCart(cartItem)
            return
        }
        val updatedList = _cartItems.value.map {
            if (it == cartItem) it.copy(quantity = quantity, updatedAt = System.currentTimeMillis()) else it
        }
        _cartItems.value = updatedList
        saveCartState(updatedList)
    }

    fun updateCartItem(oldItem: CartItem, newItem: CartItem) {
        val updatedList = _cartItems.value.map {
            if (it == oldItem) newItem.copy(updatedAt = System.currentTimeMillis()) else it
        }
        _cartItems.value = updatedList
        saveCartState(updatedList)
    }

    fun clearCart() {
        val userId = securityManager.getUserId()
        _cartItems.value = emptyList()
        scope.launch {
            if (userId != null) {
                cartDao.clearCart(userId)
                try {
                    apiService.clearRemoteCart("eq.$userId")
                } catch (e: Exception) {
                    syncActionDao.insertSyncAction(SyncActionEntity(
                        actionType = "CLEAR_CART",
                        payloadJson = userId
                    ))
                }
            } else {
                securityManager.clearCart()
            }
        }
    }

    private fun CartItem.toEntity(userId: String) = CartItemEntity(
        productId = product.id,
        quantity = quantity,
        size = size,
        colorName = color?.name,
        colorHex = color?.hex,
        embroideryName = embroideryName,
        userId = userId,
        updatedAt = updatedAt
    )

    private fun CartItemEntity.toDomain(product: com.example.nursewearconnect.model.Product) = CartItem(
        product = product,
        quantity = quantity,
        size = size,
        color = colorName?.let { name -> 
            com.example.nursewearconnect.model.ProductColor(name, colorHex ?: 0L)
        },
        embroideryName = embroideryName,
        updatedAt = updatedAt
    )

    private fun CartItemEntity.toLoadingDomain() = CartItem(
        product = com.example.nursewearconnect.model.Product(
            id = productId,
            name = "Loading...",
            category = "",
            gender = "",
            priceKes = 0.0,
            rating = 0.0,
            reviewsCount = 0,
            tag = null,
            images = emptyList()
        ),
        quantity = quantity,
        size = size,
        color = colorName?.let { name -> 
            com.example.nursewearconnect.model.ProductColor(name, colorHex ?: 0L)
        },
        embroideryName = embroideryName,
        updatedAt = updatedAt
    )
}

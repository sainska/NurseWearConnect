package com.example.nursewearconnect.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>): List<Long>

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts(): Int

    @Transaction
    suspend fun refreshProducts(products: List<ProductEntity>): Int {
        deleteAllProducts()
        insertProducts(products)
        return products.size
    }
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>): List<Long>

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories(): Int
}

@Dao
interface CartDao {
    @Query("SELECT * FROM cart_items WHERE userId = :userId")
    fun getCartItems(userId: String): Flow<List<CartItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCartItem(item: CartItemEntity)

    @Update
    suspend fun updateCartItem(item: CartItemEntity)

    @Delete
    suspend fun deleteCartItem(item: CartItemEntity)

    @Query("DELETE FROM cart_items WHERE userId = :userId")
    suspend fun clearCart(userId: String)

    @Query("SELECT * FROM cart_items WHERE userId = :userId")
    suspend fun getCartItemsList(userId: String): List<CartItemEntity>
}

@Dao
interface SyncActionDao {
    @Query("SELECT * FROM sync_actions ORDER BY createdAt ASC")
    fun getAllSyncActions(): Flow<List<SyncActionEntity>>

    @Query("SELECT * FROM sync_actions ORDER BY createdAt ASC")
    suspend fun getPendingActions(): List<SyncActionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncAction(action: SyncActionEntity)

    @Delete
    suspend fun deleteSyncAction(action: SyncActionEntity)

    @Query("DELETE FROM sync_actions WHERE id = :id")
    suspend fun deleteById(id: Long)
}

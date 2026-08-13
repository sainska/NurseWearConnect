package com.example.nursewearconnect.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.nursewearconnect.model.Product
import com.example.nursewearconnect.model.Category

@Database(entities = [ProductEntity::class, CategoryEntity::class, CartItemEntity::class, SyncActionEntity::class], version = 7, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun categoryDao(): CategoryDao
    abstract fun cartDao(): CartDao
    abstract fun syncActionDao(): SyncActionDao

    suspend fun clearAllCaches() {
        productDao().deleteAllProducts()
        categoryDao().deleteAllCategories()
    }
}

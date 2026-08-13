package com.example.nursewearconnect.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.nursewearconnect.data.api.ApiService
import com.example.nursewearconnect.data.local.AppDatabase
import com.example.nursewearconnect.data.local.SyncActionEntity
import io.github.jan.supabase.SupabaseClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val apiService: ApiService,
    private val database: AppDatabase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val pendingActions = database.syncActionDao().getPendingActions()
        
        if (pendingActions.isEmpty()) return Result.success()

        var allSuccessful = true
        
        for (action in pendingActions) {
            val success = processAction(action)
            if (success) {
                database.syncActionDao().deleteById(action.id)
            } else {
                allSuccessful = false
            }
        }

        return if (allSuccessful) Result.success() else Result.retry()
    }

    private suspend fun processAction(action: SyncActionEntity): Boolean {
        return try {
            val payload = Json.parseToJsonElement(action.payloadJson).jsonObject
            when (action.actionType) {
                "ADD_TO_CART" -> {
                    // Logic to sync cart with Supabase
                    true
                }
                "UPDATE_PROFILE" -> {
                    // Logic to sync profile changes
                    true
                }
                else -> true
            }
        } catch (e: Exception) {
            false
        }
    }
}

package com.example.nursewearconnect.data.sync

import android.content.Context
import androidx.work.*
import com.example.nursewearconnect.data.local.SyncActionDao
import com.example.nursewearconnect.data.local.SyncActionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class SyncManager(
    private val context: Context,
    private val syncActionDao: SyncActionDao
) {
    /**
     * Schedules a background worker to sync pending actions when network is available.
     */
    fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .addTag("offline_sync_tag")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "offline_sync",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            syncRequest
        )
    }

    /**
     * Adds an action to the local sync queue and triggers a sync attempt.
     */
    suspend fun addAction(type: String, payload: String) {
        withContext(Dispatchers.IO) {
            syncActionDao.insertSyncAction(
                SyncActionEntity(
                    actionType = type,
                    payloadJson = payload
                )
            )
            scheduleSync()
        }
    }
}

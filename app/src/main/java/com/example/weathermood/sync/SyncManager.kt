package com.example.weathermood.sync

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class SyncManager(private val context: Context) {
    
    private val workManager = WorkManager.getInstance(context)
    
    fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
            
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            15, TimeUnit.MINUTES, // Минимум 15 минут между синхронизациями
            5, TimeUnit.MINUTES    // Гибкость в 5 минут
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag("weather_sync")
            .build()
            
        workManager.enqueueUniquePeriodicWork(
            "weather_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
    
    fun scheduleImmediateSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .addTag("immediate_sync")
            .build()
            
        workManager.enqueue(syncRequest)
    }
    
    fun cancelSync() {
        workManager.cancelAllWorkByTag("weather_sync")
        workManager.cancelAllWorkByTag("immediate_sync")
    }
}

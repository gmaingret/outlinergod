package com.gmaingret.outlinergod.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gmaingret.outlinergod.repository.AuthRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncOrchestrator: SyncOrchestrator,
    private val authRepository: AuthRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return if (syncOrchestrator.fullSync().isSuccess) {
            Result.success()
        } else {
            Result.retry()
        }
    }

}

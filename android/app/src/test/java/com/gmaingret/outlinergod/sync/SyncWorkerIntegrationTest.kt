package com.gmaingret.outlinergod.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.gmaingret.outlinergod.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Integration test for SyncWorker — verifies WorkManager Result mapping via SyncOrchestrator.
 *
 * After the Plan 02 refactor, SyncWorker delegates all sync logic to SyncOrchestrator.
 * The real integration coverage (pull/push/upsert/HLC update) lives in
 * SyncOrchestratorIntegrationTest (Plan 01).
 *
 * This test only verifies: orchestrator.fullSync() result → WorkManager Result mapping.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncWorkerIntegrationTest {

    private lateinit var context: Context
    private lateinit var syncOrchestrator: SyncOrchestrator
    private lateinit var authRepository: AuthRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        syncOrchestrator = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
    }

    private fun createWorkerFactory(): WorkerFactory {
        return object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker {
                return SyncWorker(
                    appContext = appContext,
                    workerParams = workerParameters,
                    syncOrchestrator = syncOrchestrator,
                    authRepository = authRepository
                )
            }
        }
    }

    private suspend fun runSyncWorker(): ListenableWorker.Result {
        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(createWorkerFactory())
            .build()
        return worker.doWork()
    }

    @Test
    fun doWork_returnsSuccess_whenOrchestratorSucceeds() = runTest {
        coEvery { syncOrchestrator.fullSync() } returns Result.success(Unit)

        val result = runSyncWorker()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun doWork_returnsRetry_whenOrchestratorFails() = runTest {
        coEvery { syncOrchestrator.fullSync() } returns Result.failure(
            RuntimeException("Network unavailable")
        )

        val result = runSyncWorker()

        assertEquals(ListenableWorker.Result.retry(), result)
    }
}

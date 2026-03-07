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
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncWorkerTest {

    private lateinit var context: Context
    private lateinit var syncOrchestrator: SyncOrchestrator
    private lateinit var authRepository: AuthRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        syncOrchestrator = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)

        // Default mock setup: happy path
        coEvery { syncOrchestrator.fullSync() } returns Result.success(Unit)
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
    fun doWork_returnsRetry_whenOrchestratorFailsOnAuthError() = runTest {
        coEvery { syncOrchestrator.fullSync() } returns Result.failure(
            RuntimeException("Token expired")
        )

        val result = runSyncWorker()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun doWork_returnsRetry_whenOrchestratorFailsOnNetworkError() = runTest {
        coEvery { syncOrchestrator.fullSync() } returns Result.failure(
            java.io.IOException("Network error")
        )

        val result = runSyncWorker()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun doWork_callsOrchestratorFullSync() = runTest {
        coEvery { syncOrchestrator.fullSync() } returns Result.success(Unit)

        runSyncWorker()

        coVerify { syncOrchestrator.fullSync() }
    }
}

package com.gmaingret.outlinergod.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncSchedulerTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager
    private lateinit var syncScheduler: SyncScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)
        syncScheduler = SyncScheduler(workManager)
    }

    @Test
    fun schedulePeriodicSync_enqueuesWorkWithCorrectName() = runTest {
        syncScheduler.schedulePeriodicSync()

        val workInfos = workManager.getWorkInfosForUniqueWork(SyncScheduler.WORK_NAME).get()
        assertTrue("Work should be enqueued", workInfos.isNotEmpty())
        assertEquals(WorkInfo.State.ENQUEUED, workInfos[0].state)
    }

    @Test
    fun schedulePeriodicSync_hasConnectedNetworkConstraint() = runTest {
        syncScheduler.schedulePeriodicSync()

        val workInfos = workManager.getWorkInfosForUniqueWork(SyncScheduler.WORK_NAME).get()
        assertTrue("Work should be enqueued", workInfos.isNotEmpty())
        val constraints = workInfos[0].constraints
        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
    }
}

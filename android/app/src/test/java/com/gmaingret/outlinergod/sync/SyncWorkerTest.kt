package com.gmaingret.outlinergod.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.gmaingret.outlinergod.db.dao.BookmarkDao
import com.gmaingret.outlinergod.db.dao.DocumentDao
import com.gmaingret.outlinergod.db.dao.NodeDao
import com.gmaingret.outlinergod.db.dao.SettingsDao
import com.gmaingret.outlinergod.db.entity.SettingsEntity
import com.gmaingret.outlinergod.network.model.NodeSyncRecord
import com.gmaingret.outlinergod.network.model.SyncChangesResponse
import com.gmaingret.outlinergod.network.model.SyncPushResponse
import com.gmaingret.outlinergod.network.model.TokenPair
import com.gmaingret.outlinergod.repository.AuthRepository
import com.gmaingret.outlinergod.repository.SyncRepository
import io.mockk.Ordering
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.gmaingret.outlinergod.sync.SyncConstants
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncWorkerTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var syncRepository: SyncRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var nodeDao: NodeDao
    private lateinit var documentDao: DocumentDao
    private lateinit var bookmarkDao: BookmarkDao
    private lateinit var settingsDao: SettingsDao

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        dataStore = PreferenceDataStoreFactory.create {
            File(tmpFolder.newFolder(), "test_prefs.preferences_pb")
        }

        syncRepository = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        nodeDao = mockk(relaxed = true)
        documentDao = mockk(relaxed = true)
        bookmarkDao = mockk(relaxed = true)
        settingsDao = mockk(relaxed = true)

        // Default mock setup: happy path
        coEvery { authRepository.refreshToken() } returns Result.success(
            TokenPair(token = "token", refreshToken = "refresh")
        )
        coEvery { authRepository.getDeviceId() } returns flowOf("device-1")
        coEvery { authRepository.getAccessToken() } returns flowOf("user-1")
        coEvery { authRepository.getUserId() } returns flowOf("user-1")
        coEvery { nodeDao.getPendingChanges(any(), any(), any()) } returns emptyList()
        coEvery { documentDao.getPendingChanges(any(), any(), any()) } returns emptyList()
        coEvery { bookmarkDao.getPendingChanges(any(), any(), any()) } returns emptyList()
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
                    syncRepository = syncRepository,
                    authRepository = authRepository,
                    nodeDao = nodeDao,
                    documentDao = documentDao,
                    bookmarkDao = bookmarkDao,
                    settingsDao = settingsDao,
                    dataStore = dataStore
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
    fun doWork_refreshesToken_beforeSync() = runTest {
        coEvery { syncRepository.pull(any(), any()) } returns Result.success(
            SyncChangesResponse(serverHlc = "hlc1")
        )
        coEvery { syncRepository.push(any()) } returns Result.success(
            SyncPushResponse(serverHlc = "hlc1")
        )

        val result = runSyncWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(ordering = Ordering.ORDERED) {
            authRepository.refreshToken()
            syncRepository.pull(any(), any())
        }
    }

    @Test
    fun doWork_returnsFailure_whenTokenRefreshFails() = runTest {
        coEvery { authRepository.refreshToken() } returns Result.failure(
            RuntimeException("Token expired")
        )

        val result = runSyncWorker()

        assertEquals(ListenableWorker.Result.failure(), result)
        coVerify(exactly = 0) { syncRepository.pull(any(), any()) }
    }

    @Test
    fun doWork_returnsRetry_onNetworkPullFailure() = runTest {
        coEvery { syncRepository.pull(any(), any()) } returns Result.failure(
            IOException("Network error")
        )

        val result = runSyncWorker()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun doWork_appliesUpsert_forPulledNode() = runTest {
        val pulledNode = NodeSyncRecord(
            id = "node-1",
            documentId = "doc-1",
            userId = "user-1",
            content = "server content",
            contentHlc = "BBBB",
            sortOrder = "a0",
            createdAt = 1000L,
            updatedAt = 2000L
        )

        coEvery { syncRepository.pull(any(), any()) } returns Result.success(
            SyncChangesResponse(serverHlc = "hlc2", nodes = listOf(pulledNode))
        )
        coEvery { syncRepository.push(any()) } returns Result.success(
            SyncPushResponse(serverHlc = "hlc2")
        )

        val result = runSyncWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { nodeDao.upsertNodes(match { nodes ->
            nodes.size == 1 && nodes[0].content == "server content" && nodes[0].id == "node-1"
        }) }
    }

    @Test
    fun doWork_updatesLastSyncHlc_afterSuccess() = runTest {
        coEvery { syncRepository.pull(any(), any()) } returns Result.success(
            SyncChangesResponse(serverHlc = "pull-hlc")
        )
        coEvery { syncRepository.push(any()) } returns Result.success(
            SyncPushResponse(serverHlc = "CCCC")
        )

        val result = runSyncWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        val storedHlc = dataStore.data.map { prefs ->
            prefs[SyncConstants.LAST_SYNC_HLC_KEY]
        }.first()
        assertEquals("CCCC", storedHlc)
    }

    @Test
    fun doWork_returnsSuccess_onCleanRun() = runTest {
        coEvery { syncRepository.pull(any(), any()) } returns Result.success(
            SyncChangesResponse(serverHlc = "hlc1")
        )
        coEvery { syncRepository.push(any()) } returns Result.success(
            SyncPushResponse(serverHlc = "hlc1")
        )

        val result = runSyncWorker()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun doWork_returnsRetry_onPushFailure() = runTest {
        coEvery { syncRepository.pull(any(), any()) } returns Result.success(
            SyncChangesResponse(serverHlc = "hlc1")
        )
        coEvery { syncRepository.push(any()) } returns Result.failure(
            IOException("Push failed")
        )

        val result = runSyncWorker()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun doWork_includesSettings_whenPendingSettingsExist() = runTest {
        val pendingSettings = SettingsEntity(
            userId = "user-1",
            theme = "light",
            themeHlc = "BBBB",
            densityHlc = "AAAA",
            showGuideLinesHlc = "AAAA",
            showBacklinkBadgeHlc = "AAAA",
            deviceId = "device-1",
            updatedAt = 1000L
        )
        coEvery { settingsDao.getPendingSettings(any(), any(), any()) } returns pendingSettings
        coEvery { syncRepository.pull(any(), any()) } returns Result.success(
            SyncChangesResponse(serverHlc = "hlc1")
        )
        coEvery { syncRepository.push(any()) } returns Result.success(
            SyncPushResponse(serverHlc = "hlc1")
        )

        val result = runSyncWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify {
            syncRepository.push(match { payload ->
                payload.settings != null &&
                payload.settings!!.theme == "light" &&
                payload.settings!!.id == "user-1"
            })
        }
    }

    @Test
    fun doWork_omitsSettings_whenNoPendingSettings() = runTest {
        coEvery { settingsDao.getPendingSettings(any(), any(), any()) } returns null
        coEvery { syncRepository.pull(any(), any()) } returns Result.success(
            SyncChangesResponse(serverHlc = "hlc1")
        )
        coEvery { syncRepository.push(any()) } returns Result.success(
            SyncPushResponse(serverHlc = "hlc1")
        )

        val result = runSyncWorker()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify {
            syncRepository.push(match { payload ->
                payload.settings == null
            })
        }
    }
}

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
import com.gmaingret.outlinergod.db.AppDatabase
import com.gmaingret.outlinergod.db.dao.BookmarkDao
import com.gmaingret.outlinergod.db.dao.DocumentDao
import com.gmaingret.outlinergod.db.dao.NodeDao
import com.gmaingret.outlinergod.db.dao.SettingsDao
import com.gmaingret.outlinergod.network.model.NodeSyncRecord
import com.gmaingret.outlinergod.network.model.SyncChangesResponse
import com.gmaingret.outlinergod.network.model.SyncPushResponse
import com.gmaingret.outlinergod.network.model.TokenPair
import com.gmaingret.outlinergod.repository.AuthRepository
import com.gmaingret.outlinergod.repository.SyncRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Integration test for SyncWorker that uses a real in-memory Room database.
 *
 * Unlike SyncWorkerTest which mocks all DAOs, this test verifies that:
 * - Pulled nodes are actually upserted and persisted in Room
 * - The last_sync_hlc is actually written to a real DataStore
 * - The worker returns the correct Result when pull fails
 *
 * This catches wiring bugs between SyncWorker, Room DAOs, and DataStore.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncWorkerIntegrationTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var database: AppDatabase
    private lateinit var nodeDao: NodeDao
    private lateinit var documentDao: DocumentDao
    private lateinit var bookmarkDao: BookmarkDao
    private lateinit var settingsDao: SettingsDao
    private lateinit var syncRepository: SyncRepository
    private lateinit var authRepository: AuthRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)

        dataStore = PreferenceDataStoreFactory.create {
            File(tmpFolder.newFolder(), "integration_prefs.preferences_pb")
        }

        // Real in-memory Room database (no FTS — Robolectric lacks FTS virtual tables)
        database = AppDatabase.buildInMemory(context)
        nodeDao = database.nodeDao()
        documentDao = database.documentDao()
        bookmarkDao = database.bookmarkDao()
        settingsDao = database.settingsDao()

        syncRepository = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)

        // Default happy-path mock setup
        coEvery { authRepository.refreshToken() } returns Result.success(
            TokenPair(token = "token", refreshToken = "refresh")
        )
        coEvery { authRepository.getDeviceId() } returns flowOf("device-integration")
        coEvery { authRepository.getAccessToken() } returns flowOf("access-token")
        coEvery { authRepository.getUserId() } returns flowOf("user-integration")
    }

    @After
    fun tearDown() {
        database.close()
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
    fun pullThenPush_upsertsNodeIntoRealDb() = runTest {
        // Arrange: server returns a node on pull
        val serverNode = NodeSyncRecord(
            id = "node-integration-1",
            documentId = "doc-integration-1",
            userId = "user-integration",
            content = "Integration test content",
            contentHlc = "HLC-AAAA",
            sortOrder = "aV",
            createdAt = 1000L,
            updatedAt = 2000L
        )

        coEvery { syncRepository.pull(any(), any()) } returns Result.success(
            SyncChangesResponse(serverHlc = "HLC-SERVER-1", nodes = listOf(serverNode))
        )
        coEvery { syncRepository.push(any()) } returns Result.success(
            SyncPushResponse(serverHlc = "HLC-SERVER-1")
        )

        // Act
        val result = runSyncWorker()

        // Assert: worker succeeded
        assertEquals(ListenableWorker.Result.success(), result)

        // Assert: the node was actually persisted in the real Room database
        val persistedNodes = nodeDao.getNodesByDocumentSync("doc-integration-1")
        assertEquals(1, persistedNodes.size)
        val persisted = persistedNodes[0]
        assertEquals("node-integration-1", persisted.id)
        assertEquals("Integration test content", persisted.content)
        assertEquals("HLC-AAAA", persisted.contentHlc)
    }

    @Test
    fun pullThenPush_updatesLastSyncHlcInDataStore() = runTest {
        // Arrange: server returns a push response with a new HLC
        val expectedServerHlc = "HLC-FINAL-PUSH"

        coEvery { syncRepository.pull(any(), any()) } returns Result.success(
            SyncChangesResponse(serverHlc = "HLC-PULL")
        )
        coEvery { syncRepository.push(any()) } returns Result.success(
            SyncPushResponse(serverHlc = expectedServerHlc)
        )

        // Act
        val result = runSyncWorker()

        // Assert: worker succeeded
        assertEquals(ListenableWorker.Result.success(), result)

        // Assert: the HLC was written to the real DataStore
        val storedHlc = dataStore.data.map { prefs ->
            prefs[SyncConstants.LAST_SYNC_HLC_KEY]
        }.first()
        assertNotNull(storedHlc)
        assertEquals(expectedServerHlc, storedHlc)
    }

    @Test
    fun doWork_returnsRetryOnPullFailure() = runTest {
        // Arrange: pull fails
        coEvery { syncRepository.pull(any(), any()) } returns Result.failure(
            RuntimeException("Network unavailable")
        )

        // Act
        val result = runSyncWorker()

        // Assert: worker returns retry (not failure, not success)
        assertEquals(ListenableWorker.Result.retry(), result)

        // Assert: nothing was written to DataStore
        val storedHlc = dataStore.data.map { prefs ->
            prefs[SyncConstants.LAST_SYNC_HLC_KEY]
        }.first()
        assertEquals(null, storedHlc)
    }
}

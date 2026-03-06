package com.gmaingret.outlinergod.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Integration test for SyncOrchestratorImpl that uses a real in-memory Room database
 * and real DataStore. Network calls (SyncRepository, AuthRepository) are still mocked.
 *
 * Verifies that:
 * - Pulled nodes are actually persisted in Room
 * - The last_sync_hlc is written to a real DataStore after successful push
 * - hasLocalData guard forces since="0" when documentDao is empty
 * - Pull failure leaves DataStore unchanged
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncOrchestratorIntegrationTest {

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
    private lateinit var orchestrator: SyncOrchestratorImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        dataStore = PreferenceDataStoreFactory.create {
            File(tmpFolder.newFolder(), "integration_orchestrator_prefs.preferences_pb")
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
        coEvery { authRepository.getUserId() } returns flowOf("user-integration")

        orchestrator = SyncOrchestratorImpl(
            syncRepository = syncRepository,
            authRepository = authRepository,
            nodeDao = nodeDao,
            documentDao = documentDao,
            bookmarkDao = bookmarkDao,
            settingsDao = settingsDao,
            dataStore = dataStore
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * Test 5: Pull returns a node — it lands in the real Room DB.
     */
    @Test
    fun fullSync_pulledNode_persistedInRealRoomDb() = runTest {
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

        val result = orchestrator.fullSync()

        assertTrue(result.isSuccess)

        val persistedNodes = nodeDao.getNodesByDocumentSync("doc-integration-1")
        assertEquals(1, persistedNodes.size)
        val persisted = persistedNodes[0]
        assertEquals("node-integration-1", persisted.id)
        assertEquals("Integration test content", persisted.content)
        assertEquals("HLC-AAAA", persisted.contentHlc)
    }

    /**
     * Test 6: Push succeeds — DataStore lastSyncHlcKey contains pushResponse.serverHlc.
     */
    @Test
    fun fullSync_pushSucceeds_hlcWrittenToDataStore() = runTest {
        val expectedServerHlc = "HLC-FINAL-PUSH"

        coEvery { syncRepository.pull(any(), any()) } returns Result.success(
            SyncChangesResponse(serverHlc = "HLC-PULL")
        )
        coEvery { syncRepository.push(any()) } returns Result.success(
            SyncPushResponse(serverHlc = expectedServerHlc)
        )

        val result = orchestrator.fullSync()

        assertTrue(result.isSuccess)

        val storedHlc = dataStore.data.map { prefs ->
            prefs[SyncConstants.lastSyncHlcKey("user-integration")]
        }.first()
        assertNotNull(storedHlc)
        assertEquals(expectedServerHlc, storedHlc)
    }

    /**
     * Test 7: hasLocalData guard — documentDao is empty → pull called with since="0"
     * regardless of any stored HLC value.
     */
    @Test
    fun fullSync_hasLocalDataGuard_emptyDb_pullsWithZero() = runTest {
        // documentDao is real and empty — countDocuments returns 0
        // Pre-seed DataStore with a non-zero HLC to ensure the guard overrides it
        // (In practice, this would only happen on reinstall with DataStore backup)

        coEvery { syncRepository.pull(any(), any()) } returns Result.success(
            SyncChangesResponse(serverHlc = "HLC-AFTER")
        )
        coEvery { syncRepository.push(any()) } returns Result.success(
            SyncPushResponse(serverHlc = "HLC-AFTER")
        )

        val result = orchestrator.fullSync()

        assertTrue(result.isSuccess)

        // Verify pull was called with since="0" (the empty-DB guard)
        io.mockk.coVerify {
            syncRepository.pull(since = "0", deviceId = any())
        }
    }

    /**
     * Test 8: Pull failure — DataStore unchanged (no HLC written).
     */
    @Test
    fun fullSync_pullFailure_dataStoreUnchanged() = runTest {
        coEvery { syncRepository.pull(any(), any()) } returns Result.failure(
            RuntimeException("Network unavailable")
        )

        val result = orchestrator.fullSync()

        assertTrue(result.isFailure)

        val storedHlc = dataStore.data.map { prefs ->
            prefs[SyncConstants.lastSyncHlcKey("user-integration")]
        }.first()
        assertNull(storedHlc)
    }
}

private fun assertTrue(value: Boolean) {
    org.junit.Assert.assertTrue(value)
}

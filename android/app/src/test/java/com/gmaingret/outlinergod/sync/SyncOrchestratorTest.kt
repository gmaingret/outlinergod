package com.gmaingret.outlinergod.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
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
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncOrchestratorTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var syncRepository: SyncRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var nodeDao: NodeDao
    private lateinit var documentDao: DocumentDao
    private lateinit var bookmarkDao: BookmarkDao
    private lateinit var settingsDao: SettingsDao
    private lateinit var orchestrator: SyncOrchestratorImpl

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create {
            File(tmpFolder.newFolder(), "orchestrator_test_prefs.preferences_pb")
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
        coEvery { authRepository.getUserId() } returns flowOf("user-1")
        coEvery { documentDao.countDocuments(any()) } returns 1 // has local data
        coEvery { nodeDao.getPendingChanges(any(), any(), any()) } returns emptyList()
        coEvery { documentDao.getPendingChanges(any(), any(), any()) } returns emptyList()
        coEvery { bookmarkDao.getPendingChanges(any(), any(), any()) } returns emptyList()
        coEvery { settingsDao.getPendingSettings(any(), any(), any()) } returns null

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

    /**
     * Test 1: Happy path — all steps succeed.
     * - refreshToken succeeds
     * - pull returns a node
     * - push returns serverHlc="CCCC"
     * Expects: Result.isSuccess, nodeDao.upsertNodes called, DataStore has "CCCC"
     */
    @Test
    fun fullSync_happyPath_returnsSuccess() = runTest {
        val pulledNode = NodeSyncRecord(
            id = "node-1",
            documentId = "doc-1",
            userId = "user-1",
            content = "server content",
            contentHlc = "BBBB",
            sortOrder = "aV",
            createdAt = 1000L,
            updatedAt = 2000L
        )

        coEvery { syncRepository.pull(any(), any()) } returns Result.success(
            SyncChangesResponse(serverHlc = "hlc1", nodes = listOf(pulledNode))
        )
        coEvery { syncRepository.push(any()) } returns Result.success(
            SyncPushResponse(serverHlc = "CCCC")
        )

        val result = orchestrator.fullSync()

        assertTrue(result.isSuccess)
        coVerify { nodeDao.upsertNodes(any()) }

        val storedHlc = dataStore.data.map { prefs ->
            prefs[SyncConstants.lastSyncHlcKey("user-1")]
        }.first()
        assertEquals("CCCC", storedHlc)
    }

    /**
     * Test 2: Auth failure — refreshToken fails, no pull attempted.
     * Expects: Result.isFailure, syncRepository.pull never called
     */
    @Test
    fun fullSync_authFailure_returnsFailureNoPull() = runTest {
        coEvery { authRepository.refreshToken() } returns Result.failure(
            RuntimeException("Token expired")
        )

        val result = orchestrator.fullSync()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { syncRepository.pull(any(), any()) }
    }

    /**
     * Test 3: Pull failure — pull fails, no push attempted.
     * Expects: Result.isFailure, syncRepository.push never called
     */
    @Test
    fun fullSync_pullFailure_returnsFailureNoPush() = runTest {
        coEvery { syncRepository.pull(any(), any()) } returns Result.failure(
            IOException("Network error")
        )

        val result = orchestrator.fullSync()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { syncRepository.push(any()) }
    }

    /**
     * Test 4: Push failure — pull succeeds, push fails, DataStore not updated.
     * Expects: Result.isFailure, no HLC written to DataStore
     */
    @Test
    fun fullSync_pushFailure_returnsFailureNoHlcWritten() = runTest {
        coEvery { syncRepository.pull(any(), any()) } returns Result.success(
            SyncChangesResponse(serverHlc = "hlc1")
        )
        coEvery { syncRepository.push(any()) } returns Result.failure(
            IOException("Push failed")
        )

        val result = orchestrator.fullSync()

        assertTrue(result.isFailure)

        val storedHlc = dataStore.data.map { prefs ->
            prefs[SyncConstants.lastSyncHlcKey("user-1")]
        }.first()
        assertEquals(null, storedHlc)
    }
}

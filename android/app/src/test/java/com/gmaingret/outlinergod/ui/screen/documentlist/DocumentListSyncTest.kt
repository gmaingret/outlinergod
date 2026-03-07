package com.gmaingret.outlinergod.ui.screen.documentlist

import com.gmaingret.outlinergod.ui.common.SyncStatus
import com.gmaingret.outlinergod.db.dao.BookmarkDao
import com.gmaingret.outlinergod.db.dao.DocumentDao
import com.gmaingret.outlinergod.db.dao.NodeDao
import com.gmaingret.outlinergod.db.dao.SettingsDao
import com.gmaingret.outlinergod.repository.AuthRepository
import com.gmaingret.outlinergod.sync.HlcClock
import com.gmaingret.outlinergod.sync.SyncOrchestrator
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.orbitmvi.orbit.test.test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentListSyncTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var documentDao: DocumentDao
    private lateinit var nodeDao: NodeDao
    private lateinit var bookmarkDao: BookmarkDao
    private lateinit var settingsDao: SettingsDao
    private lateinit var authRepository: AuthRepository
    private lateinit var syncOrchestrator: SyncOrchestrator
    private lateinit var hlcClock: HlcClock

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        documentDao = mockk(relaxed = true)
        nodeDao = mockk(relaxed = true)
        bookmarkDao = mockk(relaxed = true)
        settingsDao = mockk(relaxed = true)
        authRepository = mockk()
        syncOrchestrator = mockk(relaxed = true)
        hlcClock = mockk()
        every { authRepository.getAccessToken() } returns flowOf("user-1")
        every { authRepository.getUserId() } returns flowOf("user-1")
        every { authRepository.getDeviceId() } returns flowOf("device-1")
        every { hlcClock.generate(any()) } returns "1636300202430-00000-device-1"
        // Default: orchestrator succeeds
        coEvery { syncOrchestrator.fullSync() } returns Result.success(Unit)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): DocumentListViewModel {
        return DocumentListViewModel(
            documentDao = documentDao,
            nodeDao = nodeDao,
            bookmarkDao = bookmarkDao,
            settingsDao = settingsDao,
            authRepository = authRepository,
            syncOrchestrator = syncOrchestrator,
            hlcClock = hlcClock
        )
    }

    @Test
    fun `triggerSync sets Syncing then Idle on success`() = runTest {
        every { documentDao.getAllDocuments("user-1") } returns flowOf(emptyList())
        coEvery { syncOrchestrator.fullSync() } returns Result.success(Unit)

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.triggerSync()
            expectState(DocumentListUiState.Success(syncStatus = SyncStatus.Syncing))
            expectState(DocumentListUiState.Success(syncStatus = SyncStatus.Idle))
        }
    }

    @Test
    fun `triggerSync sets Error status on network failure`() = runTest {
        every { documentDao.getAllDocuments("user-1") } returns flowOf(emptyList())
        coEvery { syncOrchestrator.fullSync() } returns Result.failure(IOException("Network error"))

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.triggerSync()
            expectState(DocumentListUiState.Success(syncStatus = SyncStatus.Syncing))
            expectState(DocumentListUiState.Success(syncStatus = SyncStatus.Error))
        }
    }

    @Test
    fun `onScreenResumed calls triggerSync`() = runTest {
        every { documentDao.getAllDocuments("user-1") } returns flowOf(emptyList())
        coEvery { syncOrchestrator.fullSync() } returns Result.success(Unit)

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.onScreenResumed()
            // If triggerSync was called, we should see the Syncing then Idle states
            expectState(DocumentListUiState.Success(syncStatus = SyncStatus.Syncing))
            expectState(DocumentListUiState.Success(syncStatus = SyncStatus.Idle))
        }
    }
}

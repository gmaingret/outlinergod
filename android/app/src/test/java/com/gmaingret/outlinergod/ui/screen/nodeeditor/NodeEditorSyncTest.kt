package com.gmaingret.outlinergod.ui.screen.nodeeditor

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.SavedStateHandle
import com.gmaingret.outlinergod.db.dao.BookmarkDao
import com.gmaingret.outlinergod.db.dao.DocumentDao
import com.gmaingret.outlinergod.db.dao.NodeDao
import com.gmaingret.outlinergod.db.dao.SettingsDao
import com.gmaingret.outlinergod.db.entity.NodeEntity
import com.gmaingret.outlinergod.db.entity.SettingsEntity
import com.gmaingret.outlinergod.network.model.SyncChangesResponse
import com.gmaingret.outlinergod.network.model.SyncConflicts
import com.gmaingret.outlinergod.network.model.SyncPushResponse
import com.gmaingret.outlinergod.repository.AuthRepository
import com.gmaingret.outlinergod.repository.FileRepository
import com.gmaingret.outlinergod.repository.SyncRepository
import com.gmaingret.outlinergod.sync.HlcClock
import com.gmaingret.outlinergod.ui.common.SyncStatus
import com.gmaingret.outlinergod.ui.mapper.mapToFlatList
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.orbitmvi.orbit.test.test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class NodeEditorSyncTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var nodeDao: NodeDao
    private lateinit var authRepository: AuthRepository
    private lateinit var hlcClock: HlcClock
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var syncRepository: SyncRepository
    private lateinit var documentDao: DocumentDao
    private lateinit var bookmarkDao: BookmarkDao
    private lateinit var settingsDao: SettingsDao
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var fileRepository: FileRepository

    private val testDeviceId = "device-1"
    private val testHlcValue = "1636300202430-00000-device-1"
    private val testDocumentId = "doc-1"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        nodeDao = mockk(relaxed = true)
        authRepository = mockk()
        hlcClock = mockk()
        syncRepository = mockk(relaxed = true)
        documentDao = mockk(relaxed = true)
        bookmarkDao = mockk(relaxed = true)
        settingsDao = mockk(relaxed = true)
        fileRepository = mockk(relaxed = true)
        dataStore = PreferenceDataStoreFactory.create(scope = testScope) {
            tempDir.newFile("test_prefs.preferences_pb")
        }
        savedStateHandle = SavedStateHandle(mapOf("documentId" to testDocumentId))
        every { authRepository.getDeviceId() } returns flowOf(testDeviceId)
        every { authRepository.getAccessToken() } returns flowOf(null)
        every { authRepository.getUserId() } returns flowOf("user-1")
        every { hlcClock.generate(any()) } returns testHlcValue
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeNode(
        id: String,
        content: String = "",
        parentId: String? = testDocumentId,
        sortOrder: String = "a0",
    ) = NodeEntity(
        id = id,
        documentId = testDocumentId,
        userId = "user-1",
        content = content,
        contentHlc = testHlcValue,
        note = "",
        noteHlc = "",
        parentId = parentId,
        parentIdHlc = "",
        sortOrder = sortOrder,
        sortOrderHlc = "",
        deviceId = testDeviceId,
        createdAt = 1000L,
        updatedAt = 1000L,
    )

    private fun fakePullResponse() = SyncChangesResponse(serverHlc = "AAAA")
    private fun fakePushResponse() = SyncPushResponse(serverHlc = "AAAA", conflicts = SyncConflicts())

    private fun setupSyncSuccess() {
        coEvery { syncRepository.pull(any(), any()) } returns Result.success(fakePullResponse())
        coEvery { syncRepository.push(any()) } returns Result.success(fakePushResponse())
    }

    private fun createViewModel(): NodeEditorViewModel {
        return NodeEditorViewModel(
            savedStateHandle = savedStateHandle,
            nodeDao = nodeDao,
            authRepository = authRepository,
            hlcClock = hlcClock,
            syncRepository = syncRepository,
            documentDao = documentDao,
            bookmarkDao = bookmarkDao,
            settingsDao = settingsDao,
            dataStore = dataStore,
            fileRepository = fileRepository,
        )
    }

    @Test
    fun `onScreenResumed triggersSyncImmediately`() = runTest {
        setupSyncSuccess()
        val nodes = listOf(fakeNode(id = "n1", content = "Hello"))
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.onScreenResumed()
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes, syncStatus = SyncStatus.Syncing))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes, syncStatus = SyncStatus.Idle))
        }

        coVerify(atLeast = 1) { syncRepository.pull(any(), any()) }
    }

    @Test
    fun `onScreenResumed startsInactivityTimer 30s`() = runTest {
        setupSyncSuccess()
        val nodes = listOf(fakeNode(id = "n1", content = "Hello"))
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.onScreenResumed()
            // Initial sync states
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes, syncStatus = SyncStatus.Syncing))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes, syncStatus = SyncStatus.Idle))
        }

        // After initial sync, verify pull was called once
        coVerify(exactly = 1) { syncRepository.pull(any(), any()) }

        // Advance to just before timer fires
        testDispatcher.scheduler.advanceTimeBy(29_999)
        testDispatcher.scheduler.runCurrent()
        coVerify(exactly = 1) { syncRepository.pull(any(), any()) }

        // Advance past 30s — timer fires
        testDispatcher.scheduler.advanceTimeBy(2)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 2) { syncRepository.pull(any(), any()) }
    }

    @Test
    fun `onContentChanged resetsInactivityTimer`() = runTest {
        setupSyncSuccess()
        val nodes = listOf(fakeNode(id = "n1", content = "old"))
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        coEvery { nodeDao.getNodesByDocumentSync(testDocumentId) } returns nodes
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.onScreenResumed()
            // Initial sync states
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes, syncStatus = SyncStatus.Syncing))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes, syncStatus = SyncStatus.Idle))
        }

        coVerify(exactly = 1) { syncRepository.pull(any(), any()) }

        // Advance 15s into the timer
        testDispatcher.scheduler.advanceTimeBy(15_000)
        testDispatcher.scheduler.runCurrent()

        // Reset the timer via content change
        viewModel.onContentChanged("n1", "x")

        // Advance 29_999ms from reset point (total = 15s + 29.999s = 44.999s)
        testDispatcher.scheduler.advanceTimeBy(29_999)
        testDispatcher.scheduler.runCurrent()
        // Still only 1 pull — timer was reset
        coVerify(exactly = 1) { syncRepository.pull(any(), any()) }

        // Advance 1ms more — timer fires at 30s from reset
        testDispatcher.scheduler.advanceTimeBy(2)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 2) { syncRepository.pull(any(), any()) }
    }

    @Test
    fun `onScreenPaused cancelsInactivityTimer`() = runTest {
        setupSyncSuccess()
        val nodes = listOf(fakeNode(id = "n1", content = "Hello"))
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.onScreenResumed()
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes, syncStatus = SyncStatus.Syncing))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes, syncStatus = SyncStatus.Idle))
        }

        coVerify(exactly = 1) { syncRepository.pull(any(), any()) }

        // Pause the screen — should cancel the inactivity timer
        viewModel.onScreenPaused()

        // Advance well past 30s
        testDispatcher.scheduler.advanceTimeBy(60_000)
        testDispatcher.scheduler.advanceUntilIdle()

        // Still only 1 pull — timer was cancelled
        coVerify(exactly = 1) { syncRepository.pull(any(), any()) }
    }

    @Test
    fun `triggerSync setsSyncingThenIdle inUiState`() = runTest {
        setupSyncSuccess()
        val nodes = listOf(fakeNode(id = "n1", content = "Hello"))
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.onScreenResumed()
            // Verify state transitions: Syncing then Idle
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes, syncStatus = SyncStatus.Syncing))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes, syncStatus = SyncStatus.Idle))
        }
    }

    @Test
    fun `syncFailure setsSyncStatusError noCrash`() = runTest {
        coEvery { syncRepository.pull(any(), any()) } returns Result.failure(IOException("Network error"))
        val nodes = listOf(fakeNode(id = "n1", content = "Hello"))
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.onScreenResumed()
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes, syncStatus = SyncStatus.Syncing))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes, syncStatus = SyncStatus.Error))
        }

        // Verify the ViewModel is still functional — no crash
        assertEquals(NodeEditorStatus.Success, viewModel.container.stateFlow.value.status)
    }

    @Test
    fun `triggerSync includes settings in push when pending`() = runTest {
        setupSyncSuccess()
        val nodes = listOf(fakeNode(id = "n1", content = "Hello"))
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)
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

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.onScreenResumed()
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes, syncStatus = SyncStatus.Syncing))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes, syncStatus = SyncStatus.Idle))
        }
        coVerify {
            syncRepository.push(match { payload ->
                payload.settings != null && payload.settings!!.id == "user-1"
            })
        }
    }

    @Test
    fun `triggerSync omits settings from push when no pending`() = runTest {
        setupSyncSuccess()
        val nodes = listOf(fakeNode(id = "n1", content = "Hello"))
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)
        coEvery { settingsDao.getPendingSettings(any(), any(), any()) } returns null

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.onScreenResumed()
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes, syncStatus = SyncStatus.Syncing))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes, syncStatus = SyncStatus.Idle))
        }
        coVerify {
            syncRepository.push(match { payload ->
                payload.settings == null
            })
        }
    }
}

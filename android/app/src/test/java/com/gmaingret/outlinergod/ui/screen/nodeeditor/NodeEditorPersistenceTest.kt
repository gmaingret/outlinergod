package com.gmaingret.outlinergod.ui.screen.nodeeditor

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.SavedStateHandle
import com.gmaingret.outlinergod.db.dao.BookmarkDao
import com.gmaingret.outlinergod.db.dao.DocumentDao
import com.gmaingret.outlinergod.db.dao.NodeDao
import com.gmaingret.outlinergod.db.dao.SettingsDao
import com.gmaingret.outlinergod.db.entity.NodeEntity
import com.gmaingret.outlinergod.repository.AuthRepository
import com.gmaingret.outlinergod.repository.SyncRepository
import com.gmaingret.outlinergod.sync.HlcClock
import com.gmaingret.outlinergod.ui.mapper.mapToFlatList
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.orbitmvi.orbit.test.test

@OptIn(ExperimentalCoroutinesApi::class)
class NodeEditorPersistenceTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var nodeDao: NodeDao
    private lateinit var authRepository: AuthRepository
    private lateinit var hlcClock: HlcClock
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var syncRepository: SyncRepository
    private lateinit var documentDao: DocumentDao
    private lateinit var bookmarkDao: BookmarkDao
    private lateinit var settingsDao: SettingsDao
    private lateinit var dataStore: DataStore<Preferences>

    private val testDeviceId = "device-1"
    private val testHlcValue = "0000017b05a3a1be-0000-device-1"
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
        dataStore = mockk(relaxed = true)
        savedStateHandle = SavedStateHandle(mapOf("documentId" to testDocumentId))
        every { authRepository.getDeviceId() } returns flowOf(testDeviceId)
        every { hlcClock.generate(any()) } returns testHlcValue
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeNode(
        id: String,
        content: String = "",
        note: String = "",
        parentId: String? = testDocumentId,
        sortOrder: String = "aP",
    ) = NodeEntity(
        id = id,
        documentId = testDocumentId,
        userId = "user-1",
        content = content,
        contentHlc = testHlcValue,
        note = note,
        noteHlc = "",
        parentId = parentId,
        parentIdHlc = "",
        sortOrder = sortOrder,
        sortOrderHlc = "",
        deviceId = testDeviceId,
        createdAt = 1000L,
        updatedAt = 1000L,
    )

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
        )
    }

    private fun setupNodeDao(nodes: List<NodeEntity>) {
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        coEvery { nodeDao.getNodesByDocumentSync(testDocumentId) } returns nodes
        coEvery { nodeDao.updateNode(any()) } just Runs
    }

    @Test
    fun `onContentChanged updatesInMemoryStateImmediately`() = runTest {
        val nodes = listOf(fakeNode(id = "n1", content = "old", sortOrder = "a0"))
        setupNodeDao(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.onContentChanged("n1", "new content")
            val state = awaitState()
            val node = state.flatNodes.first { it.entity.id == "n1" }
            assertEquals("new content", node.entity.content)
        }
    }

    @Test
    fun `onContentChanged doesNotWriteToRoom before300ms`() = runTest {
        val nodes = listOf(fakeNode(id = "n1", content = "old", sortOrder = "a0"))
        setupNodeDao(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.onContentChanged("n1", "new content")
            awaitState() // consume optimistic update
            testDispatcher.scheduler.advanceTimeBy(299)
            testDispatcher.scheduler.runCurrent()
        }

        coVerify(exactly = 0) { nodeDao.updateNode(any()) }
    }

    @Test
    fun `onContentChanged writesToRoom after300ms`() = runTest {
        val nodes = listOf(fakeNode(id = "n1", content = "old", sortOrder = "a0"))
        setupNodeDao(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.onContentChanged("n1", "new content")
            awaitState() // consume optimistic update
            testDispatcher.scheduler.advanceTimeBy(301)
            testDispatcher.scheduler.runCurrent()
        }

        coVerify(exactly = 1) { nodeDao.updateNode(match { it.content == "new content" }) }
    }

    @Test
    fun `onContentChanged rapidSuccession coalescesToSingleWrite`() = runTest {
        val nodes = listOf(fakeNode(id = "n1", content = "old", sortOrder = "a0"))
        setupNodeDao(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.onContentChanged("n1", "a")
            awaitState()
            testDispatcher.scheduler.advanceTimeBy(100)
            testDispatcher.scheduler.runCurrent()

            containerHost.onContentChanged("n1", "ab")
            awaitState()
            testDispatcher.scheduler.advanceTimeBy(100)
            testDispatcher.scheduler.runCurrent()

            containerHost.onContentChanged("n1", "abc")
            awaitState()
            testDispatcher.scheduler.advanceTimeBy(301)
            testDispatcher.scheduler.runCurrent()
        }

        coVerify(exactly = 1) { nodeDao.updateNode(match { it.content == "abc" }) }
    }

    @Test
    fun `onContentChanged stampsHlcTimestamp onWrite`() = runTest {
        val nodes = listOf(fakeNode(id = "n1", content = "old", sortOrder = "a0"))
        setupNodeDao(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.onContentChanged("n1", "new")
            awaitState()
            testDispatcher.scheduler.advanceTimeBy(301)
            testDispatcher.scheduler.runCurrent()
        }

        coVerify {
            nodeDao.updateNode(match { it.contentHlc == testHlcValue })
        }
    }

    @Test
    fun `onNodeFocusLost flushesDebounce immediately`() = runTest {
        val nodes = listOf(fakeNode(id = "n1", content = "old", sortOrder = "a0"))
        setupNodeDao(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.onContentChanged("n1", "flushed")
            awaitState()
            // Don't advance past debounce — instead call focusLost
            testDispatcher.scheduler.advanceTimeBy(10)
            testDispatcher.scheduler.runCurrent()

            containerHost.onNodeFocusLost("n1")
            testDispatcher.scheduler.advanceTimeBy(1)
            testDispatcher.scheduler.runCurrent()
        }

        coVerify { nodeDao.updateNode(match { it.content == "flushed" }) }
    }

    @Test
    fun `onNoteChanged writesToNoteHlc column`() = runTest {
        val nodes = listOf(fakeNode(id = "n1", content = "stuff", sortOrder = "a0"))
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        coEvery { nodeDao.getNodesByDocumentSync(testDocumentId) } returns nodes
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)

        val updateSlot = slot<NodeEntity>()
        coEvery { nodeDao.updateNode(capture(updateSlot)) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.onNoteChanged("n1", "my note")
            awaitState()
            testDispatcher.scheduler.advanceTimeBy(301)
            testDispatcher.scheduler.runCurrent()
        }

        assertTrue("noteHlc should be non-blank", updateSlot.captured.noteHlc.isNotBlank())
        assertEquals("my note", updateSlot.captured.note)
    }
}

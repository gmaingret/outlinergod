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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.orbitmvi.orbit.test.test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteEditorTest {

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
        dataStore = mockk(relaxed = true)
        savedStateHandle = SavedStateHandle(mapOf("documentId" to testDocumentId))
        every { authRepository.getDeviceId() } returns flowOf(testDeviceId)
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
    fun `toggleNote adds nodeId to expandedNoteIds`() = runTest {
        val nodes = listOf(fakeNode(id = "n1", content = "test", sortOrder = "a0"))
        setupNodeDao(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.toggleNote("n1")
            val state = awaitState()
            assertTrue("n1 should be in expandedNoteIds", "n1" in state.expandedNoteIds)
        }
    }

    @Test
    fun `toggleNote again removes nodeId from expandedNoteIds`() = runTest {
        val nodes = listOf(fakeNode(id = "n1", content = "test", sortOrder = "a0"))
        setupNodeDao(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.toggleNote("n1")
            val state1 = awaitState()
            assertTrue("n1 should be in expandedNoteIds", "n1" in state1.expandedNoteIds)

            containerHost.toggleNote("n1")
            val state2 = awaitState()
            assertFalse("n1 should be removed from expandedNoteIds", "n1" in state2.expandedNoteIds)
        }
    }

    @Test
    fun `noteWithContent isAlwaysExpanded via isNoteExpanded logic`() = runTest {
        // A node with existing note content should be treated as "expanded"
        // even without being in expandedNoteIds. This is computed in the screen:
        //   isNoteExpanded = nodeId in expandedNoteIds || entity.note.isNotBlank()
        // Here we verify the expandedNoteIds doesn't contain the node, but note has content.
        val nodes = listOf(fakeNode(id = "n1", content = "test", note = "existing note", sortOrder = "a0"))
        setupNodeDao(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            // expandedNoteIds is empty, but the node has note content
            // We verify state directly below after the test block
        }

        // The isNoteExpanded logic is:  flatNode.entity.note.isNotBlank() -> true
        // Verify the flat node has the note
        val state = viewModel.container.stateFlow.value
        val flatNode = state.flatNodes.first { it.entity.id == "n1" }
        assertEquals("existing note", flatNode.entity.note)
        // expandedNoteIds does NOT contain n1 — expansion is driven by note content
        assertFalse("expandedNoteIds should not contain n1", "n1" in state.expandedNoteIds)
        // The screen composable computes: isNoteExpanded = nodeId in expandedNoteIds || entity.note.isNotBlank()
        // So isNotBlank() == true means note field is shown
        assertTrue("note should be non-blank", flatNode.entity.note.isNotBlank())
    }

    @Test
    fun `onNoteChanged writes to note field not content field`() = runTest {
        val nodes = listOf(fakeNode(id = "n1", content = "original content", sortOrder = "a0"))
        setupNodeDao(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)

        val updateSlot = slot<NodeEntity>()
        coEvery { nodeDao.updateNode(capture(updateSlot)) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.onNoteChanged("n1", "my note")
            awaitState() // consume optimistic update
            testDispatcher.scheduler.advanceTimeBy(301)
            testDispatcher.scheduler.runCurrent()
        }

        assertEquals("my note", updateSlot.captured.note)
        assertEquals("original content", updateSlot.captured.content)
    }

    @Test
    fun `toggleNote on multiple nodes tracks them independently`() = runTest {
        val nodes = listOf(
            fakeNode(id = "n1", content = "first", sortOrder = "a0"),
            fakeNode(id = "n2", content = "second", sortOrder = "a1"),
        )
        setupNodeDao(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.toggleNote("n1")
            val state1 = awaitState()
            assertTrue("n1 should be expanded", "n1" in state1.expandedNoteIds)
            assertFalse("n2 should not be expanded", "n2" in state1.expandedNoteIds)

            containerHost.toggleNote("n2")
            val state2 = awaitState()
            assertTrue("n1 should still be expanded", "n1" in state2.expandedNoteIds)
            assertTrue("n2 should now be expanded", "n2" in state2.expandedNoteIds)

            containerHost.toggleNote("n1")
            val state3 = awaitState()
            assertFalse("n1 should be collapsed", "n1" in state3.expandedNoteIds)
            assertTrue("n2 should still be expanded", "n2" in state3.expandedNoteIds)
        }
    }
}

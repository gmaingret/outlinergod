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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.orbitmvi.orbit.test.test

@OptIn(ExperimentalCoroutinesApi::class)
class NodeEditorViewModelTest {

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

    @Test
    fun `loadDocument setsSuccessStatus withFlatNodes`() = runTest {
        val nodes = listOf(
            fakeNode(id = "n0", content = "First", sortOrder = "a0"),
            fakeNode(id = "n1", content = "Second", sortOrder = "a1"),
        )
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))
        }
    }

    @Test
    fun `loadDocument empty inserts root node and does not emit empty success`() = runTest {
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(emptyList())
        every { authRepository.getAccessToken() } returns flowOf("user-1")
        coEvery { nodeDao.insertNode(any()) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectNoItems()
        }

        coVerify { nodeDao.insertNode(any()) }
    }

    @Test
    fun `onEnterPressed splitContent atCursorPosition`() = runTest {
        val nodes = listOf(
            fakeNode(id = "n1", content = "HelloWorld", sortOrder = "a0"),
        )
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)

        val updateSlot = slot<NodeEntity>()
        val insertSlot = slot<NodeEntity>()
        coEvery { nodeDao.updateNode(capture(updateSlot)) } just Runs
        coEvery { nodeDao.insertNode(capture(insertSlot)) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.onEnterPressed("n1", 5)
            // Consume the focusedNodeId state change (UUID is unpredictable)
            awaitState()
        }

        assertEquals("Hello", updateSlot.captured.content)
        assertEquals("World", insertSlot.captured.content)
    }

    @Test
    fun `onEnterPressed atStart insertsBlankNodeAbove`() = runTest {
        val nodes = listOf(
            fakeNode(id = "n1", content = "ABC", sortOrder = "a0"),
        )
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)

        val insertSlot = slot<NodeEntity>()
        coEvery { nodeDao.insertNode(capture(insertSlot)) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.onEnterPressed("n1", 0)
            awaitState()
        }

        assertEquals("", insertSlot.captured.content)
        coVerify(exactly = 0) { nodeDao.updateNode(any()) }
    }

    @Test
    fun `onEnterPressed setsFocusedNodeId toNewNode`() = runTest {
        val nodes = listOf(
            fakeNode(id = "n1", content = "HelloWorld", sortOrder = "a0"),
        )
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)
        coEvery { nodeDao.updateNode(any()) } just Runs
        coEvery { nodeDao.insertNode(any()) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.onEnterPressed("n1", 5)
            val focusState = awaitState()
            assertNotNull("focusedNodeId should be set", focusState.focusedNodeId)
            assertTrue("focusedNodeId should not be original node", focusState.focusedNodeId != "n1")
        }
    }

    @Test
    fun `onBackspaceOnEmptyNode softDeletesNode`() = runTest {
        val nodes = listOf(
            fakeNode(id = "n0", content = "First", sortOrder = "a0"),
            fakeNode(id = "n1", content = "", note = "", sortOrder = "a1"),
        )
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)
        coEvery { nodeDao.softDeleteNode(any(), any(), any(), any()) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.onBackspaceOnEmptyNode("n1")
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes, focusedNodeId = "n0"))
        }

        coVerify { nodeDao.softDeleteNode(eq("n1"), any(), any(), any()) }
    }

    @Test
    fun `onBackspaceOnEmptyNode setsFocusToPrecedingNode`() = runTest {
        val nodes = listOf(
            fakeNode(id = "n0", content = "First", sortOrder = "a0"),
            fakeNode(id = "n1", content = "", note = "", sortOrder = "a1"),
        )
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)
        coEvery { nodeDao.softDeleteNode(any(), any(), any(), any()) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.onBackspaceOnEmptyNode("n1")
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes, focusedNodeId = "n0"))
        }
    }

    @Test
    fun `onBackspaceOnNonEmptyNode doesNotDelete`() = runTest {
        val nodes = listOf(
            fakeNode(id = "n1", content = "Not empty", sortOrder = "a0"),
        )
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.onBackspaceOnEmptyNode("n1")
            // No state change expected for non-empty node
        }

        coVerify(exactly = 0) { nodeDao.softDeleteNode(any(), any(), any(), any()) }
    }

    @Test
    fun `moveFocus Down advancesFocusIndex`() = runTest {
        val nodes = listOf(
            fakeNode(id = "n0", content = "A", sortOrder = "a0"),
            fakeNode(id = "n1", content = "B", sortOrder = "a1"),
            fakeNode(id = "n2", content = "C", sortOrder = "a2"),
        )
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            // From no focus (index -1), Down should go to index 0
            containerHost.moveFocus(FocusDirection.Down)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes, focusedNodeId = "n0"))

            containerHost.moveFocus(FocusDirection.Down)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes, focusedNodeId = "n1"))
        }
    }

    @Test
    fun `moveFocus Up atFirstNode doesNothing`() = runTest {
        val nodes = listOf(
            fakeNode(id = "n0", content = "A", sortOrder = "a0"),
            fakeNode(id = "n1", content = "B", sortOrder = "a1"),
        )
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            // Set focus to n0 first
            containerHost.moveFocus(FocusDirection.Down)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes, focusedNodeId = "n0"))

            // Try moving up from first node - identical state, so orbit-test
            // deduplicates. Verify no additional state emission.
            containerHost.moveFocus(FocusDirection.Up)
            expectNoItems()
        }

        // Double-check via stateFlow
        assertEquals("n0", viewModel.container.stateFlow.value.focusedNodeId)
    }
}

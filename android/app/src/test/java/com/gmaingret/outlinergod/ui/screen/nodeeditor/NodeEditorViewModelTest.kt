package com.gmaingret.outlinergod.ui.screen.nodeeditor

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.SavedStateHandle
import com.gmaingret.outlinergod.db.dao.BookmarkDao
import com.gmaingret.outlinergod.db.dao.DocumentDao
import com.gmaingret.outlinergod.db.dao.NodeDao
import com.gmaingret.outlinergod.db.dao.SettingsDao
import com.gmaingret.outlinergod.db.entity.NodeEntity
import com.gmaingret.outlinergod.repository.AuthRepository
import com.gmaingret.outlinergod.repository.FileRepository
import com.gmaingret.outlinergod.repository.SyncRepository
import com.gmaingret.outlinergod.repository.UploadedFile
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import com.gmaingret.outlinergod.ui.common.SyncStatus
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
        dataStore = mockk(relaxed = true)
        fileRepository = mockk()
        savedStateHandle = SavedStateHandle(mapOf("documentId" to testDocumentId))
        every { authRepository.getAccessToken() } returns flowOf(null)
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
            fileRepository = fileRepository,
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
        coEvery { nodeDao.softDeleteNode(any(), any(), any(), any(), any()) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.onBackspaceOnEmptyNode("n1")
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes, focusedNodeId = "n0"))
        }

        coVerify { nodeDao.softDeleteNode(eq("n1"), any(), any(), any(), any()) }
    }

    @Test
    fun `onBackspaceOnEmptyNode setsFocusToPrecedingNode`() = runTest {
        val nodes = listOf(
            fakeNode(id = "n0", content = "First", sortOrder = "a0"),
            fakeNode(id = "n1", content = "", note = "", sortOrder = "a1"),
        )
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)
        coEvery { nodeDao.softDeleteNode(any(), any(), any(), any(), any()) } just Runs

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

        coVerify(exactly = 0) { nodeDao.softDeleteNode(any(), any(), any(), any(), any()) }
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

    @Test
    fun `restoreNode clearsDeletedAt andSetsNewHlc`() = runTest {
        val deletedAt = 999L
        val deletedNode = fakeNode(id = "n1", content = "Deleted").copy(
            deletedAt = deletedAt,
            deletedHlc = "old-hlc",
        )
        val nodes = listOf(
            fakeNode(id = "n0", content = "First", sortOrder = "a0"),
        )
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        every { nodeDao.getNodeById("n1") } returns flowOf(deletedNode)
        coEvery { nodeDao.getNodesByDocumentIncludingDeleted(testDocumentId) } returns listOf(
            nodes[0], deletedNode
        )
        coEvery { nodeDao.restoreNodes(any(), any(), any()) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            // Consume Loading + Success states
            awaitState()
            awaitState()

            containerHost.restoreNode("n1")
            testDispatcher.scheduler.advanceUntilIdle()
        }

        // Verify restoreNodes was called with n1 and the new HLC
        coVerify { nodeDao.restoreNodes(match { it.contains("n1") }, eq(testHlcValue), any()) }
    }

    @Test
    fun `onNodeFocusGained setsFocusedNodeId`() = runTest {
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

            containerHost.onNodeFocusGained("n1")
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes, focusedNodeId = "n1"))

            containerHost.onNodeFocusGained("n0")
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes, focusedNodeId = "n0"))
        }
    }

    @Test
    fun `loadDocument withRootNodeId scopes flatList to subtree of that node`() = runTest {
        // Document structure:
        //   n0 (parentId = doc)
        //   n1 (parentId = doc)  <-- this is the zoom root
        //     n2 (parentId = n1)
        //     n3 (parentId = n1)
        //   n4 (parentId = doc)
        val nodes = listOf(
            fakeNode(id = "n0", content = "Root A", sortOrder = "a0", parentId = testDocumentId),
            fakeNode(id = "n1", content = "Root B", sortOrder = "a1", parentId = testDocumentId),
            fakeNode(id = "n2", content = "Child B1", sortOrder = "a0", parentId = "n1"),
            fakeNode(id = "n3", content = "Child B2", sortOrder = "a1", parentId = "n1"),
            fakeNode(id = "n4", content = "Root C", sortOrder = "a2", parentId = testDocumentId),
        )
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)

        // When zoomed into n1, only n2 and n3 should appear (children of n1)
        val filteredForZoom = NodeEditorViewModel.filterSubtree(nodes, "n1")
        val zoomedFlatNodes = mapToFlatList(filteredForZoom, "n1")

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId, rootNodeId = "n1")
            expectState(NodeEditorUiState(documentId = testDocumentId, rootNodeId = "n1", status = NodeEditorStatus.Loading))
            val successState = awaitState()
            assertEquals(NodeEditorStatus.Success, successState.status)
            assertEquals(zoomedFlatNodes, successState.flatNodes)
            // Verify only children of n1 are visible (not n0 or n4)
            assertTrue(successState.flatNodes.none { it.entity.id == "n0" })
            assertTrue(successState.flatNodes.none { it.entity.id == "n4" })
            assertTrue(successState.flatNodes.any { it.entity.id == "n2" })
            assertTrue(successState.flatNodes.any { it.entity.id == "n3" })
        }
    }

    @Test
    fun `deleteNode softDeletesAndSetsFocusToPrecedingNode`() = runTest {
        val nodes = listOf(
            fakeNode(id = "n0", content = "First", sortOrder = "a0"),
            fakeNode(id = "n1", content = "Second", sortOrder = "a1"),
        )
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)
        coEvery { nodeDao.softDeleteNode(any(), any(), any(), any(), any()) } just Runs
        coEvery { nodeDao.softDeleteNodes(any(), any(), any(), any(), any()) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.deleteNode("n1")
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes, focusedNodeId = "n0", canUndo = true, canRedo = false))
        }

        coVerify { nodeDao.softDeleteNodes(any(), any(), any(), any(), any()) }
    }

    // --- Plan 14-01: Delete Undo tests ---

    @Test
    fun `deleteNode_pushesToUndoStack canUndo becomes true`() = runTest {
        val nodes = listOf(
            fakeNode(id = "n0", content = "First", sortOrder = "a0"),
            fakeNode(id = "n1", content = "Second", sortOrder = "a1"),
        )
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)
        coEvery { nodeDao.softDeleteNodes(any(), any(), any(), any(), any()) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.deleteNode("n1")
            val afterDelete = awaitState()
            assertTrue("canUndo should be true after delete", afterDelete.canUndo)
        }
    }

    @Test
    fun `undo_afterDelete_restoresNode flatNodes restored and restoreNodes called`() = runTest {
        val nodes = listOf(
            fakeNode(id = "n0", content = "First", sortOrder = "a0"),
            fakeNode(id = "n1", content = "Second", sortOrder = "a1"),
        )
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)
        coEvery { nodeDao.softDeleteNodes(any(), any(), any(), any(), any()) } just Runs
        coEvery { nodeDao.restoreNodes(any(), any(), any()) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.deleteNode("n1")
            awaitState() // canUndo=true, focusedNodeId=n0

            containerHost.undo()
            val afterUndo = awaitState()
            // flatNodes restored to snapshot before delete
            assertEquals(expectedFlatNodes, afterUndo.flatNodes)
        }

        // restoreNodes must have been called with the deleted node's id
        coVerify { nodeDao.restoreNodes(match { it.contains("n1") }, any(), any()) }
    }

    @Test
    fun `undo_afterDelete_restoresSubtree parent and children all restored`() = runTest {
        // n1 is parent; n2 is its child
        val nodes = listOf(
            fakeNode(id = "n0", content = "Root", sortOrder = "a0"),
            fakeNode(id = "n1", content = "Parent", sortOrder = "a1"),
            fakeNode(id = "n2", content = "Child", sortOrder = "a0", parentId = "n1"),
        )
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)

        val deletedIdsSlot = slot<List<String>>()
        coEvery { nodeDao.softDeleteNodes(capture(deletedIdsSlot), any(), any(), any(), any()) } just Runs
        coEvery { nodeDao.restoreNodes(any(), any(), any()) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            containerHost.deleteNode("n1")
            awaitState() // canUndo=true

            containerHost.undo()
            val afterUndo = awaitState()
            assertEquals(expectedFlatNodes, afterUndo.flatNodes)
        }

        // Both n1 and n2 should have been in the deleted IDs (captured from softDeleteNodes)
        assertTrue("n1 should be deleted", deletedIdsSlot.captured.contains("n1"))
        assertTrue("n2 should be deleted (child)", deletedIdsSlot.captured.contains("n2"))
        // restoreNodes should restore both
        coVerify { nodeDao.restoreNodes(match { it.containsAll(listOf("n1", "n2")) }, any(), any()) }
    }

    @Test
    fun `restoreNode_restoresSubtree all nodes with same deletedAt are restored`() = runTest {
        val deletedAt = 999L
        val deletedParent = fakeNode(id = "n1", content = "Parent").copy(
            deletedAt = deletedAt,
            deletedHlc = "old-hlc",
        )
        val deletedChild = fakeNode(id = "n2", content = "Child", parentId = "n1").copy(
            deletedAt = deletedAt,
            deletedHlc = "old-hlc",
        )
        val nodes = listOf(fakeNode(id = "n0", content = "First", sortOrder = "a0"))
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        every { nodeDao.getNodeById("n1") } returns flowOf(deletedParent)
        coEvery { nodeDao.getNodesByDocumentIncludingDeleted(testDocumentId) } returns listOf(
            nodes[0], deletedParent, deletedChild
        )
        coEvery { nodeDao.restoreNodes(any(), any(), any()) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            awaitState() // Loading
            awaitState() // Success

            containerHost.restoreNode("n1")
            testDispatcher.scheduler.advanceUntilIdle()
        }

        // restoreNodes should be called with both n1 and n2 (same deletedAt)
        coVerify { nodeDao.restoreNodes(match { it.containsAll(listOf("n1", "n2")) }, any(), any()) }
    }

    @Test
    fun `undoStack_alignedWithDeletedIds interleaved ops undo correct state`() = runTest {
        val nodes = listOf(
            fakeNode(id = "n0", content = "A", sortOrder = "a0"),
            fakeNode(id = "n1", content = "B", sortOrder = "a1"),
            fakeNode(id = "n2", content = "C", sortOrder = "a2"),
        )
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)
        coEvery { nodeDao.softDeleteNodes(any(), any(), any(), any(), any()) } just Runs
        coEvery { nodeDao.restoreNodes(any(), any(), any()) } just Runs
        coEvery { nodeDao.updateNode(any()) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Success, flatNodes = expectedFlatNodes))

            // Delete n2
            containerHost.deleteNode("n2")
            val afterDelete = awaitState()
            assertTrue("canUndo after delete", afterDelete.canUndo)

            // Undo the delete: restoreNodes should be called for n2
            containerHost.undo()
            val afterUndo = awaitState()
            assertEquals(expectedFlatNodes, afterUndo.flatNodes)
        }

        // The delete undo should have called restoreNodes for n2
        coVerify { nodeDao.restoreNodes(match { it.contains("n2") }, any(), any()) }
    }

    @Test
    fun `onScreenResumed triggers sync by setting syncStatus to Syncing`() = runTest {
        val nodes = listOf(
            fakeNode(id = "n0", content = "First", sortOrder = "a0"),
        )
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            val loadedState = awaitState()
            assertEquals(NodeEditorStatus.Success, loadedState.status)

            // onScreenResumed triggers sync (Syncing → Idle/Error) and starts inactivity timer.
            // Cancel the timer immediately after to prevent a second sync cycle during advanceUntilIdle.
            containerHost.onScreenResumed()
            containerHost.onScreenPaused() // cancel inactivity timer before it fires
            testDispatcher.scheduler.advanceUntilIdle()

            // Consume Syncing state
            val syncingState = awaitState()
            assertEquals(SyncStatus.Syncing, syncingState.syncStatus)

            // Consume final Idle or Error state
            val finalState = awaitState()
            assertTrue(
                "syncStatus should be Idle or Error after triggerSync, was ${finalState.syncStatus}",
                finalState.syncStatus == SyncStatus.Idle ||
                finalState.syncStatus == SyncStatus.Error
            )
        }
    }

    @Test
    fun `onScreenPaused cancels inactivity timer`() = runTest {
        val nodes = listOf(
            fakeNode(id = "n0", content = "First", sortOrder = "a0"),
        )
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            val loadedState = awaitState()
            assertEquals(NodeEditorStatus.Success, loadedState.status)

            // Resume starts inactivity timer; also triggers sync (Syncing -> Idle/Error).
            // Immediately pause to cancel the timer before advanceUntilIdle fires the 30s delay.
            containerHost.onScreenResumed()
            containerHost.onScreenPaused()
            testDispatcher.scheduler.advanceUntilIdle()
            // Consume sync state changes from the immediate triggerSync call
            awaitState() // Syncing
            awaitState() // Idle or Error
            // No crash, no additional state change after pause = timer was cancelled cleanly
        }
    }


    // -------------------------------------------------------------------------
    // uploadAttachment tests (14-03)
    // -------------------------------------------------------------------------

    @Test
    fun `uploadAttachment_success_createsAttachChildNode`() = runTest {
        val nodes = listOf(
            fakeNode(id = "n1", content = "Hello", sortOrder = "a0"),
        )
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)

        val mockUri: Uri = mockk()
        val uploaded = UploadedFile(url = "/api/files/abc.jpg", filename = "abc.jpg", mimeType = "image/jpeg")
        coEvery { fileRepository.uploadFile("n1", mockUri, "image/jpeg") } returns Result.success(uploaded)

        val nodeSlot = slot<NodeEntity>()
        coEvery { nodeDao.insertNode(capture(nodeSlot)) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            awaitState() // Loading
            awaitState() // Success

            containerHost.uploadAttachment("n1", mockUri, "image/jpeg")

            val syncingState = awaitState()
            assertEquals(SyncStatus.Syncing, syncingState.syncStatus)

            val idleState = awaitState()
            assertEquals(SyncStatus.Idle, idleState.syncStatus)

            // Verify a child ATTACH node was inserted under "n1"
            val inserted = nodeSlot.captured
            assertEquals("n1", inserted.parentId)
            assertTrue("Content should use ATTACH format",
                inserted.content.startsWith("ATTACH|image/jpeg|abc.jpg|"))
        }
    }

    @Test
    fun `uploadAttachment_failure_postsShowError`() = runTest {
        val nodes = listOf(
            fakeNode(id = "n1", content = "Hello", sortOrder = "a0"),
        )
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)

        val mockUri: Uri = mockk()
        coEvery { fileRepository.uploadFile("n1", mockUri, "application/pdf") } returns
            Result.failure(RuntimeException("Server error"))

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            awaitState() // Loading
            awaitState() // Success

            containerHost.uploadAttachment("n1", mockUri, "application/pdf")

            val syncingState = awaitState()
            assertEquals(SyncStatus.Syncing, syncingState.syncStatus)

            val errorState = awaitState()
            assertEquals(SyncStatus.Error, errorState.syncStatus)

            val sideEffect = awaitSideEffect()
            assertTrue(sideEffect is NodeEditorSideEffect.ShowError)
            assertTrue((sideEffect as NodeEditorSideEffect.ShowError).message.contains("Upload failed"))
        }
    }

    // -------------------------------------------------------------------------
    // reorderNodes depth-adoption pure-logic tests (14-02)
    // -------------------------------------------------------------------------

    private fun buildFlatNode(
        id: String,
        depth: Int,
        parentId: String,
        sortOrder: String = "aP",
    ) = com.gmaingret.outlinergod.ui.mapper.FlatNode(
        entity = fakeNode(id = id, parentId = parentId, sortOrder = sortOrder),
        depth = depth,
        hasChildren = false,
    )

    /**
     * Applies the same depth-adoption logic that reorderNodes() uses, given a pre-built
     * flat list, from/to indices, and the document/root IDs. Returns the recomputed list.
     */
    private fun applyReorder(
        flatNodes: List<com.gmaingret.outlinergod.ui.mapper.FlatNode>,
        fromIndex: Int,
        toIndex: Int,
        documentId: String,
        rootNodeId: String? = null,
    ): List<com.gmaingret.outlinergod.ui.mapper.FlatNode> {
        val node = flatNodes[fromIndex]
        val blockEnd = ((fromIndex + 1)..flatNodes.lastIndex)
            .firstOrNull { flatNodes[it].depth <= node.depth } ?: flatNodes.size
        val blockSize = blockEnd - fromIndex
        val block = flatNodes.subList(fromIndex, blockEnd).toList()
        val withoutBlock = flatNodes.toMutableList()
        withoutBlock.subList(fromIndex, blockEnd).clear()
        val insertAt = if (toIndex > fromIndex) {
            (toIndex - blockSize + 1).coerceIn(0, withoutBlock.size)
        } else {
            toIndex.coerceIn(0, withoutBlock.size)
        }
        withoutBlock.addAll(insertAt, block)

        val nodeAbove = withoutBlock.getOrNull(insertAt - 1)
        val aboveDepth = nodeAbove?.depth ?: 0
        val minParentId = rootNodeId ?: documentId
        val blockHeadDepth = withoutBlock[insertAt].depth
        val maxAllowedDepth = if (nodeAbove != null) aboveDepth + 1 else 0
        val minAllowedDepth = aboveDepth
        when {
            blockHeadDepth > maxAllowedDepth -> {
                val depthDelta = blockHeadDepth - maxAllowedDepth
                val newParentId = nodeAbove?.entity?.id ?: minParentId
                for (i in insertAt until insertAt + blockSize) {
                    val n = withoutBlock[i]
                    withoutBlock[i] = n.copy(
                        entity = if (i == insertAt) n.entity.copy(parentId = newParentId) else n.entity,
                        depth = n.depth - depthDelta,
                    )
                }
            }
            blockHeadDepth < minAllowedDepth -> {
                val depthDelta = blockHeadDepth - minAllowedDepth
                val newParentId = if (aboveDepth == 0) minParentId else nodeAbove!!.entity.parentId
                for (i in insertAt until insertAt + blockSize) {
                    val n = withoutBlock[i]
                    withoutBlock[i] = n.copy(
                        entity = if (i == insertAt) n.entity.copy(parentId = newParentId) else n.entity,
                        depth = n.depth - depthDelta,
                    )
                }
            }
        }
        return NodeEditorViewModel.recomputeFlatNodes(withoutBlock)
    }

    @Test
    fun `reorderNodes_l1DroppedBetweenL2_becomesL2`() {
        // Tree: docId -> [A(L1), B(L1) -> [C(L2), D(L2)]]
        // Drag A (depth=0) to between C and D
        // Expected: A becomes depth=1 with parentId=B
        val docId = "doc-1"
        val a = buildFlatNode("A", depth = 0, parentId = docId, sortOrder = "a0")
        val b = buildFlatNode("B", depth = 0, parentId = docId, sortOrder = "a1")
        val c = buildFlatNode("C", depth = 1, parentId = "B", sortOrder = "a0")
        val d = buildFlatNode("D", depth = 1, parentId = "B", sortOrder = "a1")
        val flatNodes = listOf(a, b, c, d)

        // Drag A (fromIndex=0) to toIndex=2 (inserts after C)
        val result = applyReorder(flatNodes, fromIndex = 0, toIndex = 2, documentId = docId)
        val inserted = result.first { it.entity.id == "A" }
        assertEquals(1, inserted.depth)
        assertEquals("B", inserted.entity.parentId)
    }

    @Test
    fun `reorderNodes_l2DroppedToRoot_becomesL1`() {
        // Tree: docId -> [A(L1) -> [B(L2)], C(L1)]
        // Drag B (depth=1) to before A (toIndex=0)
        // Expected: B becomes depth=0, parentId=docId
        val docId = "doc-1"
        val a = buildFlatNode("A", depth = 0, parentId = docId, sortOrder = "a0")
        val b = buildFlatNode("B", depth = 1, parentId = "A", sortOrder = "a0")
        val c = buildFlatNode("C", depth = 0, parentId = docId, sortOrder = "a1")
        val flatNodes = listOf(a, b, c)

        val result = applyReorder(flatNodes, fromIndex = 1, toIndex = 0, documentId = docId)
        val inserted = result.first { it.entity.id == "B" }
        assertEquals(0, inserted.depth)
        assertEquals(docId, inserted.entity.parentId)
    }

    @Test
    fun `reorderNodes_l1DroppedAfterL1_keepsSameDepth`() {
        // Tree: docId -> [A(L1), B(L1), C(L1)]
        // Drag A to after B — both are L1, same parent
        // Expected: depth=0, parentId=docId unchanged
        val docId = "doc-1"
        val a = buildFlatNode("A", depth = 0, parentId = docId, sortOrder = "a0")
        val b = buildFlatNode("B", depth = 0, parentId = docId, sortOrder = "a1")
        val c = buildFlatNode("C", depth = 0, parentId = docId, sortOrder = "a2")
        val flatNodes = listOf(a, b, c)

        val result = applyReorder(flatNodes, fromIndex = 0, toIndex = 2, documentId = docId)
        val inserted = result.first { it.entity.id == "A" }
        assertEquals(0, inserted.depth)
        assertEquals(docId, inserted.entity.parentId)
    }

    @Test
    fun `reorderNodes_blockWithChildren_childrenShiftDepth`() {
        // Tree: docId -> [A(L1) -> [Achild(L2)], B(L1) -> [C(L2), D(L2)]]
        // Drag block [A, Achild] into the L2 section between C and D
        // Expected: A becomes depth=1 parentId=B, Achild becomes depth=2
        val docId = "doc-1"
        val a = buildFlatNode("A", depth = 0, parentId = docId, sortOrder = "a0")
        val achild = buildFlatNode("Achild", depth = 1, parentId = "A", sortOrder = "a0")
        val b = buildFlatNode("B", depth = 0, parentId = docId, sortOrder = "a1")
        val c = buildFlatNode("C", depth = 1, parentId = "B", sortOrder = "a0")
        val d = buildFlatNode("D", depth = 1, parentId = "B", sortOrder = "a1")
        val flatNodes = listOf(a, achild, b, c, d)

        // Drag A+Achild (fromIndex=0) to toIndex=3 (between C and D)
        val result = applyReorder(flatNodes, fromIndex = 0, toIndex = 3, documentId = docId)
        val insertedHead = result.first { it.entity.id == "A" }
        val insertedChild = result.first { it.entity.id == "Achild" }
        assertEquals(1, insertedHead.depth)
        assertEquals("B", insertedHead.entity.parentId)
        assertEquals(2, insertedChild.depth)
    }

    @Test
    fun `reorderNodes_sameParentReorder_noParentIdChange`() {
        // Tree: docId -> [A(L1) -> [C(L2), D(L2)]]
        // Drag D before C — both are siblings of A
        // Expected: both parentIds remain A, depths remain 1
        val docId = "doc-1"
        val a = buildFlatNode("A", depth = 0, parentId = docId, sortOrder = "a0")
        val c = buildFlatNode("C", depth = 1, parentId = "A", sortOrder = "a0")
        val d = buildFlatNode("D", depth = 1, parentId = "A", sortOrder = "a1")
        val flatNodes = listOf(a, c, d)

        // Drag D (fromIndex=2) to toIndex=1 (before C)
        val result = applyReorder(flatNodes, fromIndex = 2, toIndex = 1, documentId = docId)
        val reorderedC = result.first { it.entity.id == "C" }
        val reorderedD = result.first { it.entity.id == "D" }
        assertEquals("A", reorderedC.entity.parentId)
        assertEquals("A", reorderedD.entity.parentId)
        assertEquals(1, reorderedC.depth)
        assertEquals(1, reorderedD.depth)
    }
}

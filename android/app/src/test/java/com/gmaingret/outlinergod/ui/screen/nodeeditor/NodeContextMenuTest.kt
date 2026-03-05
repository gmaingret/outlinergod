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
import com.gmaingret.outlinergod.repository.FileRepository
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
class NodeContextMenuTest {

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
        fileRepository = mockk(relaxed = true)
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
        completed: Int = 0,
        color: Int = 0,
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
        completed = completed,
        completedHlc = "",
        color = color,
        colorHlc = "",
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

    private fun setupNodeDao(nodes: List<NodeEntity>) {
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        coEvery { nodeDao.getNodesByDocumentSync(testDocumentId) } returns nodes
        coEvery { nodeDao.updateNode(any()) } just Runs
        coEvery { nodeDao.insertNode(any()) } just Runs
    }

    // Test 1: addChildNode inserts node with correct parentId
    @Test
    fun `addChildNode insertsNodeWithCorrectParentId`() = runTest {
        val nodes = listOf(fakeNode(id = "n1", content = "Parent", sortOrder = "a0"))
        setupNodeDao(nodes)

        val insertSlot = slot<NodeEntity>()
        coEvery { nodeDao.insertNode(capture(insertSlot)) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            awaitState() // Success

            containerHost.addChildNode("n1")
            awaitState() // focusedNodeId set
        }

        coVerify(exactly = 1) { nodeDao.insertNode(any()) }
        assertEquals("n1", insertSlot.captured.parentId)
        assertEquals("", insertSlot.captured.content)
        assertEquals(testDocumentId, insertSlot.captured.documentId)
    }

    // Test 2: onColorChanged writes color and colorHlc to Room
    @Test
    fun `onColorChanged writesColorAndColorHlc toRoom`() = runTest {
        val nodes = listOf(fakeNode(id = "n1", content = "Colored", sortOrder = "a0", color = 0))
        setupNodeDao(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)

        val updateSlot = slot<NodeEntity>()
        coEvery { nodeDao.updateNode(capture(updateSlot)) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(
                NodeEditorUiState(
                    documentId = testDocumentId,
                    status = NodeEditorStatus.Success,
                    flatNodes = expectedFlatNodes
                )
            )

            containerHost.onColorChanged("n1", 3)
            awaitState() // optimistic update
            testDispatcher.scheduler.advanceTimeBy(301)
            testDispatcher.scheduler.runCurrent()
        }

        assertEquals(3, updateSlot.captured.color)
        assertTrue("colorHlc should be a valid HLC", updateSlot.captured.colorHlc.isNotBlank())
        assertEquals(testHlcValue, updateSlot.captured.colorHlc)
    }

    // Test 3: onCompletedToggled flips completed bit from 0 to 1
    @Test
    fun `onCompletedToggled flipsCompletedBit`() = runTest {
        val nodes = listOf(fakeNode(id = "n1", content = "Task", sortOrder = "a0", completed = 0))
        setupNodeDao(nodes)

        val updateSlot = slot<NodeEntity>()
        coEvery { nodeDao.updateNode(capture(updateSlot)) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            awaitState() // Success

            containerHost.onCompletedToggled("n1")
            awaitState() // optimistic update
        }

        assertEquals(1, updateSlot.captured.completed)
    }

    // Test 4: onCompletedToggled flips back to zero
    @Test
    fun `onCompletedToggled flipsBackToZero`() = runTest {
        val nodes = listOf(fakeNode(id = "n1", content = "Task", sortOrder = "a0", completed = 1))
        setupNodeDao(nodes)

        val updateSlot = slot<NodeEntity>()
        coEvery { nodeDao.updateNode(capture(updateSlot)) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            awaitState() // Success

            containerHost.onCompletedToggled("n1")
            awaitState() // optimistic update
        }

        assertEquals(0, updateSlot.captured.completed)
    }

    // Test 5: deleteNode soft-deletes even non-empty nodes
    @Test
    fun `deleteNode softDeletesNonEmptyNode`() = runTest {
        val nodes = listOf(
            fakeNode(id = "n0", content = "First", sortOrder = "a0"),
            fakeNode(id = "n1", content = "Non-empty content", sortOrder = "a1"),
        )
        setupNodeDao(nodes)
        coEvery { nodeDao.softDeleteNodes(any(), any(), any(), any()) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            awaitState() // Success

            containerHost.deleteNode("n1")
            awaitState() // focusedNodeId set to preceding
        }

        coVerify { nodeDao.softDeleteNodes(eq(listOf("n1")), any(), any(), any()) }
    }

    // Test 6: deleteNode sets focus to preceding node
    @Test
    fun `deleteNode setsFocusToPrecedingNode`() = runTest {
        val nodes = listOf(
            fakeNode(id = "n0", content = "First", sortOrder = "a0"),
            fakeNode(id = "n1", content = "Second", sortOrder = "a1"),
        )
        setupNodeDao(nodes)
        val expectedFlatNodes = mapToFlatList(nodes, testDocumentId)
        coEvery { nodeDao.softDeleteNodes(any(), any(), any(), any()) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            expectState(
                NodeEditorUiState(
                    documentId = testDocumentId,
                    status = NodeEditorStatus.Success,
                    flatNodes = expectedFlatNodes
                )
            )

            containerHost.deleteNode("n1")
            expectState(
                NodeEditorUiState(
                    documentId = testDocumentId,
                    status = NodeEditorStatus.Success,
                    flatNodes = expectedFlatNodes,
                    focusedNodeId = "n0",
                    canUndo = true,
                    canRedo = false,
                )
            )
        }
    }

    // Test 7: deleteNode recursively deletes all descendants
    @Test
    fun `deleteNode recursivelyDeletesDescendants`() = runTest {
        // A > B > C  (B and C are children/grandchildren of A)
        val nodes = listOf(
            fakeNode(id = "nA", content = "A", sortOrder = "a0", parentId = testDocumentId),
            fakeNode(id = "nB", content = "B", sortOrder = "a0", parentId = "nA"),
            fakeNode(id = "nC", content = "C", sortOrder = "a0", parentId = "nB"),
        )
        setupNodeDao(nodes)
        val capturedIds = slot<List<String>>()
        coEvery { nodeDao.softDeleteNodes(capture(capturedIds), any(), any(), any()) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            awaitState() // Success

            containerHost.deleteNode("nB")
            awaitState() // focusedNodeId update
        }

        assertEquals(listOf("nB", "nC"), capturedIds.captured)
    }
}

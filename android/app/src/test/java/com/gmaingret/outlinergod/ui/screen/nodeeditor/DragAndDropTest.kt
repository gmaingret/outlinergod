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
import com.gmaingret.outlinergod.sync.SyncOrchestrator
import com.gmaingret.outlinergod.sync.HlcClock
import com.gmaingret.outlinergod.ui.mapper.FlatNode
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.orbitmvi.orbit.test.test

@OptIn(ExperimentalCoroutinesApi::class)
class DragAndDropTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var nodeDao: NodeDao
    private lateinit var authRepository: AuthRepository
    private lateinit var hlcClock: HlcClock
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var syncOrchestrator: SyncOrchestrator
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
        syncOrchestrator = mockk(relaxed = true)
        documentDao = mockk(relaxed = true)
        bookmarkDao = mockk(relaxed = true)
        settingsDao = mockk(relaxed = true)
        dataStore = mockk(relaxed = true)
        fileRepository = mockk(relaxed = true)
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
        parentId: String? = testDocumentId,
        sortOrder: String = "aP",
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

    private fun createViewModel(): NodeEditorViewModel {
        return NodeEditorViewModel(
            savedStateHandle = savedStateHandle,
            nodeDao = nodeDao,
            authRepository = authRepository,
            hlcClock = hlcClock,
            syncOrchestrator = syncOrchestrator,
            documentDao = documentDao,
            bookmarkDao = bookmarkDao,
            settingsDao = settingsDao,
            dataStore = dataStore,
            fileRepository = fileRepository,
        )
    }

    // Test 1: Vertical reorder same parent updates sort order
    @Test
    fun `reorderNodes verticalReorder sameParent updatesSortOrder`() = runTest {
        val nodes = listOf(
            fakeNode(id = "A", content = "A", sortOrder = "a0"),
            fakeNode(id = "B", content = "B", sortOrder = "a1"),
            fakeNode(id = "C", content = "C", sortOrder = "a2"),
        )
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        coEvery { nodeDao.updateNode(any()) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            val loadedState = awaitState()

            // Drop B below C: fromIndex=1, toIndex=2
            containerHost.reorderNodes(1, 2)
            val reorderedState = awaitState()

            // New order should be [A, C, B]
            assertEquals("A", reorderedState.flatNodes[0].entity.id)
            assertEquals("C", reorderedState.flatNodes[1].entity.id)
            assertEquals("B", reorderedState.flatNodes[2].entity.id)

            // B's sort order must be > C's sort order (lexicographically)
            assertTrue(
                "B.sortOrder (${reorderedState.flatNodes[2].entity.sortOrder}) must be > C.sortOrder (${reorderedState.flatNodes[1].entity.sortOrder})",
                reorderedState.flatNodes[2].entity.sortOrder > reorderedState.flatNodes[1].entity.sortOrder
            )
        }
    }

    // Test 2: Indent reparents under preceding sibling
    @Test
    fun `indentNode reparentsUnderPrecedingSibling`() = runTest {
        val nodes = listOf(
            fakeNode(id = "A", content = "A", sortOrder = "a0"),
            fakeNode(id = "B", content = "B", sortOrder = "a1"),
        )
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        coEvery { nodeDao.updateNode(any()) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            awaitState()

            containerHost.indentNode("B")
            val indentedState = awaitState()

            val bNode = indentedState.flatNodes.first { it.entity.id == "B" }
            assertEquals("B parentId should be A", "A", bNode.entity.parentId)
            assertEquals("B depth should be 1", 1, bNode.depth)
        }
    }

    // Test 3: Outdent moves node up one level
    @Test
    fun `outdentNode movesNodeUpOneLevel`() = runTest {
        // B is child of A (depth 1); outdent should make B have parentId = doc-1
        val nodes = listOf(
            fakeNode(id = "A", content = "A", parentId = testDocumentId, sortOrder = "a0"),
            fakeNode(id = "B", content = "B", parentId = "A", sortOrder = "a0"),
        )
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        coEvery { nodeDao.updateNode(any()) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            awaitState()

            containerHost.outdentNode("B")
            val outdentedState = awaitState()

            val bNode = outdentedState.flatNodes.first { it.entity.id == "B" }
            assertEquals("B parentId should be doc-1 (A's parent)", testDocumentId, bNode.entity.parentId)
            assertEquals("B depth should be 0", 0, bNode.depth)
        }
    }

    // Test 4: Reorder stamps parentIdHlc and sortOrderHlc
    @Test
    fun `reorderNodes stamps parentIdHlc and sortOrderHlc`() = runTest {
        val nodes = listOf(
            fakeNode(id = "A", content = "A", sortOrder = "a0"),
            fakeNode(id = "B", content = "B", sortOrder = "a1"),
            fakeNode(id = "C", content = "C", sortOrder = "a2"),
        )
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        val updateSlots = mutableListOf<NodeEntity>()
        coEvery { nodeDao.updateNode(capture(updateSlots)) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            awaitState()

            containerHost.reorderNodes(1, 2)
            awaitState()
        }

        // At least one updateNode call should have been made
        assertTrue("updateNode should have been called", updateSlots.isNotEmpty())

        // Check that HLC values are set on the updated nodes
        for (updated in updateSlots) {
            assertTrue(
                "sortOrderHlc should be non-blank",
                updated.sortOrderHlc.isNotBlank()
            )
        }
    }

    // Test 5: Depth constraint — moving a deep node next to a shallow node clamps its depth
    @Test
    fun `reorderNodes clamps depth to nodeAbove depth plus one`() = runTest {
        // Tree: A(0) → B(1) → C(2); D(0)
        // Drag C (depth 2) to position 0 (above A, which is depth 0)
        // Expected: C becomes depth 1 (nodeAbove=null → maxDepth=0... wait, above A there's nothing)
        // Actually drag C to position after D (index 3 → insert at 3 in list without C)
        // Let's set up: A(0), B(1,child of A), C(2,child of B), D(0)
        // Drag C from index 2 to index 3 (after D, nodeAbove=D depth 0 → maxDepth=1)
        // C.depth=2 > maxDepth=1 → adjust C to depth=1, parentId=D
        val nodes = listOf(
            fakeNode(id = "A", content = "A", parentId = testDocumentId, sortOrder = "a0"),
            fakeNode(id = "B", content = "B", parentId = "A", sortOrder = "a0"),
            fakeNode(id = "C", content = "C", parentId = "B", sortOrder = "a0"),
            fakeNode(id = "D", content = "D", parentId = testDocumentId, sortOrder = "a1"),
        )
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        coEvery { nodeDao.updateNode(any()) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            val loadedState = awaitState()
            // flat list: A(0), B(1), C(2), D(0)
            assertEquals(4, loadedState.flatNodes.size)

            // Drag C (index 2) to index 3 (after D)
            containerHost.reorderNodes(2, 3)
            val reorderedState = awaitState()

            val cNode = reorderedState.flatNodes.first { it.entity.id == "C" }
            // C was depth 2; node above after move is D (depth 0); maxAllowed = 1
            assertEquals("C depth should be clamped to 1", 1, cNode.depth)
            assertEquals("C parentId should be D", "D", cNode.entity.parentId)
        }
    }

    // Test 6: Shallow item dropped between deeper siblings snaps to their depth
    @Test
    fun `reorderNodes snaps depth up when dropped between deeper siblings`() = runTest {
        // List: AppImprovement(depth 0), Groceries(depth 0), bread(depth 1, child Groceries), multisel(depth 1, child Groceries)
        // Drag AppImprovement (index 0) to between bread(2) and multisel(3): toIndex=3 → insertAt=3-1+1=3... but let's test fromIndex=0,toIndex=2
        // After removing block [AppImprovement] from withoutBlock=[Groceries,bread,multisel],
        // insertAt for toIndex(2)>fromIndex(0): (2-1+1)=2 → prevNode=bread(depth 1) → newDepth snaps to 1
        val nodes = listOf(
            fakeNode(id = "AppImprovement", content = "App improvement", parentId = testDocumentId, sortOrder = "a0"),
            fakeNode(id = "Groceries", content = "Groceries", parentId = testDocumentId, sortOrder = "a1"),
            fakeNode(id = "bread", content = "bread", parentId = "Groceries", sortOrder = "a0"),
            fakeNode(id = "multisel", content = "multisel", parentId = "Groceries", sortOrder = "a1"),
        )
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        coEvery { nodeDao.updateNode(any()) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            awaitState()

            // Drag AppImprovement (index 0) to index 2 (between bread and multisel in withoutBlock)
            containerHost.reorderNodes(0, 2)
            val reorderedState = awaitState()

            val appNode = reorderedState.flatNodes.first { it.entity.id == "AppImprovement" }
            val multiselNode = reorderedState.flatNodes.first { it.entity.id == "multisel" }

            // AppImprovement should snap to depth 1 (sibling of bread/multisel)
            assertEquals("AppImprovement should snap to depth 1", 1, appNode.depth)
            // multisel must NOT become a child of AppImprovement
            assertNotEquals("multisel parentId must not be AppImprovement", "AppImprovement", multiselNode.entity.parentId)
            assertEquals("multisel must remain child of Groceries", "Groceries", multiselNode.entity.parentId)
        }
    }

    // Test 7: Optimistic in-memory update reflects the reorder without awaiting Room
    @Test
    fun `reorderNodes optimisticallyUpdatesInMemoryFlatNodes`() = runTest {
        val nodes = listOf(
            fakeNode(id = "A", content = "A", sortOrder = "a0"),
            fakeNode(id = "B", content = "B", sortOrder = "a1"),
            fakeNode(id = "C", content = "C", sortOrder = "a2"),
        )
        every { nodeDao.getNodesByDocument(testDocumentId) } returns flowOf(nodes)
        coEvery { nodeDao.updateNode(any()) } just Runs

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocument(testDocumentId)
            expectState(NodeEditorUiState(documentId = testDocumentId, status = NodeEditorStatus.Loading))
            val loadedState = awaitState()
            assertEquals(3, loadedState.flatNodes.size)

            // Reorder: move C (index 2) to top (index 0)
            containerHost.reorderNodes(2, 0)
            val reorderedState = awaitState()

            // The state should immediately reflect the new order
            assertEquals("C", reorderedState.flatNodes[0].entity.id)
            assertEquals("A", reorderedState.flatNodes[1].entity.id)
            assertEquals("B", reorderedState.flatNodes[2].entity.id)
        }
    }
}

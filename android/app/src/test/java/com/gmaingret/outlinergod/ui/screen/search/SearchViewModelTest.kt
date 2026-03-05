package com.gmaingret.outlinergod.ui.screen.search

import com.gmaingret.outlinergod.db.dao.DocumentDao
import com.gmaingret.outlinergod.db.entity.DocumentEntity
import com.gmaingret.outlinergod.db.entity.NodeEntity
import com.gmaingret.outlinergod.repository.AuthRepository
import com.gmaingret.outlinergod.repository.SearchRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.orbitmvi.orbit.test.test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(testScheduler)

    private lateinit var searchRepository: SearchRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var documentDao: DocumentDao

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        searchRepository = mockk(relaxed = true)
        authRepository = mockk()
        documentDao = mockk(relaxed = true)
        every { authRepository.getUserId() } returns flowOf("test-user-id")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeNode(
        id: String = "node-1",
        documentId: String = "doc-1",
        content: String = "Hello world",
        note: String = ""
    ) = NodeEntity(
        id = id,
        documentId = documentId,
        userId = "test-user-id",
        content = content,
        contentHlc = "1636300202430-00000-device-1",
        note = note,
        noteHlc = "",
        parentId = null,
        parentIdHlc = "",
        sortOrder = "aV",
        sortOrderHlc = "",
        completed = 0,
        completedHlc = "",
        color = 0,
        colorHlc = "",
        collapsed = 0,
        collapsedHlc = "",
        deletedAt = null,
        deletedHlc = "",
        deviceId = "device-1",
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private fun fakeDocument(
        id: String = "doc-1",
        title: String = "Test Doc"
    ) = DocumentEntity(
        id = id,
        userId = "test-user-id",
        title = title,
        titleHlc = "1636300202430-00000-device-1",
        type = "document",
        parentId = null,
        parentIdHlc = "",
        sortOrder = "aV",
        sortOrderHlc = "",
        collapsed = 0,
        collapsedHlc = "",
        deletedAt = null,
        deletedHlc = "",
        deviceId = "device-1",
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private fun createViewModel() = SearchViewModel(
        searchRepository = searchRepository,
        authRepository = authRepository,
        documentDao = documentDao
    )

    @Test
    fun `initial state is Idle`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        assertEquals(SearchUiState.Idle, viewModel.container.stateFlow.value)
    }

    @Test
    fun `onQueryChanged with non-blank query produces Success state after debounce`() = runTest(testDispatcher) {
        val node = fakeNode()
        val doc = fakeDocument()
        coEvery { searchRepository.searchNodes("hello", "test-user-id") } returns listOf(node)
        coEvery { documentDao.getDocumentByIdSync("doc-1") } returns doc

        val viewModel = createViewModel()
        viewModel.test(this) {
            testScheduler.advanceUntilIdle()

            containerHost.onQueryChanged("hello")
            testScheduler.advanceTimeBy(500)
            testScheduler.advanceUntilIdle()

            expectState(SearchUiState.Loading)
            val expectedItem = SearchResultItem(
                nodeId = "node-1",
                documentId = "doc-1",
                content = "Hello world",
                note = "",
                documentTitle = "Test Doc"
            )
            expectState(SearchUiState.Success(results = listOf(expectedItem), query = "hello"))
        }
    }

    @Test
    fun `empty query after search returns to Idle`() = runTest(testDispatcher) {
        coEvery { searchRepository.searchNodes("hello", "test-user-id") } returns emptyList()

        val viewModel = createViewModel()
        viewModel.test(this) {
            testScheduler.advanceUntilIdle()

            // First set a real query to leave Idle state
            containerHost.onQueryChanged("hello")
            testScheduler.advanceTimeBy(500)
            testScheduler.advanceUntilIdle()

            expectState(SearchUiState.Loading)
            expectState(SearchUiState.NoResults(query = "hello"))

            // Now clear the query — should return to Idle
            containerHost.onQueryChanged("")
            testScheduler.advanceTimeBy(500)
            testScheduler.advanceUntilIdle()

            expectState(SearchUiState.Idle)
        }
    }

    @Test
    fun `no results shows NoResults state`() = runTest(testDispatcher) {
        coEvery { searchRepository.searchNodes("xyz", "test-user-id") } returns emptyList()

        val viewModel = createViewModel()
        viewModel.test(this) {
            testScheduler.advanceUntilIdle()

            containerHost.onQueryChanged("xyz")
            testScheduler.advanceTimeBy(500)
            testScheduler.advanceUntilIdle()

            expectState(SearchUiState.Loading)
            expectState(SearchUiState.NoResults(query = "xyz"))
        }
    }

    @Test
    fun `search error shows Error state`() = runTest(testDispatcher) {
        coEvery { searchRepository.searchNodes("crash", "test-user-id") } throws Exception("DB error")

        val viewModel = createViewModel()
        viewModel.test(this) {
            testScheduler.advanceUntilIdle()

            containerHost.onQueryChanged("crash")
            testScheduler.advanceTimeBy(500)
            testScheduler.advanceUntilIdle()

            expectState(SearchUiState.Loading)
            expectState(SearchUiState.Error(message = "DB error"))
        }
    }

    @Test
    fun `onResultTapped posts NavigateToNodeEditor side effect`() = runTest(testDispatcher) {
        val item = SearchResultItem(
            nodeId = "n1",
            documentId = "doc-1",
            content = "Hello",
            note = "",
            documentTitle = "Doc"
        )

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.onResultTapped(item)
            testScheduler.advanceUntilIdle()
            expectSideEffect(SearchSideEffect.NavigateToNodeEditor("doc-1", "n1"))
        }
    }

    @Test
    fun `onResultTapped posts NavigateToNodeEditor with correct nodeId`() = runTest(testDispatcher) {
        val item = SearchResultItem(
            nodeId = "specific-node",
            documentId = "doc-1",
            content = "Hello",
            note = "",
            documentTitle = "Doc"
        )
        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.onResultTapped(item)
            testScheduler.advanceUntilIdle()
            expectSideEffect(SearchSideEffect.NavigateToNodeEditor("doc-1", "specific-node"))
        }
    }
}

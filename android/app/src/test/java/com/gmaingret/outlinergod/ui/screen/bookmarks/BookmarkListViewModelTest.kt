package com.gmaingret.outlinergod.ui.screen.bookmarks

import com.gmaingret.outlinergod.db.dao.BookmarkDao
import com.gmaingret.outlinergod.db.entity.BookmarkEntity
import com.gmaingret.outlinergod.repository.AuthRepository
import com.gmaingret.outlinergod.sync.HlcClock
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
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
import org.junit.runner.RunWith
import org.orbitmvi.orbit.test.test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookmarkListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var bookmarkDao: BookmarkDao
    private lateinit var authRepository: AuthRepository
    private lateinit var hlcClock: HlcClock

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        bookmarkDao = mockk(relaxed = true)
        authRepository = mockk()
        hlcClock = mockk()
        every { authRepository.getUserId() } returns flowOf("test-user-id")
        every { authRepository.getDeviceId() } returns flowOf("test-device-id")
        every { hlcClock.generate(any()) } returns "1636300202430-00000-test-device-id"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeBookmark(
        id: String = "bm-1",
        title: String = "Test Bookmark",
        targetType: String = "document",
        targetDocumentId: String? = "doc-1",
        targetNodeId: String? = null
    ) = BookmarkEntity(
        id = id,
        userId = "test-user-id",
        title = title,
        titleHlc = "1636300202430-00000-test-device-id",
        targetType = targetType,
        targetTypeHlc = "1636300202430-00000-test-device-id",
        targetDocumentId = targetDocumentId,
        targetDocumentIdHlc = "1636300202430-00000-test-device-id",
        targetNodeId = targetNodeId,
        targetNodeIdHlc = "",
        query = null,
        queryHlc = "",
        sortOrder = "aV",
        sortOrderHlc = "1636300202430-00000-test-device-id",
        deletedAt = null,
        deletedHlc = "",
        deviceId = "test-device-id",
        createdAt = 1000L,
        updatedAt = 1000L
    )

    private fun createViewModel(): BookmarkListViewModel {
        return BookmarkListViewModel(
            bookmarkDao = bookmarkDao,
            authRepository = authRepository,
            hlcClock = hlcClock
        )
    }

    @Test
    fun `loadBookmarks emits Success with bookmarks`() = runTest {
        val bookmark = fakeBookmark()
        every { bookmarkDao.observeAllActive("test-user-id") } returns flowOf(listOf(bookmark))
        val viewModel = createViewModel()
        viewModel.test(this) {
            // Explicitly trigger loadBookmarks (init's call went to the real container)
            containerHost.loadBookmarks()
            expectState(BookmarkListUiState.Success(bookmarks = listOf(bookmark)))
        }
    }

    @Test
    fun `loadBookmarks emits Empty when no bookmarks`() = runTest {
        every { bookmarkDao.observeAllActive("test-user-id") } returns flowOf(emptyList())
        val viewModel = createViewModel()
        viewModel.test(this) {
            // Explicitly trigger loadBookmarks (init's call went to the real container)
            containerHost.loadBookmarks()
            expectState(BookmarkListUiState.Empty)
        }
    }

    @Test
    fun `onBookmarkTapped with document type posts NavigateToDocument`() = runTest {
        val bookmark = fakeBookmark(targetType = "document", targetDocumentId = "doc-1")
        every { bookmarkDao.observeAllActive("test-user-id") } returns flowOf(listOf(bookmark))
        val viewModel = createViewModel()
        viewModel.test(this) {
            // First consume the state emitted by loadBookmarks via init
            containerHost.loadBookmarks()
            expectState(BookmarkListUiState.Success(bookmarks = listOf(bookmark)))
            // Now test navigation: onBookmarkTapped posts a side effect, no state change
            containerHost.onBookmarkTapped(bookmark)
            testDispatcher.scheduler.advanceUntilIdle()
            expectSideEffect(BookmarkListSideEffect.NavigateToDocument("doc-1"))
        }
    }

    @Test
    fun `onBookmarkTapped with node type posts NavigateToNodeEditor`() = runTest {
        val bookmark = fakeBookmark(targetType = "node", targetDocumentId = "doc-2", targetNodeId = "node-1")
        every { bookmarkDao.observeAllActive("test-user-id") } returns flowOf(listOf(bookmark))
        val viewModel = createViewModel()
        viewModel.test(this) {
            // First consume the state emitted by loadBookmarks via init
            containerHost.loadBookmarks()
            expectState(BookmarkListUiState.Success(bookmarks = listOf(bookmark)))
            // Now test navigation: onBookmarkTapped posts a side effect, no state change
            containerHost.onBookmarkTapped(bookmark)
            testDispatcher.scheduler.advanceUntilIdle()
            expectSideEffect(BookmarkListSideEffect.NavigateToNodeEditor("doc-2", "node-1"))
        }
    }

    @Test
    fun `softDeleteBookmark calls dao with correct id and hlc`() = runTest {
        every { bookmarkDao.observeAllActive("test-user-id") } returns flowOf(emptyList())
        coEvery { bookmarkDao.softDeleteBookmark(any(), any(), any(), any()) } just Runs
        val viewModel = createViewModel()
        // Call softDeleteBookmark outside test{} to avoid state expectations
        // The init loadBookmarks emits Empty; softDelete only calls the DAO, no state change
        viewModel.test(this) {
            containerHost.softDeleteBookmark("bm-1")
            testDispatcher.scheduler.advanceUntilIdle()
        }
        coVerify { bookmarkDao.softDeleteBookmark("bm-1", any(), "1636300202430-00000-test-device-id", any()) }
    }
}

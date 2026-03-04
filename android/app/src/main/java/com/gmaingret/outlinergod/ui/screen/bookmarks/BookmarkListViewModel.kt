package com.gmaingret.outlinergod.ui.screen.bookmarks

import androidx.lifecycle.ViewModel
import com.gmaingret.outlinergod.db.dao.BookmarkDao
import com.gmaingret.outlinergod.db.entity.BookmarkEntity
import com.gmaingret.outlinergod.repository.AuthRepository
import com.gmaingret.outlinergod.sync.HlcClock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

@HiltViewModel
class BookmarkListViewModel @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val authRepository: AuthRepository,
    private val hlcClock: HlcClock
) : ViewModel(), ContainerHost<BookmarkListUiState, BookmarkListSideEffect> {

    override val container = container<BookmarkListUiState, BookmarkListSideEffect>(BookmarkListUiState.Loading)

    init {
        loadBookmarks()
    }

    fun loadBookmarks() = intent {
        val userId = authRepository.getUserId().filterNotNull().first()
        bookmarkDao.observeAllActive(userId).collect { bookmarks ->
            reduce {
                if (bookmarks.isEmpty()) {
                    BookmarkListUiState.Empty
                } else {
                    BookmarkListUiState.Success(bookmarks = bookmarks)
                }
            }
        }
    }

    fun onBookmarkTapped(bookmark: BookmarkEntity) = intent {
        when (bookmark.targetType) {
            "document" -> {
                bookmark.targetDocumentId?.let { docId ->
                    postSideEffect(BookmarkListSideEffect.NavigateToDocument(docId))
                }
            }
            "node" -> {
                bookmark.targetDocumentId?.let { docId ->
                    postSideEffect(BookmarkListSideEffect.NavigateToNodeEditor(docId))
                }
            }
            else -> {
                // no-op for unknown target types
            }
        }
    }

    fun softDeleteBookmark(id: String) = intent {
        try {
            val deviceId = authRepository.getDeviceId().first()
            val hlc = hlcClock.generate(deviceId)
            val now = System.currentTimeMillis()
            bookmarkDao.softDeleteBookmark(id, now, hlc, now)
        } catch (e: Exception) {
            postSideEffect(BookmarkListSideEffect.ShowError(e.message ?: "Failed to delete bookmark"))
        }
    }
}

sealed class BookmarkListUiState {
    data object Loading : BookmarkListUiState()
    data class Success(val bookmarks: List<BookmarkEntity> = emptyList()) : BookmarkListUiState()
    data object Empty : BookmarkListUiState()
}

sealed class BookmarkListSideEffect {
    data class NavigateToDocument(val documentId: String) : BookmarkListSideEffect()
    data class NavigateToNodeEditor(val documentId: String) : BookmarkListSideEffect()
    data class ShowError(val message: String) : BookmarkListSideEffect()
}

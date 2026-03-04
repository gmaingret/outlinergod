package com.gmaingret.outlinergod.ui.screen.search

import androidx.lifecycle.ViewModel
import com.gmaingret.outlinergod.db.dao.DocumentDao
import com.gmaingret.outlinergod.repository.AuthRepository
import com.gmaingret.outlinergod.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.debounce
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val authRepository: AuthRepository,
    private val documentDao: DocumentDao
) : ViewModel(), ContainerHost<SearchUiState, SearchSideEffect> {

    override val container = container<SearchUiState, SearchSideEffect>(SearchUiState.Idle)

    private val queryFlow = MutableStateFlow("")

    init {
        intent {
            val userId = authRepository.getUserId().filterNotNull().first()
            queryFlow
                .debounce(400L)
                .distinctUntilChanged()
                .flatMapLatest { q ->
                    if (q.isBlank()) {
                        flowOf<SearchUiState>(SearchUiState.Idle)
                    } else {
                        flow<SearchUiState> {
                            emit(SearchUiState.Loading)
                            try {
                                val nodes = searchRepository.searchNodes(q, userId)
                                if (nodes.isEmpty()) {
                                    emit(SearchUiState.NoResults(q))
                                } else {
                                    val items = nodes.map { node ->
                                        val docTitle = documentDao.getDocumentByIdSync(node.documentId)?.title ?: "Unknown"
                                        SearchResultItem(
                                            nodeId = node.id,
                                            documentId = node.documentId,
                                            content = node.content,
                                            note = node.note,
                                            documentTitle = docTitle
                                        )
                                    }
                                    emit(SearchUiState.Success(items, q))
                                }
                            } catch (e: Exception) {
                                emit(SearchUiState.Error(e.message ?: "Search failed"))
                            }
                        }
                    }
                }
                .collect { newState -> reduce { newState } }
        }
    }

    fun onQueryChanged(query: String) {
        queryFlow.value = query
    }

    fun onResultTapped(item: SearchResultItem) = intent {
        postSideEffect(SearchSideEffect.NavigateToNodeEditor(item.documentId))
    }
}

data class SearchResultItem(
    val nodeId: String,
    val documentId: String,
    val content: String,
    val note: String,
    val documentTitle: String
)

sealed class SearchUiState {
    data object Idle : SearchUiState()
    data object Loading : SearchUiState()
    data class Success(val results: List<SearchResultItem>, val query: String) : SearchUiState()
    data class NoResults(val query: String) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

sealed class SearchSideEffect {
    data class NavigateToNodeEditor(val documentId: String) : SearchSideEffect()
}

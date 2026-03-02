package com.gmaingret.outlinergod.ui.screen.documentlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmaingret.outlinergod.db.dao.DocumentDao
import com.gmaingret.outlinergod.db.entity.DocumentEntity
import com.gmaingret.outlinergod.repository.AuthRepository
import com.gmaingret.outlinergod.sync.HlcClock
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class DocumentListViewModel @Inject constructor(
    private val documentDao: DocumentDao,
    private val authRepository: AuthRepository,
    private val hlcClock: HlcClock,
    @Named("baseUrl") private val baseUrl: String,
    private val httpClient: HttpClient
) : ViewModel(), ContainerHost<DocumentListUiState, DocumentListSideEffect> {

    override val container = container<DocumentListUiState, DocumentListSideEffect>(DocumentListUiState.Loading)

    init {
        loadDocuments()
    }

    fun loadDocuments() = intent {
        val userId = authRepository.getAccessToken().filterNotNull().first()
        documentDao.getAllDocuments(userId).collect { documents ->
            reduce {
                DocumentListUiState.Success(items = documents)
            }
        }
    }

    fun createDocument(title: String, type: String, parentId: String?, sortOrder: String) = intent {
        try {
            val deviceId = authRepository.getDeviceId().first()
            val now = System.currentTimeMillis()
            val hlc = hlcClock.generate(deviceId)
            val doc = DocumentEntity(
                id = java.util.UUID.randomUUID().toString(),
                userId = "",
                title = title,
                titleHlc = hlc,
                type = type,
                parentId = parentId,
                parentIdHlc = hlc,
                sortOrder = sortOrder,
                sortOrderHlc = hlc,
                collapsed = 0,
                collapsedHlc = hlc,
                deletedAt = null,
                deletedHlc = "",
                deviceId = deviceId,
                createdAt = now,
                updatedAt = now,
                syncStatus = 0
            )

            // POST to backend
            try {
                httpClient.post("$baseUrl/api/documents") {
                    contentType(ContentType.Application.Json)
                    setBody(mapOf(
                        "id" to doc.id,
                        "title" to doc.title,
                        "type" to doc.type,
                        "parentId" to doc.parentId,
                        "sortOrder" to doc.sortOrder
                    ))
                }
            } catch (_: Exception) {
                // Network failure is non-fatal; sync will reconcile later
            }

            documentDao.insertDocument(doc)
        } catch (e: Exception) {
            postSideEffect(DocumentListSideEffect.ShowError(e.message ?: "Failed to create document"))
        }
    }

    fun renameDocument(id: String, title: String) = intent {
        try {
            val entity = documentDao.getDocumentByIdSync(id) ?: return@intent
            val deviceId = authRepository.getDeviceId().first()
            val now = System.currentTimeMillis()
            val hlc = hlcClock.generate(deviceId)
            val updated = entity.copy(
                title = title,
                titleHlc = hlc,
                updatedAt = now
            )
            documentDao.updateDocument(updated)

            // PATCH in background — non-fatal on failure
            viewModelScope.launch {
                try {
                    httpClient.patch("$baseUrl/api/documents/$id") {
                        contentType(ContentType.Application.Json)
                        setBody(mapOf("title" to title))
                    }
                } catch (_: Exception) {
                    // sync reconciles later
                }
            }
        } catch (e: Exception) {
            postSideEffect(DocumentListSideEffect.ShowError(e.message ?: "Failed to rename document"))
        }
    }

    fun deleteDocument(id: String) = intent {
        try {
            val deviceId = authRepository.getDeviceId().first()
            val now = System.currentTimeMillis()
            val hlc = hlcClock.generate(deviceId)
            documentDao.softDeleteDocument(id, now, hlc, now)

            // DELETE in background — non-fatal on failure
            viewModelScope.launch {
                try {
                    httpClient.delete("$baseUrl/api/documents/$id")
                } catch (_: Exception) {
                    // sync reconciles later
                }
            }
        } catch (e: Exception) {
            postSideEffect(DocumentListSideEffect.ShowError(e.message ?: "Failed to delete document"))
        }
    }

    fun toggleFolderCollapse(id: String) = intent {
        try {
            val entity = documentDao.getDocumentByIdSync(id) ?: return@intent
            val updated = entity.copy(
                collapsed = if (entity.collapsed == 0) 1 else 0
            )
            documentDao.updateDocument(updated)
        } catch (e: Exception) {
            postSideEffect(DocumentListSideEffect.ShowError(e.message ?: "Failed to toggle folder"))
        }
    }
}

sealed class DocumentListUiState {
    data object Loading : DocumentListUiState()
    data class Success(
        val items: List<DocumentEntity> = emptyList(),
        val syncStatus: SyncStatus = SyncStatus.Idle
    ) : DocumentListUiState()
    data class Error(val message: String) : DocumentListUiState()
}

enum class SyncStatus { Idle, Syncing, Error }

sealed class DocumentListSideEffect {
    data class ShowError(val message: String) : DocumentListSideEffect()
}

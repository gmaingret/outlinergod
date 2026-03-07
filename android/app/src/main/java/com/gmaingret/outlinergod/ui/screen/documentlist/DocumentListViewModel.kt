package com.gmaingret.outlinergod.ui.screen.documentlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmaingret.outlinergod.db.dao.BookmarkDao
import com.gmaingret.outlinergod.db.dao.DocumentDao
import com.gmaingret.outlinergod.db.dao.NodeDao
import com.gmaingret.outlinergod.db.dao.SettingsDao
import com.gmaingret.outlinergod.db.entity.DocumentEntity
import com.gmaingret.outlinergod.db.entity.NodeEntity
import com.gmaingret.outlinergod.repository.AuthRepository
import com.gmaingret.outlinergod.sync.HlcClock
import com.gmaingret.outlinergod.sync.SyncOrchestrator
import com.gmaingret.outlinergod.ui.common.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

@HiltViewModel
class DocumentListViewModel @Inject constructor(
    private val documentDao: DocumentDao,
    private val nodeDao: NodeDao,
    private val bookmarkDao: BookmarkDao,
    private val settingsDao: SettingsDao,
    private val authRepository: AuthRepository,
    private val syncOrchestrator: SyncOrchestrator,
    private val hlcClock: HlcClock,
) : ViewModel(), ContainerHost<DocumentListUiState, DocumentListSideEffect> {

    override val container = container<DocumentListUiState, DocumentListSideEffect>(DocumentListUiState.Loading)

    init {
        loadDocuments()
    }

    fun loadDocuments() = intent {
        val userId = authRepository.getUserId().filterNotNull().first()
        documentDao.getAllDocuments(userId).collect { documents ->
            reduce {
                when (val current = state) {
                    is DocumentListUiState.Success -> current.copy(items = documents)
                    else -> DocumentListUiState.Success(items = documents)
                }
            }
        }
    }

    fun triggerSync() = intent {
        reduce {
            when (val s = state) {
                is DocumentListUiState.Success -> s.copy(syncStatus = SyncStatus.Syncing)
                else -> DocumentListUiState.Success(syncStatus = SyncStatus.Syncing)
            }
        }
        val result = syncOrchestrator.fullSync()
        reduce {
            when (val s = state) {
                is DocumentListUiState.Success -> s.copy(syncStatus = if (result.isSuccess) SyncStatus.Idle else SyncStatus.Error)
                else -> DocumentListUiState.Success(syncStatus = if (result.isSuccess) SyncStatus.Idle else SyncStatus.Error)
            }
        }
    }

    private var periodicSyncJob: Job? = null

    fun onScreenResumed() {
        periodicSyncJob?.cancel()
        periodicSyncJob = viewModelScope.launch {
            while (true) {
                triggerSync().join()
                delay(30_000)
            }
        }
    }

    fun onScreenPaused() {
        periodicSyncJob?.cancel()
        periodicSyncJob = null
    }

    fun createDocument(title: String, type: String, parentId: String?, sortOrder: String) = intent {
        try {
            val userId = authRepository.getUserId().filterNotNull().first()
            val deviceId = authRepository.getDeviceId().first()
            val now = System.currentTimeMillis()
            val hlc = hlcClock.generate(deviceId)
            val doc = DocumentEntity(
                id = java.util.UUID.randomUUID().toString(),
                userId = userId,
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
            )

            documentDao.insertDocument(doc)

            val rootNode = NodeEntity(
                id = java.util.UUID.randomUUID().toString(),
                documentId = doc.id,
                userId = userId,
                content = "",
                contentHlc = hlc,
                note = "",
                noteHlc = "",
                parentId = doc.id,
                parentIdHlc = hlc,
                sortOrder = "a0",
                sortOrderHlc = hlc,
                deviceId = deviceId,
                createdAt = now,
                updatedAt = now,
            )
            nodeDao.insertNode(rootNode)
            triggerSync()
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
            triggerSync()
        } catch (e: Exception) {
            postSideEffect(DocumentListSideEffect.ShowError(e.message ?: "Failed to rename document"))
        }
    }

    fun deleteDocument(id: String) = intent {
        try {
            val deviceId = authRepository.getDeviceId().first()
            val now = System.currentTimeMillis()
            val hlc = hlcClock.generate(deviceId)
            documentDao.softDeleteDocument(id, now, hlc, now, deviceId)
            triggerSync()
        } catch (e: Exception) {
            postSideEffect(DocumentListSideEffect.ShowError(e.message ?: "Failed to delete document"))
        }
    }

    fun addBookmark(documentId: String, title: String) = intent {
        try {
            val userId = authRepository.getUserId().filterNotNull().first()
            val deviceId = authRepository.getDeviceId().first()
            val now = System.currentTimeMillis()
            val hlc = hlcClock.generate(deviceId)
            bookmarkDao.insertBookmark(
                com.gmaingret.outlinergod.db.entity.BookmarkEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    userId = userId,
                    title = title,
                    titleHlc = hlc,
                    targetType = "document",
                    targetTypeHlc = hlc,
                    targetDocumentId = documentId,
                    targetDocumentIdHlc = hlc,
                    sortOrder = "aV",
                    sortOrderHlc = hlc,
                    deviceId = deviceId,
                    createdAt = now,
                    updatedAt = now
                )
            )
            postSideEffect(DocumentListSideEffect.ShowBookmarkAdded)
        } catch (e: Exception) {
            postSideEffect(DocumentListSideEffect.ShowError(e.message ?: "Failed to add bookmark"))
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

sealed class DocumentListSideEffect {
    data class ShowError(val message: String) : DocumentListSideEffect()
    data object ShowBookmarkAdded : DocumentListSideEffect()
}

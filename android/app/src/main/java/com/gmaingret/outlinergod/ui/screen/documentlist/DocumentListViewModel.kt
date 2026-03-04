package com.gmaingret.outlinergod.ui.screen.documentlist

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.gmaingret.outlinergod.sync.SyncConstants
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmaingret.outlinergod.db.dao.BookmarkDao
import com.gmaingret.outlinergod.db.dao.DocumentDao
import com.gmaingret.outlinergod.db.dao.NodeDao
import com.gmaingret.outlinergod.db.dao.SettingsDao
import com.gmaingret.outlinergod.ui.common.SyncStatus
import com.gmaingret.outlinergod.db.entity.DocumentEntity
import com.gmaingret.outlinergod.db.entity.NodeEntity
import com.gmaingret.outlinergod.network.model.SyncPushPayload
import com.gmaingret.outlinergod.sync.toNodeEntity
import com.gmaingret.outlinergod.sync.toDocumentEntity
import com.gmaingret.outlinergod.sync.toBookmarkEntity
import com.gmaingret.outlinergod.sync.toSettingsEntity
import com.gmaingret.outlinergod.sync.toNodeSyncRecord
import com.gmaingret.outlinergod.sync.toDocumentSyncRecord
import com.gmaingret.outlinergod.sync.toBookmarkSyncRecord
import com.gmaingret.outlinergod.sync.toSettingsSyncRecord
import com.gmaingret.outlinergod.repository.AuthRepository
import com.gmaingret.outlinergod.repository.SyncRepository
import com.gmaingret.outlinergod.sync.HlcClock
import com.gmaingret.outlinergod.util.SyncLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    private val syncRepository: SyncRepository,
    private val hlcClock: HlcClock,
    private val dataStore: DataStore<Preferences>
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
        try {
            // Set syncing status
            reduce {
                when (val current = state) {
                    is DocumentListUiState.Success -> current.copy(syncStatus = SyncStatus.Syncing)
                    else -> DocumentListUiState.Success(syncStatus = SyncStatus.Syncing)
                }
            }

            val deviceId = authRepository.getDeviceId().first()
            val lastSyncHlc = dataStore.data.map { prefs ->
                prefs[SyncConstants.LAST_SYNC_HLC_KEY] ?: "0"
            }.first()

            // Pull changes from server
            val pullResult = syncRepository.pull(since = lastSyncHlc, deviceId = deviceId)
            pullResult.getOrThrow().let { response ->
                // Upsert pulled data into local DB
                if (response.nodes.isNotEmpty()) {
                    nodeDao.upsertNodes(response.nodes.map { it.toNodeEntity() })
                }
                if (response.documents.isNotEmpty()) {
                    documentDao.upsertDocuments(response.documents.map { it.toDocumentEntity() })
                }
                if (response.bookmarks.isNotEmpty()) {
                    bookmarkDao.upsertBookmarks(response.bookmarks.map { it.toBookmarkEntity() })
                }
                response.settings?.let { settings ->
                    settingsDao.upsertSettings(settings.toSettingsEntity())
                }
            }

            // Build and push local changes
            val userId = authRepository.getUserId().filterNotNull().first()
            val pendingNodes = nodeDao.getPendingChanges(userId, lastSyncHlc, deviceId)
            val pendingDocs = documentDao.getPendingChanges(userId, lastSyncHlc, deviceId)
            val pendingBookmarks = bookmarkDao.getPendingChanges(userId, lastSyncHlc, deviceId)
            val pendingSettings = settingsDao.getPendingSettings(userId, lastSyncHlc, deviceId)

            val pushPayload = SyncPushPayload(
                deviceId = deviceId,
                nodes = pendingNodes.map { it.toNodeSyncRecord() }.ifEmpty { null },
                documents = pendingDocs.map { it.toDocumentSyncRecord() }.ifEmpty { null },
                bookmarks = pendingBookmarks.map { it.toBookmarkSyncRecord() }.ifEmpty { null },
                settings = pendingSettings?.toSettingsSyncRecord()
            )

            val pushResult = syncRepository.push(pushPayload)
            pushResult.getOrThrow().let { response ->
                // Apply conflict resolutions from server
                if (response.conflicts.nodes.isNotEmpty()) {
                    nodeDao.upsertNodes(response.conflicts.nodes.map { it.toNodeEntity() })
                }
                if (response.conflicts.documents.isNotEmpty()) {
                    documentDao.upsertDocuments(response.conflicts.documents.map { it.toDocumentEntity() })
                }
                if (response.conflicts.bookmarks.isNotEmpty()) {
                    bookmarkDao.upsertBookmarks(response.conflicts.bookmarks.map { it.toBookmarkEntity() })
                }
                response.conflicts.settings?.let { settings ->
                    settingsDao.upsertSettings(settings.toSettingsEntity())
                }

                // Update last sync HLC
                dataStore.edit { prefs ->
                    prefs[SyncConstants.LAST_SYNC_HLC_KEY] = response.serverHlc
                }
            }

            // Set idle status
            reduce {
                when (val current = state) {
                    is DocumentListUiState.Success -> current.copy(syncStatus = SyncStatus.Idle)
                    else -> DocumentListUiState.Success(syncStatus = SyncStatus.Idle)
                }
            }
        } catch (_: Exception) {
            reduce {
                when (val current = state) {
                    is DocumentListUiState.Success -> current.copy(syncStatus = SyncStatus.Error)
                    else -> DocumentListUiState.Success(syncStatus = SyncStatus.Error)
                }
            }
        }
    }

    fun onScreenResumed() {
        viewModelScope.launch {
            triggerSync()
        }
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
            documentDao.softDeleteDocument(id, now, hlc, now)
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

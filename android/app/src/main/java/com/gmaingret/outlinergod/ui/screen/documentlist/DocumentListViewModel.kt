package com.gmaingret.outlinergod.ui.screen.documentlist

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gmaingret.outlinergod.db.dao.BookmarkDao
import com.gmaingret.outlinergod.db.dao.DocumentDao
import com.gmaingret.outlinergod.db.dao.NodeDao
import com.gmaingret.outlinergod.db.dao.SettingsDao
import com.gmaingret.outlinergod.ui.common.SyncStatus
import com.gmaingret.outlinergod.db.entity.BookmarkEntity
import com.gmaingret.outlinergod.db.entity.DocumentEntity
import com.gmaingret.outlinergod.db.entity.NodeEntity
import com.gmaingret.outlinergod.db.entity.SettingsEntity
import com.gmaingret.outlinergod.network.model.BookmarkSyncRecord
import com.gmaingret.outlinergod.network.model.DocumentSyncRecord
import com.gmaingret.outlinergod.network.model.NodeSyncRecord
import com.gmaingret.outlinergod.network.model.SettingsSyncRecord
import com.gmaingret.outlinergod.network.model.SyncPushPayload
import com.gmaingret.outlinergod.repository.AuthRepository
import com.gmaingret.outlinergod.repository.SyncRepository
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class DocumentListViewModel @Inject constructor(
    private val documentDao: DocumentDao,
    private val nodeDao: NodeDao,
    private val bookmarkDao: BookmarkDao,
    private val settingsDao: SettingsDao,
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val hlcClock: HlcClock,
    @Named("baseUrl") private val baseUrl: String,
    private val httpClient: HttpClient,
    private val dataStore: DataStore<Preferences>
) : ViewModel(), ContainerHost<DocumentListUiState, DocumentListSideEffect> {

    override val container = container<DocumentListUiState, DocumentListSideEffect>(DocumentListUiState.Loading)

    init {
        loadDocuments()
    }

    fun loadDocuments() = intent {
        val userId = authRepository.getAccessToken().filterNotNull().first()
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
                prefs[LAST_SYNC_HLC_KEY] ?: "0"
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
            val userId = authRepository.getAccessToken().filterNotNull().first()
            val pendingNodes = nodeDao.getPendingChanges(lastSyncHlc, deviceId)
            val pendingDocs = documentDao.getPendingChanges(userId, lastSyncHlc, deviceId)
            val pendingBookmarks = bookmarkDao.getPendingChanges(userId, lastSyncHlc, deviceId)

            val pushPayload = SyncPushPayload(
                deviceId = deviceId,
                nodes = pendingNodes.map { it.toNodeSyncRecord() }.ifEmpty { null },
                documents = pendingDocs.map { it.toDocumentSyncRecord() }.ifEmpty { null },
                bookmarks = pendingBookmarks.map { it.toBookmarkSyncRecord() }.ifEmpty { null }
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
                    prefs[LAST_SYNC_HLC_KEY] = response.serverHlc
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

    companion object {
        internal val LAST_SYNC_HLC_KEY = stringPreferencesKey("last_sync_hlc")
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
}

// Extension functions for converting between sync records and entities
internal fun NodeSyncRecord.toNodeEntity() = NodeEntity(
    id = id,
    documentId = documentId,
    userId = userId,
    content = content,
    contentHlc = contentHlc,
    note = note,
    noteHlc = noteHlc,
    parentId = parentId,
    parentIdHlc = parentIdHlc,
    sortOrder = sortOrder,
    sortOrderHlc = sortOrderHlc,
    completed = completed,
    completedHlc = completedHlc,
    color = color,
    colorHlc = colorHlc,
    collapsed = collapsed,
    collapsedHlc = collapsedHlc,
    deletedAt = deletedAt,
    deletedHlc = deletedHlc,
    deviceId = deviceId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun DocumentSyncRecord.toDocumentEntity() = DocumentEntity(
    id = id,
    userId = userId,
    title = title,
    titleHlc = titleHlc,
    type = type,
    parentId = parentId,
    parentIdHlc = parentIdHlc,
    sortOrder = sortOrder,
    sortOrderHlc = sortOrderHlc,
    collapsed = collapsed,
    collapsedHlc = collapsedHlc,
    deletedAt = deletedAt,
    deletedHlc = deletedHlc,
    deviceId = deviceId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun BookmarkSyncRecord.toBookmarkEntity() = BookmarkEntity(
    id = id,
    userId = userId,
    title = title,
    titleHlc = titleHlc,
    targetType = targetType,
    targetTypeHlc = targetTypeHlc,
    targetDocumentId = targetDocumentId,
    targetDocumentIdHlc = targetDocumentIdHlc,
    targetNodeId = targetNodeId,
    targetNodeIdHlc = targetNodeIdHlc,
    query = query,
    queryHlc = queryHlc,
    sortOrder = sortOrder,
    sortOrderHlc = sortOrderHlc,
    deletedAt = deletedAt,
    deletedHlc = deletedHlc,
    deviceId = deviceId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun SettingsSyncRecord.toSettingsEntity() = SettingsEntity(
    userId = userId,
    theme = theme,
    themeHlc = themeHlc,
    density = density,
    densityHlc = densityHlc,
    showGuideLines = showGuideLines,
    showGuideLinesHlc = showGuideLinesHlc,
    showBacklinkBadge = showBacklinkBadge,
    showBacklinkBadgeHlc = showBacklinkBadgeHlc,
    deviceId = deviceId,
    updatedAt = updatedAt
)

internal fun NodeEntity.toNodeSyncRecord() = NodeSyncRecord(
    id = id,
    documentId = documentId,
    userId = userId,
    content = content,
    contentHlc = contentHlc,
    note = note,
    noteHlc = noteHlc,
    parentId = parentId,
    parentIdHlc = parentIdHlc,
    sortOrder = sortOrder,
    sortOrderHlc = sortOrderHlc,
    completed = completed,
    completedHlc = completedHlc,
    color = color,
    colorHlc = colorHlc,
    collapsed = collapsed,
    collapsedHlc = collapsedHlc,
    deletedAt = deletedAt,
    deletedHlc = deletedHlc,
    deviceId = deviceId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun DocumentEntity.toDocumentSyncRecord() = DocumentSyncRecord(
    id = id,
    userId = userId,
    title = title,
    titleHlc = titleHlc,
    type = type,
    parentId = parentId,
    parentIdHlc = parentIdHlc,
    sortOrder = sortOrder,
    sortOrderHlc = sortOrderHlc,
    collapsed = collapsed,
    collapsedHlc = collapsedHlc,
    deletedAt = deletedAt,
    deletedHlc = deletedHlc,
    deviceId = deviceId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun BookmarkEntity.toBookmarkSyncRecord() = BookmarkSyncRecord(
    id = id,
    userId = userId,
    title = title,
    titleHlc = titleHlc,
    targetType = targetType,
    targetTypeHlc = targetTypeHlc,
    targetDocumentId = targetDocumentId,
    targetDocumentIdHlc = targetDocumentIdHlc,
    targetNodeId = targetNodeId,
    targetNodeIdHlc = targetNodeIdHlc,
    query = query,
    queryHlc = queryHlc,
    sortOrder = sortOrder,
    sortOrderHlc = sortOrderHlc,
    deletedAt = deletedAt,
    deletedHlc = deletedHlc,
    deviceId = deviceId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

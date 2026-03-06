package com.gmaingret.outlinergod.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.gmaingret.outlinergod.db.dao.BookmarkDao
import com.gmaingret.outlinergod.db.dao.DocumentDao
import com.gmaingret.outlinergod.db.dao.NodeDao
import com.gmaingret.outlinergod.db.dao.SettingsDao
import com.gmaingret.outlinergod.network.model.SyncPushPayload
import com.gmaingret.outlinergod.repository.AuthRepository
import com.gmaingret.outlinergod.repository.SyncRepository
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// IMPORTANT: Token refresh is called before any network operation to pre-empt
// 401 retries from KtorClientFactory's interceptor. AuthRepository.refreshToken()
// is Mutex-guarded, so at most one refresh runs at a time.
@Singleton
class SyncOrchestratorImpl @Inject constructor(
    private val syncRepository: SyncRepository,
    private val authRepository: AuthRepository,
    private val nodeDao: NodeDao,
    private val documentDao: DocumentDao,
    private val bookmarkDao: BookmarkDao,
    private val settingsDao: SettingsDao,
    private val dataStore: DataStore<Preferences>
) : SyncOrchestrator {

    override suspend fun fullSync(): Result<Unit> = runCatching {
        // Step 1: Refresh token before sync to ensure fresh credentials
        val tokenResult = authRepository.refreshToken()
        if (tokenResult.isFailure) {
            throw tokenResult.exceptionOrNull() ?: RuntimeException("Token refresh failed")
        }

        val deviceId = authRepository.getDeviceId().first()
        val userId = authRepository.getUserId().filterNotNull().first()
        val storedHlc = dataStore.data.map { prefs ->
            prefs[SyncConstants.lastSyncHlcKey(userId)] ?: "0"
        }.first()
        // If Room is empty (e.g. reinstall with DataStore backup restored), force full pull.
        val hasLocalData = documentDao.countDocuments(userId) > 0
        val lastSyncHlc = if (hasLocalData) storedHlc else "0"

        // Step 2: PULL changes from server
        val pullResponse = syncRepository.pull(since = lastSyncHlc, deviceId = deviceId)
            .getOrThrow()

        // Apply pulled data — documents before nodes (application-level ordering)
        if (pullResponse.documents.isNotEmpty()) {
            documentDao.upsertDocuments(pullResponse.documents.map { it.toDocumentEntity() })
        }
        if (pullResponse.nodes.isNotEmpty()) {
            nodeDao.upsertNodes(pullResponse.nodes.map { it.toNodeEntity() })
        }
        if (pullResponse.bookmarks.isNotEmpty()) {
            bookmarkDao.upsertBookmarks(pullResponse.bookmarks.map { it.toBookmarkEntity() })
        }
        pullResponse.settings?.let { settings ->
            settingsDao.upsertSettings(settings.toSettingsEntity())
        }

        // Step 3: PUSH local changes to server
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

        val pushResponse = syncRepository.push(pushPayload).getOrThrow()

        // Apply conflict resolutions from server
        if (pushResponse.conflicts.nodes.isNotEmpty()) {
            nodeDao.upsertNodes(pushResponse.conflicts.nodes.map { it.toNodeEntity() })
        }
        if (pushResponse.conflicts.documents.isNotEmpty()) {
            documentDao.upsertDocuments(pushResponse.conflicts.documents.map { it.toDocumentEntity() })
        }
        if (pushResponse.conflicts.bookmarks.isNotEmpty()) {
            bookmarkDao.upsertBookmarks(pushResponse.conflicts.bookmarks.map { it.toBookmarkEntity() })
        }
        pushResponse.conflicts.settings?.let { settings ->
            settingsDao.upsertSettings(settings.toSettingsEntity())
        }

        // Step 4: Update last sync HLC
        dataStore.edit { prefs ->
            prefs[SyncConstants.lastSyncHlcKey(userId)] = pushResponse.serverHlc
        }
    }
}

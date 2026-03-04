package com.gmaingret.outlinergod.sync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gmaingret.outlinergod.db.dao.BookmarkDao
import com.gmaingret.outlinergod.db.dao.DocumentDao
import com.gmaingret.outlinergod.db.dao.NodeDao
import com.gmaingret.outlinergod.db.dao.SettingsDao
import com.gmaingret.outlinergod.network.model.SyncPushPayload
import com.gmaingret.outlinergod.repository.AuthRepository
import com.gmaingret.outlinergod.repository.SyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// IMPORTANT: Token refresh is called before any network operation to pre-empt
// 401 retries from KtorClientFactory's interceptor. AuthRepository.refreshToken()
// is Mutex-guarded (P3-12), so at most one refresh runs at a time.
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncRepository: SyncRepository,
    private val authRepository: AuthRepository,
    private val nodeDao: NodeDao,
    private val documentDao: DocumentDao,
    private val bookmarkDao: BookmarkDao,
    private val settingsDao: SettingsDao,
    private val dataStore: DataStore<Preferences>
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Step 1: Refresh token before sync to ensure fresh credentials
        val tokenResult = authRepository.refreshToken()
        if (tokenResult.isFailure) {
            return Result.failure()
        }

        val deviceId = authRepository.getDeviceId().first()
        val lastSyncHlc = dataStore.data.map { prefs ->
            prefs[SyncConstants.LAST_SYNC_HLC_KEY] ?: "0"
        }.first()

        // Step 2: PULL changes from server
        val pullResult = syncRepository.pull(since = lastSyncHlc, deviceId = deviceId)
        val pullResponse = pullResult.getOrElse { return Result.retry() }

        // Apply pulled data using upsert (server wins for new data)
        if (pullResponse.nodes.isNotEmpty()) {
            nodeDao.upsertNodes(pullResponse.nodes.map { it.toNodeEntity() })
        }
        if (pullResponse.documents.isNotEmpty()) {
            documentDao.upsertDocuments(pullResponse.documents.map { it.toDocumentEntity() })
        }
        if (pullResponse.bookmarks.isNotEmpty()) {
            bookmarkDao.upsertBookmarks(pullResponse.bookmarks.map { it.toBookmarkEntity() })
        }
        pullResponse.settings?.let { settings ->
            settingsDao.upsertSettings(settings.toSettingsEntity())
        }

        // Step 3: PUSH local changes to server
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
        val pushResponse = pushResult.getOrElse { return Result.retry() }

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
            prefs[SyncConstants.LAST_SYNC_HLC_KEY] = pushResponse.serverHlc
        }

        return Result.success()
    }

}

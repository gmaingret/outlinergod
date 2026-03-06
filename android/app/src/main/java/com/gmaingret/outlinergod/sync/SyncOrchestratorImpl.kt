package com.gmaingret.outlinergod.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.gmaingret.outlinergod.db.dao.BookmarkDao
import com.gmaingret.outlinergod.db.dao.DocumentDao
import com.gmaingret.outlinergod.db.dao.NodeDao
import com.gmaingret.outlinergod.db.dao.SettingsDao
import com.gmaingret.outlinergod.repository.AuthRepository
import com.gmaingret.outlinergod.repository.SyncRepository
import javax.inject.Inject
import javax.inject.Singleton

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

    override suspend fun fullSync(): Result<Unit> {
        // RED phase stub — not yet implemented
        return Result.failure(NotImplementedError("Not yet implemented"))
    }
}

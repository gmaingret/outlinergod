package com.gmaingret.outlinergod.sync

interface SyncOrchestrator {
    suspend fun fullSync(): Result<Unit>
}

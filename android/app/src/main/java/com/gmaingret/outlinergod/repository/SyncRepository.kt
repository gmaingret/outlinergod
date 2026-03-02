package com.gmaingret.outlinergod.repository

import com.gmaingret.outlinergod.network.model.SyncChangesResponse
import com.gmaingret.outlinergod.network.model.SyncPushPayload
import com.gmaingret.outlinergod.network.model.SyncPushResponse

interface SyncRepository {
    suspend fun pull(since: String, deviceId: String): Result<SyncChangesResponse>
    suspend fun push(payload: SyncPushPayload): Result<SyncPushResponse>
}

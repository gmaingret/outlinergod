package com.gmaingret.outlinergod.repository.impl

import com.gmaingret.outlinergod.network.model.SyncChangesResponse
import com.gmaingret.outlinergod.network.model.SyncPushPayload
import com.gmaingret.outlinergod.network.model.SyncPushResponse
import com.gmaingret.outlinergod.repository.SyncRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import javax.inject.Inject
import javax.inject.Named

class SyncRepositoryImpl @Inject constructor(
    private val httpClient: HttpClient,
    @Named("baseUrl") private val baseUrl: String
) : SyncRepository {

    override suspend fun pull(since: String, deviceId: String): Result<SyncChangesResponse> =
        runCatching {
            httpClient.get("$baseUrl/api/sync/changes") {
                parameter("since", since)
                parameter("device_id", deviceId)
            }.body()
        }

    override suspend fun push(payload: SyncPushPayload): Result<SyncPushResponse> =
        runCatching {
            httpClient.post("$baseUrl/api/sync/changes") {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }.body()
        }
}

package com.gmaingret.outlinergod.repository.impl

import com.gmaingret.outlinergod.network.model.SyncChangesResponse
import com.gmaingret.outlinergod.network.model.SyncPushPayload
import com.gmaingret.outlinergod.network.model.SyncPushResponse
import com.gmaingret.outlinergod.repository.SyncRepository
import com.gmaingret.outlinergod.util.SyncLogger
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

    override suspend fun pull(since: String, deviceId: String): Result<SyncChangesResponse> {
        SyncLogger.log("SyncRepo", "PULL start — baseUrl='$baseUrl' since='$since' deviceId='$deviceId'")
        return runCatching {
            val response = httpClient.get("$baseUrl/api/sync/changes") {
                parameter("since", since)
                parameter("device_id", deviceId)
            }
            SyncLogger.log("SyncRepo", "PULL raw response: HTTP ${response.status.value} ${response.status.description}")
            response.body<SyncChangesResponse>()
        }.also { result ->
            result.onSuccess { body ->
                SyncLogger.log(
                    "SyncRepo",
                    "PULL decoded OK — nodes=${body.nodes.size} docs=${body.documents.size} " +
                        "bookmarks=${body.bookmarks.size} settings=${body.settings != null} " +
                        "serverHlc='${body.serverHlc}'"
                )
            }.onFailure { e ->
                SyncLogger.logError("SyncRepo", "PULL failed", e)
            }
        }
    }

    override suspend fun push(payload: SyncPushPayload): Result<SyncPushResponse> {
        SyncLogger.log(
            "SyncRepo",
            "PUSH start — deviceId='${payload.deviceId}' " +
                "nodes=${payload.nodes?.size ?: 0} docs=${payload.documents?.size ?: 0} " +
                "bookmarks=${payload.bookmarks?.size ?: 0} settings=${payload.settings != null}"
        )
        return runCatching {
            val response = httpClient.post("$baseUrl/api/sync/changes") {
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            SyncLogger.log("SyncRepo", "PUSH raw response: HTTP ${response.status.value} ${response.status.description}")
            response.body<SyncPushResponse>()
        }.also { result ->
            result.onSuccess { body ->
                SyncLogger.log(
                    "SyncRepo",
                    "PUSH decoded OK — serverHlc='${body.serverHlc}' " +
                        "accepted(nodes=${body.acceptedNodeIds.size} docs=${body.acceptedDocumentIds.size} bookmarks=${body.acceptedBookmarkIds.size}) " +
                        "conflicts(nodes=${body.conflicts.nodes.size} docs=${body.conflicts.documents.size} bookmarks=${body.conflicts.bookmarks.size})"
                )
            }.onFailure { e ->
                SyncLogger.logError("SyncRepo", "PUSH failed", e)
            }
        }
    }
}

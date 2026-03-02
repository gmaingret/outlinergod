package com.gmaingret.outlinergod.repository

import com.gmaingret.outlinergod.network.model.*
import com.gmaingret.outlinergod.repository.impl.SyncRepositoryImpl
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.*

class SyncRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun makeClient(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
    }

    @Test
    fun pull_callsCorrectEndpoint_withQueryParams() = runTest {
        var capturedUrl: String? = null
        var capturedSince: String? = null
        var capturedDeviceId: String? = null
        val mockEngine = MockEngine { request ->
            capturedUrl = request.url.encodedPath
            capturedSince = request.url.parameters["since"]
            capturedDeviceId = request.url.parameters["device_id"]
            val response = SyncChangesResponse(serverHlc = "AAAA")
            respond(
                content = ByteReadChannel(json.encodeToString(response)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = SyncRepositoryImpl(makeClient(mockEngine), "http://localhost:3000")
        repo.pull("0", "device1")
        assertTrue(capturedUrl?.contains("/api/sync/changes") == true)
        assertEquals("0", capturedSince)
        assertEquals("device1", capturedDeviceId)
    }

    @Test
    fun pull_parsesResponse_correctly() = runTest {
        val mockEngine = MockEngine { _ ->
            val response = SyncChangesResponse(serverHlc = "AAAA")
            respond(
                content = ByteReadChannel(json.encodeToString(response)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = SyncRepositoryImpl(makeClient(mockEngine), "http://localhost:3000")
        val result = repo.pull("0", "d")
        assertTrue(result.isSuccess)
        assertEquals("AAAA", result.getOrNull()?.serverHlc)
        assertTrue(result.getOrNull()?.nodes?.isEmpty() == true)
    }

    @Test
    fun pull_since_sentinelZero_isPassedAsLiteralString() = runTest {
        var capturedSince: String? = null
        val mockEngine = MockEngine { request ->
            capturedSince = request.url.parameters["since"]
            val response = SyncChangesResponse(serverHlc = "0")
            respond(
                content = ByteReadChannel(json.encodeToString(response)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = SyncRepositoryImpl(makeClient(mockEngine), "http://localhost:3000")
        repo.pull("0", "d")
        assertEquals("0", capturedSince)
    }

    @Test
    fun pull_returnsFailure_onNetworkError() = runTest {
        val mockEngine = MockEngine { _ ->
            throw java.io.IOException("Network error")
        }
        val repo = SyncRepositoryImpl(makeClient(mockEngine), "http://localhost:3000")
        val result = repo.pull("0", "d")
        assertTrue(result.isFailure)
    }

    @Test
    fun push_callsCorrectEndpoint_withJsonBody() = runTest {
        var capturedPath: String? = null
        var capturedContentType: ContentType? = null
        val mockEngine = MockEngine { request ->
            capturedPath = request.url.encodedPath
            capturedContentType = request.body.contentType
            val response = SyncPushResponse(serverHlc = "AAAA")
            respond(
                content = ByteReadChannel(json.encodeToString(response)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = SyncRepositoryImpl(makeClient(mockEngine), "http://localhost:3000")
        repo.push(SyncPushPayload(deviceId = "d1"))
        assertTrue(capturedPath?.contains("/api/sync/changes") == true)
        assertEquals(ContentType.Application.Json, capturedContentType?.withoutParameters())
    }

    @Test
    fun push_parsesConflictsInResponse() = runTest {
        val conflictNode = NodeSyncRecord(id = "n1", documentId = "d1", userId = "u1")
        val pushResponse = SyncPushResponse(
            serverHlc = "AAAA",
            conflicts = SyncConflicts(nodes = listOf(conflictNode))
        )
        val mockEngine = MockEngine { _ ->
            respond(
                content = ByteReadChannel(json.encodeToString(pushResponse)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = SyncRepositoryImpl(makeClient(mockEngine), "http://localhost:3000")
        val result = repo.push(SyncPushPayload(deviceId = "d1"))
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.conflicts?.nodes?.size)
    }

    @Test
    fun push_returnsFailure_onNetworkError() = runTest {
        val mockEngine = MockEngine { _ ->
            throw java.io.IOException("Timeout")
        }
        val repo = SyncRepositoryImpl(makeClient(mockEngine), "http://localhost:3000")
        val result = repo.push(SyncPushPayload(deviceId = "d1"))
        assertTrue(result.isFailure)
    }
}

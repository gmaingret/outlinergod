package com.gmaingret.outlinergod.network

import com.gmaingret.outlinergod.network.model.SyncChangesResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.plugin
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KtorClientFactoryTest {

    @Test
    fun `attaches bearer token on every request`() = runTest {
        var capturedAuth: String? = null
        val mockEngine = MockEngine { request ->
            capturedAuth = request.headers[HttpHeaders.Authorization]
            respond("", HttpStatusCode.OK)
        }
        val client = buildTestClient(
            mockEngine,
            tokenProvider = { "mytoken" },
            tokenRefresher = { null }
        )
        client.get("https://example.com/test")
        assertEquals("Bearer mytoken", capturedAuth)
    }

    @Test
    fun `intercepts 401 and retries with refreshed token`() = runTest {
        var callCount = 0
        var lastAuth: String? = null
        val mockEngine = MockEngine { request ->
            callCount++
            lastAuth = request.headers[HttpHeaders.Authorization]
            if (callCount == 1) {
                respond("", HttpStatusCode.Unauthorized)
            } else {
                respond("", HttpStatusCode.OK)
            }
        }
        val client = buildTestClient(
            mockEngine,
            tokenProvider = { "oldtoken" },
            tokenRefresher = { "newtoken" }
        )
        val response = client.get("https://example.com/test")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Bearer newtoken", lastAuth)
        assertEquals(2, callCount)
    }

    @Test
    fun `does not infinite retry when refresh fails`() = runTest {
        var callCount = 0
        val mockEngine = MockEngine { _ ->
            callCount++
            respond("", HttpStatusCode.Unauthorized)
        }
        val client = buildTestClient(
            mockEngine,
            tokenProvider = { "token" },
            tokenRefresher = { null }
        )
        val response = client.get("https://example.com/test")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(1, callCount)
    }

    @Test
    fun `deserializes JSON response correctly`() = runTest {
        val json =
            """{"server_hlc":"AAAA","nodes":[],"documents":[],"settings":null,"bookmarks":[]}"""
        val mockEngine = MockEngine { _ ->
            respond(
                content = ByteReadChannel(json),
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString()
                )
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
        }
        val response: SyncChangesResponse = client.get("https://example.com/sync").body()
        assertEquals("AAAA", response.serverHlc)
        assertTrue(response.nodes.isEmpty())
    }

    private fun buildTestClient(
        engine: MockEngine,
        tokenProvider: suspend () -> String?,
        tokenRefresher: suspend () -> String?
    ) = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }.apply {
        plugin(HttpSend).intercept { request ->
            val token = tokenProvider()
            if (token != null) {
                request.headers[HttpHeaders.Authorization] = "Bearer $token"
            }
            val call = execute(request)
            if (call.response.status == HttpStatusCode.Unauthorized) {
                val newToken = tokenRefresher()
                if (newToken != null) {
                    request.headers[HttpHeaders.Authorization] = "Bearer $newToken"
                    execute(request)
                } else {
                    call
                }
            } else {
                call
            }
        }
    }
}

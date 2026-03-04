package com.gmaingret.outlinergod.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.plugin
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import com.gmaingret.outlinergod.util.SyncLogger

object KtorClientFactory {

    fun create(
        tokenProvider: suspend () -> String?,
        tokenRefresher: suspend () -> String?
    ): HttpClient = create(OkHttp, tokenProvider, tokenRefresher)

    internal fun <T : HttpClientEngineConfig> create(
        engine: HttpClientEngineFactory<T>,
        tokenProvider: suspend () -> String?,
        tokenRefresher: suspend () -> String?
    ): HttpClient = HttpClient(engine) {

        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            })
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000L
            connectTimeoutMillis = 10_000L
        }

        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 3)
            exponentialDelay(base = 2.0, maxDelayMs = 30_000L)
        }
    }.apply {
        plugin(HttpSend).intercept { request ->
            val token = tokenProvider()
            val urlStr = request.url.buildString()
            val method = request.method.value
            SyncLogger.log("Ktor", "→ $method $urlStr token=${if (token != null) "present" else "MISSING"}")
            if (token != null) {
                request.headers[HttpHeaders.Authorization] = "Bearer $token"
            }
            val call = execute(request)
            val status = call.response.status
            SyncLogger.log("Ktor", "← ${status.value} ${status.description} ($method $urlStr)")
            if (status == HttpStatusCode.Unauthorized) {
                SyncLogger.log("Ktor", "401 received — attempting token refresh for $urlStr")
                val newToken = tokenRefresher()
                if (newToken != null) {
                    SyncLogger.log("Ktor", "Token refreshed OK, retrying $method $urlStr")
                    request.headers[HttpHeaders.Authorization] = "Bearer $newToken"
                    val retryCall = execute(request)
                    SyncLogger.log("Ktor", "← (retry) ${retryCall.response.status.value} ${retryCall.response.status.description}")
                    retryCall
                } else {
                    SyncLogger.log("Ktor", "Token refresh returned null — giving up on $urlStr")
                    call
                }
            } else {
                call
            }
        }
    }
}

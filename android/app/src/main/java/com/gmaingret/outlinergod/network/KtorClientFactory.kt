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

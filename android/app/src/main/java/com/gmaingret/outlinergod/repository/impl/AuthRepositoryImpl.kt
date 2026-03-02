package com.gmaingret.outlinergod.repository.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gmaingret.outlinergod.network.model.AuthResponse
import com.gmaingret.outlinergod.network.model.TokenPair
import com.gmaingret.outlinergod.network.model.UserProfile
import com.gmaingret.outlinergod.repository.AuthRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named

class AuthRepositoryImpl @Inject constructor(
    private val httpClient: HttpClient,
    private val dataStore: DataStore<Preferences>,
    @Named("baseUrl") private val baseUrl: String
) : AuthRepository {

    private val refreshMutex = Mutex()

    companion object {
        private val ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
        private val DEVICE_ID_KEY = stringPreferencesKey("device_id")
    }

    override suspend fun googleSignIn(idToken: String): Result<AuthResponse> = runCatching {
        val response: AuthResponse = httpClient.post("$baseUrl/api/auth/google") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("idToken" to idToken))
        }.body()
        dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN] = response.token
            prefs[REFRESH_TOKEN_KEY] = response.refreshToken
        }
        response
    }

    override suspend fun refreshToken(): Result<TokenPair> = runCatching {
        refreshMutex.withLock {
            val currentRefreshToken = dataStore.data.first()[REFRESH_TOKEN_KEY]
                ?: error("No refresh token stored")
            val response: TokenPair = httpClient.post("$baseUrl/api/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("refresh_token" to currentRefreshToken))
            }.body()
            dataStore.edit { prefs ->
                prefs[ACCESS_TOKEN] = response.token
                prefs[REFRESH_TOKEN_KEY] = response.refreshToken
            }
            response
        }
    }

    override suspend fun getMe(): Result<UserProfile> = runCatching {
        httpClient.get("$baseUrl/api/auth/me").body()
    }

    override suspend fun logout(refreshToken: String): Result<Unit> = runCatching {
        httpClient.post("$baseUrl/api/auth/logout") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("refresh_token" to refreshToken))
        }
        dataStore.edit { prefs ->
            prefs.remove(ACCESS_TOKEN)
            prefs.remove(REFRESH_TOKEN_KEY)
        }
    }

    override fun getAccessToken(): Flow<String?> =
        dataStore.data.map { prefs -> prefs[ACCESS_TOKEN] }

    private suspend fun getOrCreateDeviceId(): String {
        val existing = dataStore.data.first()[DEVICE_ID_KEY]
        if (existing != null) return existing
        val newId = UUID.randomUUID().toString()
        dataStore.edit { it[DEVICE_ID_KEY] = newId }
        return newId
    }

    override fun getDeviceId(): Flow<String> = flow {
        emit(getOrCreateDeviceId())
    }
}

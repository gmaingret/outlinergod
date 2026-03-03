package com.gmaingret.outlinergod.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.gmaingret.outlinergod.network.model.AuthResponse
import com.gmaingret.outlinergod.network.model.UserProfile
import com.gmaingret.outlinergod.repository.impl.AuthRepositoryImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AuthRepositoryTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder.builder().assureDeletion().build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun createDataStore(name: String) =
        PreferenceDataStoreFactory.create {
            File(tmpFolder.root, "$name.preferences_pb")
        }

    private fun mockUserProfile() = UserProfile(
        id = "uid1",
        googleSub = "gsub1",
        email = "test@test.com",
        name = "Test User",
        picture = "https://pic.example.com"
    )

    private fun mockAuthResponse() = AuthResponse(
        token = "access-token-123",
        refreshToken = "refresh-token-456",
        user = mockUserProfile(),
        isNewUser = false
    )

    private fun successClient(): HttpClient {
        val mockEngine = MockEngine { _ ->
            respond(
                content = ByteReadChannel(json.encodeToString(mockAuthResponse())),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        return HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }
    }

    @Test
    fun googleSignIn_storesAccessToken() = runTest {
        val dataStore = createDataStore("test1")
        val repo = AuthRepositoryImpl(successClient(), dataStore, "http://localhost:3000")

        repo.googleSignIn("id-token")

        assertEquals("access-token-123", repo.getAccessToken().first())
    }

    @Test
    fun googleSignIn_returnsAuthResponse() = runTest {
        val dataStore = createDataStore("test2")
        val repo = AuthRepositoryImpl(successClient(), dataStore, "http://localhost:3000")

        val result = repo.googleSignIn("id-token")

        assertTrue(result.isSuccess)
        assertNotNull(repo.getAccessToken().first())
    }

    @Test
    fun googleSignIn_returnsFailure_onNetworkError() = runTest {
        val dataStore = createDataStore("test3")
        val mockEngine = MockEngine { _ ->
            throw java.io.IOException("Network error")
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }
        val repo = AuthRepositoryImpl(client, dataStore, "http://localhost:3000")

        val result = repo.googleSignIn("id-token")

        assertTrue(result.isFailure)
    }

    @Test
    fun deviceId_isStableAcrossMultipleCalls() = runTest {
        val dataStore = createDataStore("test4")
        val mockEngine = MockEngine { respond("", HttpStatusCode.OK) }
        val client = HttpClient(mockEngine)
        val repo = AuthRepositoryImpl(client, dataStore, "http://localhost:3000")

        val id1 = repo.getDeviceId().first()
        val id2 = repo.getDeviceId().first()

        assertEquals(id1, id2)
        assertTrue(id1.matches(Regex("[0-9a-f-]{36}")))
    }

    @Test
    fun logout_clearsAccessToken() = runTest {
        val dataStore = createDataStore("test5")
        val repo = AuthRepositoryImpl(successClient(), dataStore, "http://localhost:3000")

        repo.googleSignIn("id-token")
        assertNotNull(repo.getAccessToken().first())

        repo.logout("rt")
        assertNull(repo.getAccessToken().first())
    }

    @Test
    fun googleSignIn_storesUserId() = runTest {
        val dataStore = createDataStore("test7")
        val repo = AuthRepositoryImpl(successClient(), dataStore, "http://localhost:3000")

        repo.googleSignIn("id-token")

        assertEquals("uid1", repo.getUserId().first())
    }

    @Test
    fun logout_clearsUserId() = runTest {
        val dataStore = createDataStore("test8")
        val repo = AuthRepositoryImpl(successClient(), dataStore, "http://localhost:3000")

        repo.googleSignIn("id-token")
        assertEquals("uid1", repo.getUserId().first())

        repo.logout("rt")
        assertNull(repo.getUserId().first())
    }

    @Test
    fun getMe_returnsUserProfile_onSuccess() = runTest {
        val dataStore = createDataStore("test6")
        val profile = mockUserProfile()
        val mockEngine = MockEngine { _ ->
            respond(
                content = ByteReadChannel(json.encodeToString(profile)),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }
        val repo = AuthRepositoryImpl(client, dataStore, "http://localhost:3000")

        val result = repo.getMe()

        assertTrue(result.isSuccess)
        assertEquals("test@test.com", result.getOrNull()?.email)
    }
}

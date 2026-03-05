package com.gmaingret.outlinergod.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.gmaingret.outlinergod.repository.impl.FileRepositoryImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

class FileRepositoryImplTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver
    private lateinit var mockUri: Uri
    private val fakeFileBytes = "fake-file-content".toByteArray()

    @Before
    fun setUp() {
        context = mockk()
        contentResolver = mockk()
        mockUri = mockk()
        every { context.contentResolver } returns contentResolver
        every { contentResolver.openInputStream(mockUri) } returns ByteArrayInputStream(fakeFileBytes)
    }

    private fun makeClient(engine: MockEngine) = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
    }

    @Test
    fun uploadFile_success_returnsUploadedFile() = runTest {
        val responseJson = """
            {
                "url": "/api/files/abc123.jpg",
                "uuid": "abc123",
                "filename": "abc123.jpg",
                "size": 17,
                "mime_type": "image/jpeg"
            }
        """.trimIndent()

        val mockEngine = MockEngine { _ ->
            respond(
                content = ByteReadChannel(responseJson),
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val repo = FileRepositoryImpl(makeClient(mockEngine), "http://localhost:3000", context)
        val result = repo.uploadFile("node-1", mockUri, "image/jpeg")

        assertTrue(result.isSuccess)
        val file = result.getOrNull()!!
        assertEquals("/api/files/abc123.jpg", file.url)
        assertEquals("abc123.jpg", file.filename)
        assertEquals("image/jpeg", file.mimeType)
    }

    @Test
    fun uploadFile_serverError_returnsFailure() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("Internal Server Error"),
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }

        val repo = FileRepositoryImpl(makeClient(mockEngine), "http://localhost:3000", context)
        val result = repo.uploadFile("node-1", mockUri, "image/jpeg")

        assertTrue(result.isFailure)
    }

    @Test
    fun uploadFile_tooLarge_returnsFailure() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = ByteReadChannel("Payload Too Large"),
                status = HttpStatusCode.PayloadTooLarge,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }

        val repo = FileRepositoryImpl(makeClient(mockEngine), "http://localhost:3000", context)
        val result = repo.uploadFile("node-1", mockUri, "application/pdf")

        assertTrue(result.isFailure)
    }
}

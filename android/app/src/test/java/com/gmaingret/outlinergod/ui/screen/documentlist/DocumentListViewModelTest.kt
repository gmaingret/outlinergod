package com.gmaingret.outlinergod.ui.screen.documentlist

import com.gmaingret.outlinergod.db.dao.DocumentDao
import com.gmaingret.outlinergod.db.entity.DocumentEntity
import com.gmaingret.outlinergod.repository.AuthRepository
import com.gmaingret.outlinergod.sync.HlcClock
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.orbitmvi.orbit.test.test

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var documentDao: DocumentDao
    private lateinit var authRepository: AuthRepository
    private lateinit var hlcClock: HlcClock
    private lateinit var httpClient: HttpClient
    private val baseUrl = "http://localhost:3000"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        documentDao = mockk(relaxed = true)
        authRepository = mockk()
        hlcClock = mockk()
        every { authRepository.getAccessToken() } returns flowOf("user-1")
        every { authRepository.getDeviceId() } returns flowOf("device-1")
        every { hlcClock.generate(any()) } returns "0000017b05a3a1be-0000-device-1"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        if (::httpClient.isInitialized) {
            httpClient.close()
        }
    }

    private fun fakeDocument(
        id: String = "doc-1",
        title: String = "Test Document",
        sortOrder: String = "a0",
        collapsed: Int = 0
    ) = DocumentEntity(
        id = id,
        userId = "user-1",
        title = title,
        titleHlc = "0000017b05a3a1be-0000-device-1",
        type = "document",
        parentId = null,
        parentIdHlc = "",
        sortOrder = sortOrder,
        sortOrderHlc = "",
        collapsed = collapsed,
        collapsedHlc = "",
        deletedAt = null,
        deletedHlc = "",
        deviceId = "device-1",
        createdAt = 1000L,
        updatedAt = 1000L,
        syncStatus = 0
    )

    private fun createMockHttpClient(statusCode: HttpStatusCode = HttpStatusCode.Created): HttpClient {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{"id":"doc-new","title":"Doc"}""",
                status = statusCode,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        return HttpClient(mockEngine) {
            install(ContentNegotiation) { json() }
        }
    }

    private fun createViewModel(): DocumentListViewModel {
        return DocumentListViewModel(
            documentDao = documentDao,
            authRepository = authRepository,
            hlcClock = hlcClock,
            baseUrl = baseUrl,
            httpClient = httpClient
        )
    }

    @Test
    fun `initialLoad emits Success with documents`() = runTest {
        val docs = listOf(fakeDocument())
        every { documentDao.getAllDocuments("user-1") } returns flowOf(docs)
        httpClient = createMockHttpClient()
        val viewModel = createViewModel()
        viewModel.test(this) {
            // Explicitly trigger loadDocuments (init's call went to the real container)
            containerHost.loadDocuments()
            expectState(DocumentListUiState.Success(items = docs))
        }
    }

    @Test
    fun `initialLoad with empty list emits Success with empty items`() = runTest {
        every { documentDao.getAllDocuments("user-1") } returns flowOf(emptyList())
        httpClient = createMockHttpClient()
        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadDocuments()
            expectState(DocumentListUiState.Success(items = emptyList()))
        }
    }

    @Test
    fun `createDocument inserts document locally on success`() = runTest {
        every { documentDao.getAllDocuments("user-1") } returns flowOf(emptyList())
        coEvery { documentDao.insertDocument(any()) } just Runs
        httpClient = createMockHttpClient()
        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.createDocument("Doc", "document", null, "V")
        }
        coVerify { documentDao.insertDocument(any()) }
    }

    @Test
    fun `createDocument posts ShowError on failure`() = runTest {
        every { documentDao.getAllDocuments("user-1") } returns flowOf(emptyList())
        coEvery { documentDao.insertDocument(any()) } throws RuntimeException("DB error")
        httpClient = createMockHttpClient()
        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.createDocument("Doc", "document", null, "V")
            expectSideEffect(DocumentListSideEffect.ShowError("DB error"))
        }
    }

    @Test
    fun `deleteDocument soft deletes immediately before network call`() = runTest {
        every { documentDao.getAllDocuments("user-1") } returns flowOf(listOf(fakeDocument()))
        coEvery { documentDao.softDeleteDocument(any(), any(), any(), any()) } just Runs
        httpClient = createMockHttpClient()
        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.deleteDocument("doc-1")
        }
        coVerify { documentDao.softDeleteDocument(eq("doc-1"), any(), any(), any()) }
    }

    @Test
    fun `toggleFolderCollapse toggles collapsed bit`() = runTest {
        val folder = fakeDocument(id = "f1", collapsed = 0)
        every { documentDao.getAllDocuments("user-1") } returns flowOf(listOf(folder))
        coEvery { documentDao.getDocumentByIdSync("f1") } returns folder
        val slot = slot<DocumentEntity>()
        coEvery { documentDao.updateDocument(capture(slot)) } just Runs
        httpClient = createMockHttpClient()
        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.toggleFolderCollapse("f1")
        }
        assertEquals(1, slot.captured.collapsed)
    }

    @Test
    fun `renameDocument updates HLC timestamp`() = runTest {
        val doc = fakeDocument(id = "doc-1")
        every { documentDao.getAllDocuments("user-1") } returns flowOf(listOf(doc))
        coEvery { documentDao.getDocumentByIdSync("doc-1") } returns doc
        val slot = slot<DocumentEntity>()
        coEvery { documentDao.updateDocument(capture(slot)) } just Runs
        httpClient = createMockHttpClient()
        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.renameDocument("doc-1", "New Name")
        }
        assertTrue(
            "Expected HLC format but got '${slot.captured.titleHlc}'",
            slot.captured.titleHlc.matches(Regex("^[0-9a-f]{16}-[0-9a-f]{4}-.*"))
        )
        assertEquals("New Name", slot.captured.title)
    }
}

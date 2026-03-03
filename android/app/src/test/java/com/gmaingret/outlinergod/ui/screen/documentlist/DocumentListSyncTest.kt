package com.gmaingret.outlinergod.ui.screen.documentlist

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.gmaingret.outlinergod.ui.common.SyncStatus
import com.gmaingret.outlinergod.db.dao.BookmarkDao
import com.gmaingret.outlinergod.db.dao.DocumentDao
import com.gmaingret.outlinergod.db.dao.NodeDao
import com.gmaingret.outlinergod.db.dao.SettingsDao
import com.gmaingret.outlinergod.network.model.NodeSyncRecord
import com.gmaingret.outlinergod.network.model.SyncChangesResponse
import com.gmaingret.outlinergod.network.model.SyncConflicts
import com.gmaingret.outlinergod.network.model.SyncPushResponse
import com.gmaingret.outlinergod.repository.AuthRepository
import com.gmaingret.outlinergod.repository.SyncRepository
import com.gmaingret.outlinergod.sync.HlcClock
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.orbitmvi.orbit.test.test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentListSyncTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var documentDao: DocumentDao
    private lateinit var nodeDao: NodeDao
    private lateinit var bookmarkDao: BookmarkDao
    private lateinit var settingsDao: SettingsDao
    private lateinit var authRepository: AuthRepository
    private lateinit var syncRepository: SyncRepository
    private lateinit var hlcClock: HlcClock
    private lateinit var httpClient: HttpClient
    private lateinit var dataStore: DataStore<Preferences>
    private val baseUrl = "http://localhost:3000"

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        documentDao = mockk(relaxed = true)
        nodeDao = mockk(relaxed = true)
        bookmarkDao = mockk(relaxed = true)
        settingsDao = mockk(relaxed = true)
        authRepository = mockk()
        syncRepository = mockk(relaxed = true)
        hlcClock = mockk()
        dataStore = PreferenceDataStoreFactory.create(scope = testScope) {
            tempDir.newFile("test_prefs.preferences_pb")
        }
        every { authRepository.getAccessToken() } returns flowOf("user-1")
        every { authRepository.getUserId() } returns flowOf("user-1")
        every { authRepository.getDeviceId() } returns flowOf("device-1")
        every { hlcClock.generate(any()) } returns "1636300202430-00000-device-1"

        val mockEngine = MockEngine { _ ->
            respond(
                content = """{"id":"doc-new","title":"Doc"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json() }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        httpClient.close()
    }

    private fun fakePullResponse(
        nodes: List<NodeSyncRecord> = emptyList(),
        serverHlc: String = "AAAA"
    ) = SyncChangesResponse(
        serverHlc = serverHlc,
        nodes = nodes
    )

    private fun fakePushResponse(
        serverHlc: String = "AAAA"
    ) = SyncPushResponse(
        serverHlc = serverHlc,
        conflicts = SyncConflicts()
    )

    private fun createViewModel(): DocumentListViewModel {
        return DocumentListViewModel(
            documentDao = documentDao,
            nodeDao = nodeDao,
            bookmarkDao = bookmarkDao,
            settingsDao = settingsDao,
            authRepository = authRepository,
            syncRepository = syncRepository,
            hlcClock = hlcClock,
            baseUrl = baseUrl,
            httpClient = httpClient,
            dataStore = dataStore
        )
    }

    @Test
    fun `triggerSync sets Syncing then Idle on success`() = runTest {
        every { documentDao.getAllDocuments("user-1") } returns flowOf(emptyList())
        coEvery { syncRepository.pull(any(), any()) } returns Result.success(fakePullResponse())
        coEvery { syncRepository.push(any()) } returns Result.success(fakePushResponse())

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.triggerSync()
            expectState(DocumentListUiState.Success(syncStatus = SyncStatus.Syncing))
            expectState(DocumentListUiState.Success(syncStatus = SyncStatus.Idle))
        }
    }

    @Test
    fun `triggerSync sets Error status on network failure`() = runTest {
        every { documentDao.getAllDocuments("user-1") } returns flowOf(emptyList())
        coEvery { syncRepository.pull(any(), any()) } returns Result.failure(IOException("Network error"))

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.triggerSync()
            expectState(DocumentListUiState.Success(syncStatus = SyncStatus.Syncing))
            expectState(DocumentListUiState.Success(syncStatus = SyncStatus.Error))
        }
    }

    @Test
    fun `triggerSync upserts nodes from pull response`() = runTest {
        every { documentDao.getAllDocuments("user-1") } returns flowOf(emptyList())
        val nodes = listOf(
            NodeSyncRecord(id = "n1", documentId = "doc-1", userId = "user-1", sortOrder = "a0"),
            NodeSyncRecord(id = "n2", documentId = "doc-1", userId = "user-1", sortOrder = "a1")
        )
        coEvery { syncRepository.pull(any(), any()) } returns Result.success(fakePullResponse(nodes = nodes))
        coEvery { syncRepository.push(any()) } returns Result.success(fakePushResponse())

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.triggerSync()
            expectState(DocumentListUiState.Success(syncStatus = SyncStatus.Syncing))
            expectState(DocumentListUiState.Success(syncStatus = SyncStatus.Idle))
        }
        coVerify { nodeDao.upsertNodes(match { it.size == 2 }) }
    }

    @Test
    fun `triggerSync updates lastSyncHlc after successful push`() = runTest {
        every { documentDao.getAllDocuments("user-1") } returns flowOf(emptyList())
        coEvery { syncRepository.pull(any(), any()) } returns Result.success(fakePullResponse())
        coEvery { syncRepository.push(any()) } returns Result.success(fakePushResponse(serverHlc = "BBBB"))

        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.triggerSync()
            expectState(DocumentListUiState.Success(syncStatus = SyncStatus.Syncing))
            expectState(DocumentListUiState.Success(syncStatus = SyncStatus.Idle))
        }

        // Verify the DataStore was updated
        val hlcValue = dataStore.data.map { prefs ->
            prefs[DocumentListViewModel.LAST_SYNC_HLC_KEY]
        }.first()
        assertEquals("BBBB", hlcValue)
    }

    @Test
    fun `onScreenResumed calls triggerSync`() = runTest {
        every { documentDao.getAllDocuments("user-1") } returns flowOf(emptyList())
        coEvery { syncRepository.pull(any(), any()) } returns Result.success(fakePullResponse())
        coEvery { syncRepository.push(any()) } returns Result.success(fakePushResponse())

        val viewModel = createViewModel()
        // Use Orbit test to verify triggerSync is invoked via onScreenResumed
        // onScreenResumed uses viewModelScope, but Orbit test intercepts intents
        viewModel.test(this) {
            containerHost.onScreenResumed()
            // If triggerSync was called, we should see the Syncing then Idle states
            expectState(DocumentListUiState.Success(syncStatus = SyncStatus.Syncing))
            expectState(DocumentListUiState.Success(syncStatus = SyncStatus.Idle))
        }
    }
}

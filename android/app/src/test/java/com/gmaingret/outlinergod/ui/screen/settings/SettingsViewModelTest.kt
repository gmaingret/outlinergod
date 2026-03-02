package com.gmaingret.outlinergod.ui.screen.settings

import com.gmaingret.outlinergod.db.dao.SettingsDao
import com.gmaingret.outlinergod.db.entity.SettingsEntity
import com.gmaingret.outlinergod.repository.AuthRepository
import com.gmaingret.outlinergod.sync.HlcClock
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
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var settingsDao: SettingsDao
    private lateinit var authRepository: AuthRepository
    private lateinit var hlcClock: HlcClock

    private fun fakeSettings(
        userId: String = "u1",
        theme: String = "dark",
        density: String = "cozy",
        showGuideLines: Int = 1,
        showBacklinkBadge: Int = 1
    ) = SettingsEntity(
        userId = userId,
        theme = theme,
        themeHlc = "0000017b05a3a1be-0000-device-1",
        density = density,
        densityHlc = "0000017b05a3a1be-0000-device-1",
        showGuideLines = showGuideLines,
        showGuideLinesHlc = "0000017b05a3a1be-0000-device-1",
        showBacklinkBadge = showBacklinkBadge,
        showBacklinkBadgeHlc = "0000017b05a3a1be-0000-device-1",
        deviceId = "device-1",
        updatedAt = 1000L
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        settingsDao = mockk(relaxed = true)
        authRepository = mockk()
        hlcClock = mockk()
        every { authRepository.getAccessToken() } returns flowOf("u1")
        every { authRepository.getDeviceId() } returns flowOf("device-1")
        every { hlcClock.generate(any()) } returns "0000017b05a3a1be-0001-device-1"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SettingsViewModel {
        return SettingsViewModel(settingsDao, authRepository, hlcClock)
    }

    @Test
    fun `initialLoad withExistingSettings emitsSuccess`() = runTest {
        val settings = fakeSettings()
        every { settingsDao.getSettings("u1") } returns flowOf(settings)
        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadSettings()
            expectState(SettingsUiState.Success(settings = settings))
        }
    }

    @Test
    fun `initialLoad withNoSettings insertsDefaults`() = runTest {
        every { settingsDao.getSettings("u1") } returns flowOf(null)
        coEvery { settingsDao.upsertSettings(any()) } just Runs
        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadSettings()
        }
        val captured = mutableListOf<SettingsEntity>()
        coVerify { settingsDao.upsertSettings(capture(captured)) }
        assertTrue("Expected at least 1 upsert call", captured.isNotEmpty())
        val defaultEntity = captured.first()
        assertEquals("dark", defaultEntity.theme)
        assertEquals("cozy", defaultEntity.density)
    }

    @Test
    fun `updateTheme dark to light upsertsEntity`() = runTest {
        val settings = fakeSettings(theme = "dark")
        every { settingsDao.getSettings("u1") } returns flowOf(settings)
        coEvery { settingsDao.getSettingsSync("u1") } returns settings
        coEvery { settingsDao.upsertSettings(any()) } just Runs
        val viewModel = createViewModel()
        viewModel.test(this) {
            // Set userId/deviceId by loading settings first
            containerHost.loadSettings()
            expectState(SettingsUiState.Success(settings = settings))
            containerHost.updateTheme("light")
        }
        val slot = slot<SettingsEntity>()
        // Capture the last upsert call (the updateTheme one)
        coVerify(atLeast = 1) { settingsDao.upsertSettings(capture(slot)) }
        assertEquals("light", slot.captured.theme)
        assertTrue(slot.captured.themeHlc.isNotBlank())
    }

    @Test
    fun `updateTheme invalidValue doesNotUpsert`() = runTest {
        val settings = fakeSettings()
        every { settingsDao.getSettings("u1") } returns flowOf(settings)
        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadSettings()
            expectState(SettingsUiState.Success(settings = settings))
            containerHost.updateTheme("sepia")
        }
        coVerify(exactly = 0) { settingsDao.getSettingsSync(any()) }
    }

    @Test
    fun `updateDensity validValues accepted`() = runTest {
        val settings = fakeSettings()
        every { settingsDao.getSettings("u1") } returns flowOf(settings)
        coEvery { settingsDao.getSettingsSync("u1") } returns settings
        coEvery { settingsDao.upsertSettings(any()) } just Runs
        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadSettings()
            expectState(SettingsUiState.Success(settings = settings))
            containerHost.updateDensity("compact")
        }
        val slot = slot<SettingsEntity>()
        coVerify(atLeast = 1) { settingsDao.upsertSettings(capture(slot)) }
        assertEquals("compact", slot.captured.density)
    }

    @Test
    fun `updateDensity invalidValue doesNotUpsert`() = runTest {
        val settings = fakeSettings()
        every { settingsDao.getSettings("u1") } returns flowOf(settings)
        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadSettings()
            expectState(SettingsUiState.Success(settings = settings))
            containerHost.updateDensity("ultracompact")
        }
        coVerify(exactly = 0) { settingsDao.getSettingsSync(any()) }
    }

    @Test
    fun `toggleGuideLines flipsZeroToOne`() = runTest {
        val settings = fakeSettings(showGuideLines = 0)
        every { settingsDao.getSettings("u1") } returns flowOf(settings)
        coEvery { settingsDao.getSettingsSync("u1") } returns settings
        coEvery { settingsDao.upsertSettings(any()) } just Runs
        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadSettings()
            expectState(SettingsUiState.Success(settings = settings))
            containerHost.toggleGuideLines()
        }
        val slot = slot<SettingsEntity>()
        coVerify(atLeast = 1) { settingsDao.upsertSettings(capture(slot)) }
        assertEquals(1, slot.captured.showGuideLines)
    }

    @Test
    fun `toggleBacklinkBadge flipsOneToZero`() = runTest {
        val settings = fakeSettings(showBacklinkBadge = 1)
        every { settingsDao.getSettings("u1") } returns flowOf(settings)
        coEvery { settingsDao.getSettingsSync("u1") } returns settings
        coEvery { settingsDao.upsertSettings(any()) } just Runs
        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadSettings()
            expectState(SettingsUiState.Success(settings = settings))
            containerHost.toggleBacklinkBadge()
        }
        val slot = slot<SettingsEntity>()
        coVerify(atLeast = 1) { settingsDao.upsertSettings(capture(slot)) }
        assertEquals(0, slot.captured.showBacklinkBadge)
    }

    @Test
    fun `allMutations stampHlcOnUpdatedField`() = runTest {
        val settings = fakeSettings()
        every { settingsDao.getSettings("u1") } returns flowOf(settings)
        coEvery { settingsDao.getSettingsSync("u1") } returns settings
        val captured = mutableListOf<SettingsEntity>()
        coEvery { settingsDao.upsertSettings(capture(captured)) } just Runs
        val viewModel = createViewModel()
        viewModel.test(this) {
            containerHost.loadSettings()
            expectState(SettingsUiState.Success(settings = settings))
            containerHost.updateTheme("light")
            containerHost.updateDensity("compact")
            containerHost.toggleGuideLines()
            containerHost.toggleBacklinkBadge()
        }
        // We expect 4 upsert calls from mutations
        assertTrue("Expected at least 4 upsert calls, got ${captured.size}", captured.size >= 4)
        // Check each mutation stamped HLC on its field
        val themeUpdate = captured.find { it.theme == "light" }!!
        assertTrue(themeUpdate.themeHlc.matches(Regex("^[0-9a-f]{16}-[0-9a-f]{4}-.*")))
        val densityUpdate = captured.find { it.density == "compact" }!!
        assertTrue(densityUpdate.densityHlc.matches(Regex("^[0-9a-f]{16}-[0-9a-f]{4}-.*")))
        val guidelinesUpdate = captured.find { it.showGuideLines == 0 }!!
        assertTrue(guidelinesUpdate.showGuideLinesHlc.matches(Regex("^[0-9a-f]{16}-[0-9a-f]{4}-.*")))
        val backlinkUpdate = captured.find { it.showBacklinkBadge == 0 }!!
        assertTrue(backlinkUpdate.showBacklinkBadgeHlc.matches(Regex("^[0-9a-f]{16}-[0-9a-f]{4}-.*")))
    }
}

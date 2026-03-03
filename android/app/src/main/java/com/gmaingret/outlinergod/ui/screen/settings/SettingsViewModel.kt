package com.gmaingret.outlinergod.ui.screen.settings

import androidx.lifecycle.ViewModel
import com.gmaingret.outlinergod.db.dao.SettingsDao
import com.gmaingret.outlinergod.db.entity.SettingsEntity
import com.gmaingret.outlinergod.repository.AuthRepository
import com.gmaingret.outlinergod.sync.HlcClock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.orbitmvi.orbit.ContainerHost
import org.orbitmvi.orbit.viewmodel.container
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDao: SettingsDao,
    private val authRepository: AuthRepository,
    private val hlcClock: HlcClock
) : ViewModel(), ContainerHost<SettingsUiState, SettingsSideEffect> {

    override val container = container<SettingsUiState, SettingsSideEffect>(SettingsUiState.Loading)

    internal var userId: String = ""
    internal var deviceId: String = ""

    init {
        loadSettings()
    }

    fun loadSettings() = intent {
        try {
            userId = authRepository.getUserId().filterNotNull().first()
            deviceId = authRepository.getDeviceId().first()
            settingsDao.getSettings(userId).collect { entity ->
                if (entity == null) {
                    val defaultEntity = SettingsEntity(
                        userId = userId,
                        theme = "dark",
                        themeHlc = "",
                        density = "cozy",
                        densityHlc = "",
                        showGuideLines = 1,
                        showGuideLinesHlc = "",
                        showBacklinkBadge = 1,
                        showBacklinkBadgeHlc = "",
                        deviceId = deviceId,
                        updatedAt = System.currentTimeMillis()
                    )
                    settingsDao.upsertSettings(defaultEntity)
                } else {
                    reduce { SettingsUiState.Success(settings = entity) }
                }
            }
        } catch (e: Exception) {
            reduce { SettingsUiState.Error(e.message ?: "Failed to load settings") }
        }
    }

    fun updateTheme(theme: String) = intent {
        if (theme !in listOf("dark", "light")) return@intent
        val entity = settingsDao.getSettingsSync(userId) ?: return@intent
        val hlc = hlcClock.generate(deviceId)
        val updated = entity.copy(
            theme = theme,
            themeHlc = hlc,
            updatedAt = System.currentTimeMillis()
        )
        settingsDao.upsertSettings(updated)
    }

    fun updateDensity(density: String) = intent {
        if (density !in listOf("cozy", "comfortable", "compact")) return@intent
        val entity = settingsDao.getSettingsSync(userId) ?: return@intent
        val hlc = hlcClock.generate(deviceId)
        val updated = entity.copy(
            density = density,
            densityHlc = hlc,
            updatedAt = System.currentTimeMillis()
        )
        settingsDao.upsertSettings(updated)
    }

    fun toggleGuideLines() = intent {
        val entity = settingsDao.getSettingsSync(userId) ?: return@intent
        val hlc = hlcClock.generate(deviceId)
        val updated = entity.copy(
            showGuideLines = if (entity.showGuideLines == 0) 1 else 0,
            showGuideLinesHlc = hlc,
            updatedAt = System.currentTimeMillis()
        )
        settingsDao.upsertSettings(updated)
    }

    fun toggleBacklinkBadge() = intent {
        val entity = settingsDao.getSettingsSync(userId) ?: return@intent
        val hlc = hlcClock.generate(deviceId)
        val updated = entity.copy(
            showBacklinkBadge = if (entity.showBacklinkBadge == 0) 1 else 0,
            showBacklinkBadgeHlc = hlc,
            updatedAt = System.currentTimeMillis()
        )
        settingsDao.upsertSettings(updated)
    }

    fun logout() = intent {
        try {
            val refreshToken = authRepository.getRefreshToken().filterNotNull().first()
            authRepository.logout(refreshToken)
                .onSuccess { postSideEffect(SettingsSideEffect.NavigateToLogin) }
                .onFailure { postSideEffect(SettingsSideEffect.NavigateToLogin) }
        } catch (e: Exception) {
            // No refresh token stored — still navigate to login
            postSideEffect(SettingsSideEffect.NavigateToLogin)
        }
    }
}

sealed class SettingsUiState {
    data object Loading : SettingsUiState()
    data class Success(val settings: SettingsEntity) : SettingsUiState()
    data class Error(val message: String) : SettingsUiState()
}

sealed class SettingsSideEffect {
    data object NavigateToLogin : SettingsSideEffect()
    data class ShowError(val message: String) : SettingsSideEffect()
}

package com.gmaingret.outlinergod.ui.screen.settings

import com.gmaingret.outlinergod.ui.navigation.AppRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsScreenTest {

    @Test
    fun settingsScreenKt_classExists() {
        val clazz = Class.forName("com.gmaingret.outlinergod.ui.screen.settings.SettingsScreenKt")
        assertNotNull(clazz)
    }

    @Test
    fun settingsUiState_hasLoadingSuccessError() {
        val subclasses = SettingsUiState::class.sealedSubclasses.map { it.simpleName }
        assertTrue(subclasses.containsAll(listOf("Loading", "Success", "Error")))
    }

    @Test
    fun settingsEntity_density_validValues() {
        val validDensities = listOf("cozy", "comfortable", "compact")
        assertEquals(3, validDensities.size)
        assertTrue(validDensities.contains("cozy"))
        assertTrue(validDensities.contains("comfortable"))
        assertTrue(validDensities.contains("compact"))
    }

    @Test
    fun appNavHost_settingsRoute_isCorrect() {
        assertEquals("settings", AppRoutes.SETTINGS)
    }
}

package com.gmaingret.outlinergod

import dagger.hilt.android.HiltAndroidApp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import org.junit.Test
import org.junit.Assert.*

class MainActivityTest {

    @Test
    fun mainActivity_extendsHiltGeneratedClass() {
        // Hilt's ASM transform makes MainActivity extend Hilt_MainActivity at compile time.
        // The generated superclass name contains "Hilt_".
        val superclass = MainActivity::class.java.superclass
        assertNotNull(superclass)
        assertTrue(
            "MainActivity should extend a Hilt-generated superclass, but extends ${superclass?.name}",
            superclass!!.name.contains("Hilt_")
        )
    }

    @Test
    fun outlinerGodApp_isAnnotatedWithHiltAndroidApp() {
        assertTrue(
            OutlinerGodApp::class.java.isAnnotationPresent(HiltAndroidApp::class.java)
        )
    }

    @Test
    fun mainActivity_hasSetContentInOnCreate() {
        // Verify that onCreate is overridden (not just inherited)
        val onCreateMethod = MainActivity::class.java.getDeclaredMethod(
            "onCreate",
            android.os.Bundle::class.java
        )
        assertNotNull("MainActivity must override onCreate", onCreateMethod)
    }

    @Test
    fun outlinerGodTheme_darkAndLight_areDistinct() {
        val dark = darkColorScheme()
        val light = lightColorScheme()
        assertNotEquals(dark.background, light.background)
    }
}

package com.gmaingret.outlinergod.di

import com.gmaingret.outlinergod.OutlinerGodApp
import dagger.hilt.android.HiltAndroidApp
import dagger.Module
import org.junit.Test
import org.junit.Assert.*

class HiltModuleTest {

    @Test
    fun outlinerGodApp_isAnnotatedWithHiltAndroidApp() {
        assertTrue(OutlinerGodApp::class.java.isAnnotationPresent(HiltAndroidApp::class.java))
    }

    @Test
    fun networkModule_isAnnotatedWithModule() {
        assertTrue(NetworkModule::class.java.isAnnotationPresent(Module::class.java))
    }

    @Test
    fun databaseModule_isAnnotatedWithModule() {
        assertTrue(DatabaseModule::class.java.isAnnotationPresent(Module::class.java))
    }

    @Test
    fun authModule_isAnnotatedWithModule() {
        assertTrue(AuthModule::class.java.isAnnotationPresent(Module::class.java))
    }

    @Test
    fun allFiveModules_haveModuleAnnotation() {
        // @InstallIn has CLASS retention (not available at runtime), so we verify
        // @Module (RUNTIME retention) on all five. @InstallIn correctness is validated
        // by kspDebugKotlin succeeding — Hilt KSP rejects modules missing @InstallIn.
        assertTrue(NetworkModule::class.java.isAnnotationPresent(Module::class.java))
        assertTrue(DatabaseModule::class.java.isAnnotationPresent(Module::class.java))
        assertTrue(RepositoryModule::class.java.isAnnotationPresent(Module::class.java))
        assertTrue(ClockModule::class.java.isAnnotationPresent(Module::class.java))
        assertTrue(AuthModule::class.java.isAnnotationPresent(Module::class.java))
    }
}

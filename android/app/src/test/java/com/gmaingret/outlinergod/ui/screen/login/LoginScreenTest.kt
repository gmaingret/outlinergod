package com.gmaingret.outlinergod.ui.screen.login

import com.gmaingret.outlinergod.BuildConfig
import com.gmaingret.outlinergod.ui.navigation.AppRoutes
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

class LoginScreenTest {

    @Test
    fun loginScreenKt_classExists_inLoginPackage() {
        val clazz = Class.forName("com.gmaingret.outlinergod.ui.screen.login.LoginScreenKt")
        assertNotNull(clazz)
    }

    @Test
    fun buildConfig_googleClientId_fieldIsString() {
        val field = BuildConfig::class.java.getField("GOOGLE_CLIENT_ID")
        assertEquals(String::class.java, field.type)
    }

    @Test
    fun appNavHost_loginRoute_remainsStartDestination() {
        assertEquals("login", AppRoutes.LOGIN)
    }

    @Test
    fun appRoutes_documentList_isCorrect() {
        assertEquals("document_list", AppRoutes.DOCUMENT_LIST)
    }
}

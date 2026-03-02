package com.gmaingret.outlinergod.ui.screen.documentlist

import com.gmaingret.outlinergod.ui.navigation.AppRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentListScreenTest {

    @Test
    fun documentListScreenKt_classExists() {
        val clazz = Class.forName("com.gmaingret.outlinergod.ui.screen.documentlist.DocumentListScreenKt")
        assertNotNull(clazz)
    }

    @Test
    fun appRoutes_nodeEditor_containsDocumentIdParam() {
        assertTrue(
            "NODE_EDITOR route must contain {documentId}",
            AppRoutes.NODE_EDITOR.contains("{documentId}")
        )
    }

    @Test
    fun appRoutes_settings_isDefined() {
        assertEquals("settings", AppRoutes.SETTINGS)
    }

    @Test
    fun syncStatus_enum_hasThreeValues() {
        assertEquals(3, SyncStatus.entries.size)
    }
}

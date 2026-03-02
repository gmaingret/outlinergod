package com.gmaingret.outlinergod.ui.navigation

import org.junit.Test
import org.junit.Assert.*

class AppRoutesTest {

    @Test
    fun login_route_isCorrectString() {
        assertEquals("login", AppRoutes.LOGIN)
    }

    @Test
    fun documentList_route_isCorrectString() {
        assertEquals("document_list", AppRoutes.DOCUMENT_LIST)
    }

    @Test
    fun documentDetail_route_containsDocumentIdParam() {
        assertTrue(AppRoutes.DOCUMENT_DETAIL.contains("{documentId}"))
    }

    @Test
    fun nodeEditor_route_containsDocumentIdParam() {
        assertTrue(AppRoutes.NODE_EDITOR.contains("{documentId}"))
    }

    @Test
    fun allSixRoutes_areDefined_andNonBlank() {
        val routes = listOf(
            AppRoutes.LOGIN,
            AppRoutes.DOCUMENT_LIST,
            AppRoutes.DOCUMENT_DETAIL,
            AppRoutes.NODE_EDITOR,
            AppRoutes.SETTINGS,
            AppRoutes.BOOKMARKS
        )
        assertEquals(6, routes.size)
        assertTrue(routes.all { it.isNotBlank() })
    }

    @Test
    fun noRouteDuplicated() {
        val routes = setOf(
            AppRoutes.LOGIN,
            AppRoutes.DOCUMENT_LIST,
            AppRoutes.DOCUMENT_DETAIL,
            AppRoutes.NODE_EDITOR,
            AppRoutes.SETTINGS,
            AppRoutes.BOOKMARKS
        )
        assertEquals(6, routes.size)
    }
}

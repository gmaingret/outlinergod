package com.gmaingret.outlinergod.ui.screen.documentlist

import com.gmaingret.outlinergod.db.entity.DocumentEntity
import com.gmaingret.outlinergod.ui.common.SyncStatus
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

    // ── generateNextSortOrder tests ────────────────────────────────────────

    private fun fakeDocument(sortOrder: String = "aV") = DocumentEntity(
        id = java.util.UUID.randomUUID().toString(),
        userId = "u",
        title = "Test",
        type = "document",
        sortOrder = sortOrder,
        createdAt = 1000L,
        updatedAt = 1000L
    )

    @Test
    fun generateNextSortOrder_emptyList_returnsInitialKey() {
        // FractionalIndex.generateKeyBetween(null, null) returns "aV" (midpoint of 62-char alphabet)
        val result = generateNextSortOrder(emptyList())
        assertEquals("aV", result)
    }

    @Test
    fun generateNextSortOrder_singleItem_returnsKeyAfterIt() {
        val items = listOf(fakeDocument(sortOrder = "aV"))
        val result = generateNextSortOrder(items)
        assertTrue(
            "Expected result '$result' > 'aV'",
            result > "aV"
        )
    }

    @Test
    fun generateNextSortOrder_multipleItems_returnsKeyAfterMax() {
        val items = listOf(
            fakeDocument(sortOrder = "aV"),
            fakeDocument(sortOrder = "aVV")
        )
        val result = generateNextSortOrder(items)
        assertTrue(
            "Expected result '$result' > 'aVV'",
            result > "aVV"
        )
    }

    @Test
    fun generateNextSortOrder_sequentialCreation_maintainsSortOrder() {
        val orders = mutableListOf<String>()
        val items = mutableListOf<DocumentEntity>()
        repeat(5) {
            val nextOrder = generateNextSortOrder(items)
            orders.add(nextOrder)
            items.add(fakeDocument(sortOrder = nextOrder))
        }
        assertEquals(
            "Sequential sort orders should already be sorted",
            orders.sorted(),
            orders
        )
    }
}

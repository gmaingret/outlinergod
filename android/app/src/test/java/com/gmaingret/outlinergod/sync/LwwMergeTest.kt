package com.gmaingret.outlinergod.sync

import com.gmaingret.outlinergod.db.entity.BookmarkEntity
import com.gmaingret.outlinergod.db.entity.DocumentEntity
import com.gmaingret.outlinergod.db.entity.NodeEntity
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * P3-10 LWW merge tests for NodeEntity, DocumentEntity, BookmarkEntity.
 * Tests 3 & 4 are property-based (commutativity/idempotency).
 *
 * NOTE: All property-based tests use block-body syntax (not expression body) so
 * that JUnit4 sees a proper void return type in bytecode.
 */
class LwwMergeTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun makeNode(
        id: String = "n1",
        content: String = "content",
        contentHlc: String = "AAAA",
        note: String = "",
        noteHlc: String = "AAAA",
        parentId: String? = null,
        parentIdHlc: String = "AAAA",
        sortOrder: String = "a0",
        sortOrderHlc: String = "AAAA",
        completed: Int = 0,
        completedHlc: String = "AAAA",
        color: Int = 0,
        colorHlc: String = "AAAA",
        collapsed: Int = 0,
        collapsedHlc: String = "AAAA",
        deletedAt: Long? = null,
        deletedHlc: String = "AAAA",
        deviceId: String = "dev-a",
    ) = NodeEntity(
        id = id, documentId = "d1", userId = "u1",
        content = content, contentHlc = contentHlc,
        note = note, noteHlc = noteHlc,
        parentId = parentId, parentIdHlc = parentIdHlc,
        sortOrder = sortOrder, sortOrderHlc = sortOrderHlc,
        completed = completed, completedHlc = completedHlc,
        color = color, colorHlc = colorHlc,
        collapsed = collapsed, collapsedHlc = collapsedHlc,
        deletedAt = deletedAt, deletedHlc = deletedHlc,
        deviceId = deviceId,
        createdAt = 0L, updatedAt = 0L,
    )

    // -----------------------------------------------------------------------
    // Test 1: higher content HLC wins
    // -----------------------------------------------------------------------

    @Test
    fun nodeMerge_higherContentHlcWins() {
        val local = makeNode(content = "local-content", contentHlc = "AAAA")
        val incoming = makeNode(content = "incoming-content", contentHlc = "ZZZZ")
        val merged = mergeNode(local, incoming)
        assertEquals("incoming-content", merged.content)
    }

    // -----------------------------------------------------------------------
    // Test 2: local wins on equal HLC (via value tiebreaker — max string wins)
    // -----------------------------------------------------------------------

    @Test
    fun nodeMerge_equalHlc_deterministicTiebreaker() {
        val local = makeNode(content = "local-content", contentHlc = "AAAA")
        val incoming = makeNode(content = "incoming-content", contentHlc = "AAAA")
        val merged = mergeNode(local, incoming)
        // "local-content" > "incoming-content" lexicographically, so local wins
        assertEquals("local-content", merged.content)
    }

    // -----------------------------------------------------------------------
    // Test 3: idempotency — merge(a, merge(a, b)) == merge(a, b)
    // Property-based: holds for arbitrary HLC strings
    // -----------------------------------------------------------------------

    @Test
    fun nodeMerge_isIdempotent() {
        runBlocking {
            checkAll(500, Arb.string(0..8), Arb.string(0..8)) { hlc1, hlc2 ->
                val a = makeNode(contentHlc = hlc1, sortOrderHlc = hlc2)
                val b = makeNode(contentHlc = hlc2, sortOrderHlc = hlc1)
                val once = mergeNode(a, b)
                val twice = mergeNode(a, mergeNode(a, b))
                assertEquals(once.content, twice.content)
                assertEquals(once.sortOrder, twice.sortOrder)
                assertEquals(once.deletedAt, twice.deletedAt)
            }
        }
    }

    // -----------------------------------------------------------------------
    // Test 4: commutativity — merge(a, b) == merge(b, a) per field
    // Property-based: holds for arbitrary HLC strings
    // -----------------------------------------------------------------------

    @Test
    fun nodeMerge_isCommutative_perField() {
        runBlocking {
            checkAll(500, Arb.string(0..8), Arb.string(0..8)) { hlc1, hlc2 ->
                val a = makeNode(contentHlc = hlc1, sortOrderHlc = hlc2)
                val b = makeNode(contentHlc = hlc2, sortOrderHlc = hlc1)
                val ab = mergeNode(a, b)
                val ba = mergeNode(b, a)
                assertEquals(ab.content, ba.content)
                assertEquals(ab.note, ba.note)
                assertEquals(ab.sortOrder, ba.sortOrder)
            }
        }
    }

    // -----------------------------------------------------------------------
    // Test 5: deletion wins when deletedHlc is highest
    // -----------------------------------------------------------------------

    @Test
    fun nodeMerge_deleteWins_whenDeletedHlcIsHighest() {
        val local = makeNode(deletedAt = null, deletedHlc = "AAAA")
        val incoming = makeNode(deletedAt = 1000L, deletedHlc = "ZZZZ")
        val merged = mergeNode(local, incoming)
        assertNotNull(merged.deletedAt)
    }

    // -----------------------------------------------------------------------
    // Test 6: content field independent of deletion
    // -----------------------------------------------------------------------

    @Test
    fun nodeMerge_contentFieldIndependent_ofDeletion() {
        val local = makeNode(content = "my-content", contentHlc = "ZZZZ", deletedAt = null, deletedHlc = "AAAA")
        val incoming = makeNode(content = "other-content", contentHlc = "AAAA", deletedAt = 1000L, deletedHlc = "ZZZZZ")
        val merged = mergeNode(local, incoming)
        // incoming wins deletion (higher deletedHlc)
        assertNotNull(merged.deletedAt)
        // local wins content (higher contentHlc)
        assertEquals("my-content", merged.content)
    }

    // -----------------------------------------------------------------------
    // Test 7: document merge — title field
    // -----------------------------------------------------------------------

    @Test
    fun documentMerge_titleField_higherHlcWins() {
        val local = DocumentEntity(
            id = "d1", userId = "u1", title = "local-title", titleHlc = "AAAA",
            sortOrder = "a0", createdAt = 0L, updatedAt = 0L,
        )
        val incoming = DocumentEntity(
            id = "d1", userId = "u1", title = "incoming-title", titleHlc = "ZZZZ",
            sortOrder = "a0", createdAt = 0L, updatedAt = 0L,
        )
        val merged = mergeDocument(local, incoming)
        assertEquals("incoming-title", merged.title)
    }

    // -----------------------------------------------------------------------
    // Test 8: bookmark merge — sortOrder field
    // -----------------------------------------------------------------------

    @Test
    fun bookmarkMerge_sortOrderField_higherHlcWins() {
        val local = BookmarkEntity(
            id = "b1", userId = "u1", sortOrder = "a0", sortOrderHlc = "AAAA",
            createdAt = 0L, updatedAt = 0L,
        )
        val incoming = BookmarkEntity(
            id = "b1", userId = "u1", sortOrder = "b0", sortOrderHlc = "ZZZZ",
            createdAt = 0L, updatedAt = 0L,
        )
        val merged = mergeBookmark(local, incoming)
        assertEquals("b0", merged.sortOrder)
    }

    // -----------------------------------------------------------------------
    // Test 9: immutable fields always from local
    // -----------------------------------------------------------------------

    @Test
    fun nodeMerge_immutableFields_alwaysFromLocal() {
        val local = makeNode(id = "local-id").copy(userId = "local-user", documentId = "local-doc")
        val incoming = makeNode(id = "incoming-id").copy(
            userId = "incoming-user", documentId = "incoming-doc",
            contentHlc = "ZZZZ", noteHlc = "ZZZZ", sortOrderHlc = "ZZZZ",
            deletedHlc = "ZZZZ", completedHlc = "ZZZZ", colorHlc = "ZZZZ",
            collapsedHlc = "ZZZZ", parentIdHlc = "ZZZZ",
        )
        val merged = mergeNode(local, incoming)
        assertEquals("local-id", merged.id)
        assertEquals("local-user", merged.userId)
        assertEquals("local-doc", merged.documentId)
    }
}

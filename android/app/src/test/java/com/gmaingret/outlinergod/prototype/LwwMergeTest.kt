package com.gmaingret.outlinergod.prototype

import io.kotest.property.Arb
import io.kotest.property.checkAll
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.string
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * P0-3 Android LWW merge tests — 6 cases required by PLAN_PHASE0.md.
 * Tests 4 and 5 use Kotest property-based testing for commutativity/idempotency.
 *
 * NOTE: All property-based tests use block-body syntax (not expression body) so
 * that JUnit4 sees a proper void return type in bytecode. Using `= runBlocking {}`
 * expression body would return kotlin.Unit (Object) instead of void, causing
 * InvalidTestClassError.
 */
class LwwMergeTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun makeNode(
        id: String = "node-1",
        content: String = "hello",
        contentHlc: String = "",
        note: String = "",
        noteHlc: String = "",
        sortOrder: String = "a0",
        sortOrderHlc: String = "",
        deletedAt: Long? = null,
        deletedHlc: String = "",
        deviceId: String = "device-a",
    ) = NodeSyncRecord(
        id = id,
        documentId = "doc-1",
        content = content,
        contentHlc = contentHlc,
        note = note,
        noteHlc = noteHlc,
        parentId = null,
        parentIdHlc = "",
        sortOrder = sortOrder,
        sortOrderHlc = sortOrderHlc,
        completed = 0,
        completedHlc = "",
        color = 0,
        colorHlc = "",
        collapsed = 0,
        collapsedHlc = "",
        deletedAt = deletedAt,
        deletedHlc = deletedHlc,
        deviceId = deviceId,
    )

    // -----------------------------------------------------------------------
    // Test 3: higher HLC wins per field
    // -----------------------------------------------------------------------

    @Test
    fun lwwMerge_higherHlcWins() {
        val lowHlc = "1636300202430-00000-device-a"
        val highHlc = "1636300202430-00001-device-b"

        val a = makeNode(content = "old content", contentHlc = lowHlc)
        val b = makeNode(content = "new content", contentHlc = highHlc)

        val merged = LwwMerge.mergeNodes(a, b)
        assertEquals("new content", merged.content)
        assertEquals(highHlc, merged.contentHlc)
    }

    // -----------------------------------------------------------------------
    // Test 4: idempotency — merge(a, merge(a, b)) == merge(a, b)
    // Property-based: holds for arbitrary HLC strings
    // -----------------------------------------------------------------------

    @Test
    fun lwwMerge_idempotent() {
        runBlocking {
            checkAll(
                Arb.string(), // contentHlcA
                Arb.string(), // contentHlcB
                Arb.string(), // noteHlcA
                Arb.string(), // noteHlcB
                Arb.string(), // deviceA
                Arb.string(), // deviceB
            ) { contentHlcA, contentHlcB, noteHlcA, noteHlcB, devA, devB ->
                val a = makeNode(
                    content = "A",
                    contentHlc = contentHlcA,
                    note = "noteA",
                    noteHlc = noteHlcA,
                    deviceId = devA,
                )
                val b = makeNode(
                    content = "B",
                    contentHlc = contentHlcB,
                    note = "noteB",
                    noteHlc = noteHlcB,
                    deviceId = devB,
                )
                val mergeAB = LwwMerge.mergeNodes(a, b)
                val mergeA_AB = LwwMerge.mergeNodes(a, mergeAB)

                // merge(a, merge(a, b)) == merge(a, b) for every field
                assertEquals(mergeAB.content, mergeA_AB.content)
                assertEquals(mergeAB.contentHlc, mergeA_AB.contentHlc)
                assertEquals(mergeAB.note, mergeA_AB.note)
                assertEquals(mergeAB.noteHlc, mergeA_AB.noteHlc)
                assertEquals(mergeAB.deletedAt, mergeA_AB.deletedAt)
                assertEquals(mergeAB.deletedHlc, mergeA_AB.deletedHlc)
                assertEquals(mergeAB.deviceId, mergeA_AB.deviceId)
            }
        }
    }

    // -----------------------------------------------------------------------
    // Test 5: commutativity — merge(a, b) == merge(b, a) for every field
    // Property-based: holds for arbitrary HLC strings
    // -----------------------------------------------------------------------

    @Test
    fun lwwMerge_commutative() {
        runBlocking {
            checkAll(
                Arb.string(), // contentHlcA
                Arb.string(), // contentHlcB
                Arb.string(), // noteHlcA
                Arb.string(), // noteHlcB
                Arb.long(0L..Long.MAX_VALUE).orNull(0.3),  // deletedAtA
                Arb.long(0L..Long.MAX_VALUE).orNull(0.3),  // deletedAtB
                Arb.string(), // deletedHlcA
                Arb.string(), // deletedHlcB
                Arb.string(), // deviceA
                Arb.string(), // deviceB
            ) { cHlcA, cHlcB, nHlcA, nHlcB, delAtA, delAtB, delHlcA, delHlcB, devA, devB ->
                val a = NodeSyncRecord(
                    id = "node-1",
                    documentId = "doc-1",
                    content = "A",
                    contentHlc = cHlcA,
                    note = "noteA",
                    noteHlc = nHlcA,
                    parentId = null,
                    parentIdHlc = "",
                    sortOrder = "a0",
                    sortOrderHlc = "",
                    completed = 0,
                    completedHlc = "",
                    color = 0,
                    colorHlc = "",
                    collapsed = 0,
                    collapsedHlc = "",
                    deletedAt = delAtA,
                    deletedHlc = delHlcA,
                    deviceId = devA,
                )
                val b = NodeSyncRecord(
                    id = "node-1",
                    documentId = "doc-1",
                    content = "B",
                    contentHlc = cHlcB,
                    note = "noteB",
                    noteHlc = nHlcB,
                    parentId = null,
                    parentIdHlc = "",
                    sortOrder = "a1",
                    sortOrderHlc = "",
                    completed = 0,
                    completedHlc = "",
                    color = 0,
                    colorHlc = "",
                    collapsed = 0,
                    collapsedHlc = "",
                    deletedAt = delAtB,
                    deletedHlc = delHlcB,
                    deviceId = devB,
                )
                val ab = LwwMerge.mergeNodes(a, b)
                val ba = LwwMerge.mergeNodes(b, a)

                // merge(a, b) == merge(b, a) for every field
                assertEquals(ab.content, ba.content)
                assertEquals(ab.contentHlc, ba.contentHlc)
                assertEquals(ab.note, ba.note)
                assertEquals(ab.noteHlc, ba.noteHlc)
                assertEquals(ab.deletedAt, ba.deletedAt)
                assertEquals(ab.deletedHlc, ba.deletedHlc)
                assertEquals(ab.deviceId, ba.deviceId)
            }
        }
    }

    // -----------------------------------------------------------------------
    // Test 6: deletion wins — deleted_hlc > all other field HLCs → deleted_at set
    // -----------------------------------------------------------------------

    @Test
    fun lwwMerge_deleteWins() {
        val editHlc = "1636300202430-00001-device-a"
        val deleteHlc = "ffffffffffffffff-ffff-device-b" // highest possible HLC

        // A edited the content; B deleted the node with a very high deleted_hlc
        val a = makeNode(
            content = "edited by A",
            contentHlc = editHlc,
            deletedAt = null,
            deletedHlc = editHlc, // A has not deleted — lower deleted_hlc
            deviceId = "device-a",
        )
        val b = makeNode(
            content = "original",
            contentHlc = "1636300202430-00000-device-b", // B has older content
            deletedAt = System.currentTimeMillis(),
            deletedHlc = deleteHlc, // B deleted with highest possible HLC
            deviceId = "device-b",
        )

        val merged = LwwMerge.mergeNodes(a, b)

        // deletedHlc of B wins → deletedAt must be set
        assertNotNull("Deletion must win over concurrent edits", merged.deletedAt)
        assertEquals(deleteHlc, merged.deletedHlc)
        // A's content edit still wins for the content field (contentHlc A > B)
        assertEquals("edited by A", merged.content)
    }
}

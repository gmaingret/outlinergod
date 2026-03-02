package com.gmaingret.outlinergod.ui.mapper

import com.gmaingret.outlinergod.db.entity.NodeEntity
import io.kotest.core.spec.style.StringSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.runner.RunWith
import io.kotest.runner.junit4.KotestTestRunner

@RunWith(KotestTestRunner::class)
class FlatNodeMapperTest : StringSpec({

    fun makeNode(
        id: String,
        parentId: String?,
        sortOrder: String,
        collapsed: Int = 0,
        deletedAt: Long? = null,
    ): NodeEntity = NodeEntity(
        id = id,
        documentId = "doc-1",
        userId = "user-1",
        content = "content-$id",
        contentHlc = "",
        note = "",
        noteHlc = "",
        parentId = parentId,
        parentIdHlc = "",
        sortOrder = sortOrder,
        sortOrderHlc = "",
        completed = 0,
        completedHlc = "",
        color = 0,
        colorHlc = "",
        collapsed = collapsed,
        collapsedHlc = "",
        deletedAt = deletedAt,
        deletedHlc = "",
        deviceId = "",
        createdAt = 1000L,
        updatedAt = 1000L,
        syncStatus = 0,
    )

    "emptyList_returnsEmptyFlatList" {
        val result = mapToFlatList(emptyList(), "doc-1")
        assertTrue(result.isEmpty())
    }

    "singleTopLevelNode_depthZero" {
        val node = makeNode(id = "n1", parentId = "doc-1", sortOrder = "a0")
        val result = mapToFlatList(listOf(node), "doc-1")
        assertEquals(1, result.size)
        assertEquals(0, result[0].depth)
        assertEquals("n1", result[0].entity.id)
    }

    "nestedNode_depthOne" {
        val parent = makeNode(id = "n1", parentId = "doc-1", sortOrder = "a0")
        val child = makeNode(id = "n2", parentId = "n1", sortOrder = "a0")
        val result = mapToFlatList(listOf(parent, child), "doc-1")
        assertEquals(2, result.size)
        assertEquals(0, result[0].depth)
        assertEquals("n1", result[0].entity.id)
        assertEquals(1, result[1].depth)
        assertEquals("n2", result[1].entity.id)
    }

    "collapsedNode_childrenExcluded" {
        val parent = makeNode(id = "n1", parentId = "doc-1", sortOrder = "a0", collapsed = 1)
        val child = makeNode(id = "n2", parentId = "n1", sortOrder = "a0")
        val result = mapToFlatList(listOf(parent, child), "doc-1")
        assertEquals(1, result.size)
        assertEquals("n1", result[0].entity.id)
    }

    "expandedNode_childrenIncluded" {
        val parent = makeNode(id = "n1", parentId = "doc-1", sortOrder = "a0", collapsed = 0)
        val child = makeNode(id = "n2", parentId = "n1", sortOrder = "a0")
        val result = mapToFlatList(listOf(parent, child), "doc-1")
        assertEquals(2, result.size)
        assertEquals("n1", result[0].entity.id)
        assertEquals("n2", result[1].entity.id)
    }

    "hasChildren_true_whenNodeHasActiveChildren" {
        val parent = makeNode(id = "n1", parentId = "doc-1", sortOrder = "a0")
        val activeChild = makeNode(id = "n2", parentId = "n1", sortOrder = "a0")
        val deletedChild = makeNode(id = "n3", parentId = "n1", sortOrder = "a1", deletedAt = 1000L)
        val result = mapToFlatList(listOf(parent, activeChild, deletedChild), "doc-1")
        assertTrue(result.first { it.entity.id == "n1" }.hasChildren)
    }

    "hasChildren_false_afterAllChildrenDeleted" {
        val parent = makeNode(id = "n1", parentId = "doc-1", sortOrder = "a0")
        val deletedChild = makeNode(id = "n2", parentId = "n1", sortOrder = "a0", deletedAt = 1000L)
        val result = mapToFlatList(listOf(parent, deletedChild), "doc-1")
        assertFalse(result.first { it.entity.id == "n1" }.hasChildren)
    }

    "sortOrder_lexicographic_notNumeric" {
        // ASCII order: uppercase < lowercase, so "V" (86) < "Z" (90) < "a" (97)
        val n1 = makeNode(id = "n1", parentId = "doc-1", sortOrder = "a")
        val n2 = makeNode(id = "n2", parentId = "doc-1", sortOrder = "V")
        val n3 = makeNode(id = "n3", parentId = "doc-1", sortOrder = "Z")
        val result = mapToFlatList(listOf(n1, n2, n3), "doc-1")
        assertEquals(3, result.size)
        assertEquals("n2", result[0].entity.id) // "V"
        assertEquals("n3", result[1].entity.id) // "Z"
        assertEquals("n1", result[2].entity.id) // "a"
    }

    "orphanNode_appendedAtDepthZero" {
        val root = makeNode(id = "n1", parentId = "doc-1", sortOrder = "a0")
        val orphan = makeNode(id = "n2", parentId = "ghost-uuid", sortOrder = "a0")
        val result = mapToFlatList(listOf(root, orphan), "doc-1")
        assertEquals(2, result.size)
        assertEquals("n1", result[0].entity.id)
        assertEquals("n2", result[1].entity.id)
        assertEquals(0, result[1].depth)
    }

    "deletedNode_excludedFromResult" {
        val active = makeNode(id = "n1", parentId = "doc-1", sortOrder = "a0")
        val deleted = makeNode(id = "n2", parentId = "doc-1", sortOrder = "a1", deletedAt = 1000L)
        val result = mapToFlatList(listOf(active, deleted), "doc-1")
        assertEquals(1, result.size)
        assertEquals("n1", result[0].entity.id)
    }

    "deepNesting_computesDepthCorrectly (property-based)" {
        checkAll(200, Arb.int(1..10)) { depth ->
            // Build a chain of `depth` nodes: n0 -> n1 -> n2 -> ...
            val nodes = (0 until depth).map { i ->
                val parentId = if (i == 0) "doc-1" else "n${i - 1}"
                makeNode(id = "n$i", parentId = parentId, sortOrder = "a0")
            }
            val result = mapToFlatList(nodes, "doc-1")
            assertEquals(depth, result.size)
            assertEquals(depth - 1, result.last().depth)
        }
    }

    "multipleRoots_allAtDepthZero" {
        val n1 = makeNode(id = "n1", parentId = "doc-1", sortOrder = "a0")
        val n2 = makeNode(id = "n2", parentId = "doc-1", sortOrder = "a1")
        val n3 = makeNode(id = "n3", parentId = "doc-1", sortOrder = "a2")
        val result = mapToFlatList(listOf(n1, n2, n3), "doc-1")
        assertEquals(3, result.size)
        assertTrue(result.all { it.depth == 0 })
    }
})

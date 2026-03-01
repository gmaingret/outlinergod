package com.outlinegod.prototype

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for DndPrototypeViewModel companion-object logic.
 * All tests run on the JVM — no Android context required.
 */
class DndPrototypeViewModelTest {

    // ── Helper: verify the sort-order invariant ───────────────────────────────

    private fun assertSortOrderInvariant(nodes: List<FlatNode>) {
        val groups = nodes.groupBy { it.parentId }
        for ((parentId, siblings) in groups) {
            val orders = siblings.map { it.sortOrder }
            for (i in 1 until orders.size) {
                assertTrue(
                    "Sort orders must be strictly ascending within parent=$parentId " +
                        "but got ${orders[i - 1]} >= ${orders[i]}",
                    orders[i - 1] < orders[i],
                )
            }
        }
    }

    // ── Test 1: verticalReorder_updatesListOrder ──────────────────────────────

    @Test
    fun verticalReorder_updatesListOrder() {
        // Three siblings at root level.  Drag B (index 1) above A (index 0).
        val nodes = listOf(
            FlatNode("A", "Node A", 0, null, "aM"),
            FlatNode("B", "Node B", 0, null, "aS"),
            FlatNode("C", "Node C", 0, null, "aY"),
        )

        val result = DndPrototypeViewModel.reorder(nodes, fromIndex = 1, toIndex = 0)

        // Order is [B, A, C]
        assertEquals("B", result[0].id)
        assertEquals("A", result[1].id)
        assertEquals("C", result[2].id)

        // B's sort_order must be strictly less than A's
        assertTrue(
            "B.sortOrder (${result[0].sortOrder}) must be < A.sortOrder (${result[1].sortOrder})",
            result[0].sortOrder < result[1].sortOrder,
        )
    }

    // ── Test 2: horizontalDragRight_indentsNode ───────────────────────────────

    @Test
    fun horizontalDragRight_indentsNode() {
        // A and B are siblings at depth 0.  Indenting B makes A its parent.
        val nodes = listOf(
            FlatNode("A", "Node A", 0, null, "aM"),
            FlatNode("B", "Node B", 0, null, "aS"),
        )

        val result = DndPrototypeViewModel.indent(nodes, nodeIndex = 1)

        val b = result.first { it.id == "B" }
        assertEquals("B depth should become 1", 1, b.depth)
        assertEquals("B parentId should be A", "A", b.parentId)
    }

    // ── Test 3: horizontalDragLeft_outdentsNode ───────────────────────────────

    @Test
    fun horizontalDragLeft_outdentsNode() {
        // GrandParent → Parent → C (at depth 2).  Outdenting C raises it to depth 1.
        val nodes = listOf(
            FlatNode("gp", "GrandParent", 0, null, "aA"),
            FlatNode("p",  "Parent",      1, "gp", "aA"),
            FlatNode("c",  "NodeC",       2, "p",  "aA"),
        )

        val result = DndPrototypeViewModel.outdent(nodes, nodeIndex = 2)

        val c = result.first { it.id == "c" }
        assertEquals("C depth should become 1", 1, c.depth)
        assertEquals("C parentId should be the former grandparent", "gp", c.parentId)
    }

    // ── Test 4: sortOrder_lexicographicInvariant ──────────────────────────────

    @Test
    fun sortOrder_lexicographicInvariant() {
        val nodes = listOf(
            FlatNode("A", "Node A", 0, null, "aA"),
            FlatNode("B", "Node B", 0, null, "aM"),
            FlatNode("C", "Node C", 0, null, "aS"),
        )

        // Multiple reorders — invariant must hold after each one
        val r1 = DndPrototypeViewModel.reorder(nodes, fromIndex = 1, toIndex = 0)
        assertSortOrderInvariant(r1)

        val r2 = DndPrototypeViewModel.reorder(r1, fromIndex = 0, toIndex = 2)
        assertSortOrderInvariant(r2)

        // Also verify after an indent operation
        val r3 = DndPrototypeViewModel.indent(r2, nodeIndex = 1)
        assertSortOrderInvariant(r3)
    }

    // ── Test 5: noChildrenLost_afterReparent ──────────────────────────────────

    @Test
    fun noChildrenLost_afterReparent() {
        // "parent" has two children.  Indent "parent" under A — depth delta = +1.
        val nodes = listOf(
            FlatNode("A",       "Node A",   0, null,     "aA"),
            FlatNode("parent",  "Parent",   0, null,     "aM"),
            FlatNode("child1",  "Child 1",  1, "parent", "aA"),
            FlatNode("child2",  "Child 2",  1, "parent", "aM"),
        )
        val originalParentDepth = nodes.first { it.id == "parent" }.depth
        val originalChild1Depth = nodes.first { it.id == "child1" }.depth
        val originalChild2Depth = nodes.first { it.id == "child2" }.depth

        val result = DndPrototypeViewModel.indent(nodes, nodeIndex = 1)

        val newParent = result.first { it.id == "parent" }
        val depthDelta = newParent.depth - originalParentDepth

        // depth delta must be +1
        assertEquals("Depth delta must be +1", 1, depthDelta)

        // Children must still be present
        val child1 = result.firstOrNull { it.id == "child1" }
        val child2 = result.firstOrNull { it.id == "child2" }
        assertNotNull("child1 must not be lost", child1)
        assertNotNull("child2 must not be lost", child2)

        // Children depths must shift by the same delta
        assertEquals(
            "child1 depth must increase by delta",
            originalChild1Depth + depthDelta, child1!!.depth,
        )
        assertEquals(
            "child2 depth must increase by delta",
            originalChild2Depth + depthDelta, child2!!.depth,
        )

        // Children's parentId must remain "parent"
        assertEquals("child1.parentId unchanged", "parent", child1.parentId)
        assertEquals("child2.parentId unchanged", "parent", child2.parentId)
    }
}

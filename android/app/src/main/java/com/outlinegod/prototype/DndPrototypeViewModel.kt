package com.outlinegod.prototype

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for the DnD prototype screen.
 *
 * All tree-manipulation logic lives in the companion object as pure functions so
 * they can be unit-tested on the JVM without an Android context.
 */
class DndPrototypeViewModel {

    private val _nodes = MutableStateFlow(initialNodes())
    val nodes: StateFlow<List<FlatNode>> = _nodes.asStateFlow()

    fun onReorder(fromIndex: Int, toIndex: Int) {
        _nodes.value = reorder(_nodes.value, fromIndex, toIndex)
    }

    /** Called when a node is dragged horizontally.
     *  [xOffsetDp] > 48  → indent one level.
     *  [xOffsetDp] < -48 → outdent one level.
     */
    fun onHorizontalDrag(nodeIndex: Int, xOffsetDp: Float) {
        val thresholdDp = 48f
        _nodes.value = when {
            xOffsetDp >= thresholdDp -> indent(_nodes.value, nodeIndex)
            xOffsetDp <= -thresholdDp -> outdent(_nodes.value, nodeIndex)
            else -> _nodes.value
        }
    }

    // ── Companion object: pure functions ─────────────────────────────────────

    companion object {

        /** Hardcoded demo tree used by the Activity. */
        fun initialNodes(): List<FlatNode> = listOf(
            FlatNode("n1", "Item 1",     0, null, "aA"),
            FlatNode("n2", "Item 2",     0, null, "aH"),
            FlatNode("n3", "  Item 2.1", 1, "n2", "aA"),
            FlatNode("n4", "  Item 2.2", 1, "n2", "aH"),
            FlatNode("n5", "Item 3",     0, null, "aP"),
            FlatNode("n6", "  Item 3.1", 1, "n5", "aA"),
            FlatNode("n7", "Item 4",     0, null, "aW"),
            FlatNode("n8", "  Item 4.1", 1, "n7", "aA"),
            FlatNode("n9", "  Item 4.2", 1, "n7", "aH"),
            FlatNode("na", "Item 5",     0, null, "ad"),
        )

        /**
         * Move the node (and its subtree) at [fromIndex] so it ends up at [toIndex].
         * [toIndex] is the desired final list position of the moved node.
         */
        fun reorder(nodes: List<FlatNode>, fromIndex: Int, toIndex: Int): List<FlatNode> {
            if (fromIndex == toIndex || fromIndex !in nodes.indices) return nodes
            val node = nodes[fromIndex]

            // Collect the block: the node plus all its descendants
            val blockEnd = (fromIndex + 1..nodes.lastIndex)
                .firstOrNull { nodes[it].depth <= node.depth } ?: (nodes.size)
            val blockSize = blockEnd - fromIndex
            val block = nodes.subList(fromIndex, blockEnd).toList()

            // Remove the block from the list
            val withoutBlock = nodes.toMutableList().also {
                it.subList(fromIndex, blockEnd).clear()
            }

            // Compute where to insert in the shortened list
            val insertAt = if (toIndex > fromIndex) {
                (toIndex - blockSize + 1).coerceIn(0, withoutBlock.size)
            } else {
                toIndex.coerceIn(0, withoutBlock.size)
            }
            withoutBlock.addAll(insertAt, block)

            return recomputeSortOrders(withoutBlock)
        }

        /**
         * Indent the node at [nodeIndex] by one level.
         * The node immediately before it in the list becomes its new parent.
         * No-op if the node is already a child of its predecessor.
         */
        fun indent(nodes: List<FlatNode>, nodeIndex: Int): List<FlatNode> {
            if (nodeIndex <= 0 || nodeIndex >= nodes.size) return nodes
            val node = nodes[nodeIndex]
            val predecessor = nodes[nodeIndex - 1]
            // Can only indent by exactly one level at a time
            if (predecessor.depth != node.depth) return nodes

            val result = nodes.toMutableList()
            result[nodeIndex] = node.copy(
                parentId = predecessor.id,
                depth = node.depth + 1,
            )
            // Shift all descendants by the same delta (+1)
            var i = nodeIndex + 1
            while (i < result.size && result[i].depth > node.depth) {
                result[i] = result[i].copy(depth = result[i].depth + 1)
                i++
            }
            return recomputeSortOrders(result)
        }

        /**
         * Outdent the node at [nodeIndex] by one level.
         * Its new parent becomes its current grandparent (or null for root level).
         */
        fun outdent(nodes: List<FlatNode>, nodeIndex: Int): List<FlatNode> {
            if (nodeIndex >= nodes.size) return nodes
            val node = nodes[nodeIndex]
            if (node.depth == 0) return nodes   // already at root level

            // Find the grandparent id by looking backwards for the current parent
            val parentNode = nodes.subList(0, nodeIndex).lastOrNull { it.id == node.parentId }
            val grandparentId = parentNode?.parentId

            val result = nodes.toMutableList()
            result[nodeIndex] = node.copy(
                parentId = grandparentId,
                depth = node.depth - 1,
            )
            // Shift all descendants by -1
            var i = nodeIndex + 1
            while (i < result.size && result[i].depth > node.depth) {
                result[i] = result[i].copy(depth = result[i].depth - 1)
                i++
            }
            return recomputeSortOrders(result)
        }

        /**
         * Reassign sort_order values so that within every sibling group the values
         * are strictly ascending (lexicographically), matching the flat-list order.
         *
         * Uses evenly-spaced single-character fractional keys ("a0", "a1", …, "az")
         * which is sufficient for up to 62 siblings — fine for the prototype.
         */
        fun recomputeSortOrders(nodes: List<FlatNode>): List<FlatNode> {
            // Collect indices per parent, in flat-list order (= correct sibling order)
            val parentToIndices = linkedMapOf<String?, MutableList<Int>>()
            nodes.forEachIndexed { idx, node ->
                parentToIndices.getOrPut(node.parentId) { mutableListOf() }.add(idx)
            }

            val result = nodes.toMutableList()
            for ((_, indices) in parentToIndices) {
                indices.forEachIndexed { rank, nodeIdx ->
                    val charIdx = rank.coerceAtMost(FractionalIndex.DIGITS.length - 1)
                    result[nodeIdx] = result[nodeIdx].copy(
                        sortOrder = "a${FractionalIndex.DIGITS[charIdx]}"
                    )
                }
            }
            return result
        }
    }
}

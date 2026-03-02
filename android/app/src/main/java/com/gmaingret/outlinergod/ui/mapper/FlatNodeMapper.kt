package com.gmaingret.outlinergod.ui.mapper

import com.gmaingret.outlinergod.db.entity.NodeEntity

/**
 * Converts a flat list of [NodeEntity] into a depth-annotated [FlatNode] list
 * suitable for rendering as a tree in a LazyColumn. Stateless and thread-safe.
 */
fun mapToFlatList(nodes: List<NodeEntity>, documentId: String): List<FlatNode> {
    // 1. Filter out soft-deleted nodes
    val active = nodes.filter { it.deletedAt == null }

    // 2. Build parent -> children map, sorted by sortOrder ASC then id ASC
    val childrenMap: Map<String?, List<NodeEntity>> = active
        .groupBy { it.parentId }
        .mapValues { (_, children) ->
            children.sortedWith(compareBy<NodeEntity> { it.sortOrder }.thenBy { it.id })
        }

    // 3. Collect all active node IDs for orphan detection
    val activeIds = active.map { it.id }.toSet()

    // 4. DFS traversal
    val result = mutableListOf<FlatNode>()

    fun dfs(parentId: String?, depth: Int) {
        val children = childrenMap[parentId] ?: return
        for (child in children) {
            val hasChildren = childrenMap[child.id]?.isNotEmpty() == true
            result.add(FlatNode(entity = child, depth = depth, hasChildren = hasChildren))
            if (child.collapsed == 0) {
                dfs(child.id, depth + 1)
            }
        }
    }

    // Start from root nodes (parentId == documentId)
    dfs(documentId, 0)

    // 5. Orphan handling: nodes whose parentId is not null, not documentId,
    //    and not an existing active node ID
    val visitedIds = result.map { it.entity.id }.toSet()
    for (node in active) {
        if (node.id !in visitedIds) {
            val parentId = node.parentId
            val isOrphan = parentId != null &&
                parentId != documentId &&
                parentId !in activeIds
            if (isOrphan) {
                result.add(FlatNode(entity = node, depth = 0, hasChildren = false))
            }
        }
    }

    return result
}

package com.gmaingret.outlinergod.repository

import com.gmaingret.outlinergod.db.entity.NodeEntity

interface SearchRepository {
    /**
     * Searches nodes for [query] using FTS4 MATCH, filtered to [userId].
     *
     * Supports structured operators parsed by SearchQueryParser:
     *  - is:completed / is:not-completed
     *  - color:red/orange/yellow/green/blue/purple
     *  - in:note (post-filter: result must match in note field)
     *  - in:title (post-filter: result must match in content field)
     *
     * Returns results ordered by last updated, or empty list if query has no FTS terms.
     * Soft-deleted nodes are never returned.
     */
    suspend fun searchNodes(query: String, userId: String): List<NodeEntity>
}

package com.gmaingret.outlinergod.sync

import com.gmaingret.outlinergod.db.entity.BookmarkEntity

/**
 * Per-field Last-Write-Wins merge for BookmarkEntity.
 * For each field pair (value/hlc), keep whichever side has the lexicographically greater HLC.
 * On equal HLCs, use a deterministic value-based tiebreaker for commutativity.
 * Immutable fields (id, userId, createdAt) always come from local.
 */
fun mergeBookmark(local: BookmarkEntity, incoming: BookmarkEntity): BookmarkEntity {
    fun lwwStr(aVal: String, aHlc: String, bVal: String, bHlc: String): String = when {
        aHlc > bHlc -> aVal
        bHlc > aHlc -> bVal
        else -> maxOf(aVal, bVal)
    }

    fun lwwNullableStr(aVal: String?, aHlc: String, bVal: String?, bHlc: String): String? = when {
        aHlc > bHlc -> aVal
        bHlc > aHlc -> bVal
        else -> {
            val aComp = aVal ?: ""
            val bComp = bVal ?: ""
            val winner = maxOf(aComp, bComp)
            if (winner.isEmpty() && aVal == null && bVal == null) null
            else if (winner == aComp) aVal
            else bVal
        }
    }

    fun lwwNullableLong(aVal: Long?, aHlc: String, bVal: Long?, bHlc: String): Long? = when {
        aHlc > bHlc -> aVal
        bHlc > aHlc -> bVal
        else -> when {
            aVal == null && bVal == null -> null
            aVal == null -> bVal
            bVal == null -> aVal
            else -> maxOf(aVal, bVal)
        }
    }

    fun lwwHlc(aHlc: String, bHlc: String): String = maxOf(aHlc, bHlc)

    return local.copy(
        title = lwwStr(local.title, local.titleHlc, incoming.title, incoming.titleHlc),
        titleHlc = lwwHlc(local.titleHlc, incoming.titleHlc),
        targetType = lwwStr(local.targetType, local.targetTypeHlc, incoming.targetType, incoming.targetTypeHlc),
        targetTypeHlc = lwwHlc(local.targetTypeHlc, incoming.targetTypeHlc),
        targetDocumentId = lwwNullableStr(local.targetDocumentId, local.targetDocumentIdHlc, incoming.targetDocumentId, incoming.targetDocumentIdHlc),
        targetDocumentIdHlc = lwwHlc(local.targetDocumentIdHlc, incoming.targetDocumentIdHlc),
        targetNodeId = lwwNullableStr(local.targetNodeId, local.targetNodeIdHlc, incoming.targetNodeId, incoming.targetNodeIdHlc),
        targetNodeIdHlc = lwwHlc(local.targetNodeIdHlc, incoming.targetNodeIdHlc),
        query = lwwNullableStr(local.query, local.queryHlc, incoming.query, incoming.queryHlc),
        queryHlc = lwwHlc(local.queryHlc, incoming.queryHlc),
        sortOrder = lwwStr(local.sortOrder, local.sortOrderHlc, incoming.sortOrder, incoming.sortOrderHlc),
        sortOrderHlc = lwwHlc(local.sortOrderHlc, incoming.sortOrderHlc),
        deletedAt = lwwNullableLong(local.deletedAt, local.deletedHlc, incoming.deletedAt, incoming.deletedHlc),
        deletedHlc = lwwHlc(local.deletedHlc, incoming.deletedHlc),
        updatedAt = maxOf(local.updatedAt, incoming.updatedAt),
    )
}

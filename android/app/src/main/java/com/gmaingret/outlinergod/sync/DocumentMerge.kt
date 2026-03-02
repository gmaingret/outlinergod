package com.gmaingret.outlinergod.sync

import com.gmaingret.outlinergod.db.entity.DocumentEntity

/**
 * Per-field Last-Write-Wins merge for DocumentEntity.
 * For each field pair (value/hlc), keep whichever side has the lexicographically greater HLC.
 * On equal HLCs, use a deterministic value-based tiebreaker for commutativity.
 * Immutable fields (id, userId, type, createdAt) always come from local.
 */
fun mergeDocument(local: DocumentEntity, incoming: DocumentEntity): DocumentEntity {
    fun lwwStr(aVal: String, aHlc: String, bVal: String, bHlc: String): String = when {
        aHlc > bHlc -> aVal
        bHlc > aHlc -> bVal
        else -> maxOf(aVal, bVal)
    }

    fun lwwInt(aVal: Int, aHlc: String, bVal: Int, bHlc: String): Int = when {
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
        parentId = lwwNullableStr(local.parentId, local.parentIdHlc, incoming.parentId, incoming.parentIdHlc),
        parentIdHlc = lwwHlc(local.parentIdHlc, incoming.parentIdHlc),
        sortOrder = lwwStr(local.sortOrder, local.sortOrderHlc, incoming.sortOrder, incoming.sortOrderHlc),
        sortOrderHlc = lwwHlc(local.sortOrderHlc, incoming.sortOrderHlc),
        collapsed = lwwInt(local.collapsed, local.collapsedHlc, incoming.collapsed, incoming.collapsedHlc),
        collapsedHlc = lwwHlc(local.collapsedHlc, incoming.collapsedHlc),
        deletedAt = lwwNullableLong(local.deletedAt, local.deletedHlc, incoming.deletedAt, incoming.deletedHlc),
        deletedHlc = lwwHlc(local.deletedHlc, incoming.deletedHlc),
        updatedAt = maxOf(local.updatedAt, incoming.updatedAt),
    )
}

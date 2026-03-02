package com.gmaingret.outlinergod.sync

import com.gmaingret.outlinergod.db.entity.NodeEntity

/**
 * Per-field Last-Write-Wins merge for NodeEntity.
 * For each field pair (value/hlc), keep whichever side has the lexicographically greater HLC.
 * On equal HLCs, use a deterministic value-based tiebreaker for commutativity.
 * Immutable fields (id, userId, documentId, createdAt) always come from local.
 */
fun mergeNode(local: NodeEntity, incoming: NodeEntity): NodeEntity {
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
        content = lwwStr(local.content, local.contentHlc, incoming.content, incoming.contentHlc),
        contentHlc = lwwHlc(local.contentHlc, incoming.contentHlc),
        note = lwwStr(local.note, local.noteHlc, incoming.note, incoming.noteHlc),
        noteHlc = lwwHlc(local.noteHlc, incoming.noteHlc),
        parentId = lwwNullableStr(local.parentId, local.parentIdHlc, incoming.parentId, incoming.parentIdHlc),
        parentIdHlc = lwwHlc(local.parentIdHlc, incoming.parentIdHlc),
        sortOrder = lwwStr(local.sortOrder, local.sortOrderHlc, incoming.sortOrder, incoming.sortOrderHlc),
        sortOrderHlc = lwwHlc(local.sortOrderHlc, incoming.sortOrderHlc),
        completed = lwwInt(local.completed, local.completedHlc, incoming.completed, incoming.completedHlc),
        completedHlc = lwwHlc(local.completedHlc, incoming.completedHlc),
        color = lwwInt(local.color, local.colorHlc, incoming.color, incoming.colorHlc),
        colorHlc = lwwHlc(local.colorHlc, incoming.colorHlc),
        collapsed = lwwInt(local.collapsed, local.collapsedHlc, incoming.collapsed, incoming.collapsedHlc),
        collapsedHlc = lwwHlc(local.collapsedHlc, incoming.collapsedHlc),
        deletedAt = lwwNullableLong(local.deletedAt, local.deletedHlc, incoming.deletedAt, incoming.deletedHlc),
        deletedHlc = lwwHlc(local.deletedHlc, incoming.deletedHlc),
        updatedAt = maxOf(local.updatedAt, incoming.updatedAt),
    )
}

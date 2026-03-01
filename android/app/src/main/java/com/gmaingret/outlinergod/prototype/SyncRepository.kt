package com.gmaingret.outlinergod.prototype

// ---------------------------------------------------------------------------
// Node sync record — mirrors the backend NodeSyncRecord interface exactly.
// All HLC fields use the <wall_ms_16hex>-<counter_4hex>-<device_id> format.
// ---------------------------------------------------------------------------

data class NodeSyncRecord(
    val id: String,
    val documentId: String,
    val content: String,
    val contentHlc: String,
    val note: String,
    val noteHlc: String,
    val parentId: String?,
    val parentIdHlc: String,
    val sortOrder: String,
    val sortOrderHlc: String,
    val completed: Int,
    val completedHlc: String,
    val color: Int,
    val colorHlc: String,
    val collapsed: Int,
    val collapsedHlc: String,
    val deletedAt: Long?,
    val deletedHlc: String,
    val deviceId: String,
)

// ---------------------------------------------------------------------------
// LWW merge — per-field Last-Write-Wins using HLC lexicographic comparison.
//
// Tiebreaker when two HLCs are equal: use a deterministic comparison on the
// VALUE itself. This makes the function fully commutative for all inputs,
// including the equal-HLC edge case (which cannot occur in production because
// HLC strings embed a unique device_id, guaranteeing distinctness).
//
// Properties guaranteed:
//   Commutative:  merge(a, b) == merge(b, a)  for every field
//   Idempotent:   merge(a, merge(a, b)) == merge(a, b)
// ---------------------------------------------------------------------------

object LwwMerge {

    // -----------------------------------------------------------------------
    // Field-level LWW helpers (all commutative)
    // -----------------------------------------------------------------------

    /** LWW for String values. Tiebreaker: lexicographic max of value. */
    private fun lwwStr(aVal: String, aHlc: String, bVal: String, bHlc: String): String = when {
        aHlc > bHlc -> aVal
        bHlc > aHlc -> bVal
        else -> maxOf(aVal, bVal) // commutative value tiebreaker
    }

    /** LWW for Int values. */
    private fun lwwInt(aVal: Int, aHlc: String, bVal: Int, bHlc: String): Int = when {
        aHlc > bHlc -> aVal
        bHlc > aHlc -> bVal
        else -> maxOf(aVal, bVal)
    }

    /**
     * LWW for nullable Long values (used for deletedAt).
     * Tiebreaker when HLCs are equal: non-null > null; among two non-nulls pick max.
     */
    private fun lwwNullableLong(aVal: Long?, aHlc: String, bVal: Long?, bHlc: String): Long? =
        when {
            aHlc > bHlc -> aVal
            bHlc > aHlc -> bVal
            else -> when {
                aVal == null && bVal == null -> null
                aVal == null -> bVal
                bVal == null -> aVal
                else -> maxOf(aVal, bVal)
            }
        }

    // -----------------------------------------------------------------------
    // Node merge
    // -----------------------------------------------------------------------

    /** Merge two NodeSyncRecords using per-field LWW. `a` and `b` must share the same id. */
    fun mergeNodes(a: NodeSyncRecord, b: NodeSyncRecord): NodeSyncRecord {
        val content = lwwStr(a.content, a.contentHlc, b.content, b.contentHlc)
        val contentHlc = maxOf(a.contentHlc, b.contentHlc)

        val note = lwwStr(a.note, a.noteHlc, b.note, b.noteHlc)
        val noteHlc = maxOf(a.noteHlc, b.noteHlc)

        // parentId is nullable; coerce to "" for comparison, then back to null
        val rawParentId = lwwStr(a.parentId ?: "", a.parentIdHlc, b.parentId ?: "", b.parentIdHlc)
        val parentId: String? = rawParentId.ifEmpty { null }
        val parentIdHlc = maxOf(a.parentIdHlc, b.parentIdHlc)

        val sortOrder = lwwStr(a.sortOrder, a.sortOrderHlc, b.sortOrder, b.sortOrderHlc)
        val sortOrderHlc = maxOf(a.sortOrderHlc, b.sortOrderHlc)

        val completed = lwwInt(a.completed, a.completedHlc, b.completed, b.completedHlc)
        val completedHlc = maxOf(a.completedHlc, b.completedHlc)

        val color = lwwInt(a.color, a.colorHlc, b.color, b.colorHlc)
        val colorHlc = maxOf(a.colorHlc, b.colorHlc)

        val collapsed = lwwInt(a.collapsed, a.collapsedHlc, b.collapsed, b.collapsedHlc)
        val collapsedHlc = maxOf(a.collapsedHlc, b.collapsedHlc)

        // Deletion wins when deleted_hlc is highest — handled automatically because
        // deletedAt is just another per-field LWW register.
        val deletedAt = lwwNullableLong(a.deletedAt, a.deletedHlc, b.deletedAt, b.deletedHlc)
        val deletedHlc = maxOf(a.deletedHlc, b.deletedHlc)

        // device_id: use the record with the overall highest field HLC.
        // When both sides have the same max HLC, use lexicographic max of device_ids
        // as a deterministic tiebreaker — this preserves commutativity.
        val maxA = listOf(
            a.contentHlc, a.noteHlc, a.parentIdHlc, a.sortOrderHlc,
            a.completedHlc, a.colorHlc, a.collapsedHlc, a.deletedHlc,
        ).maxOrNull() ?: ""
        val maxB = listOf(
            b.contentHlc, b.noteHlc, b.parentIdHlc, b.sortOrderHlc,
            b.completedHlc, b.colorHlc, b.collapsedHlc, b.deletedHlc,
        ).maxOrNull() ?: ""
        val deviceId = when {
            maxB > maxA -> b.deviceId
            maxA > maxB -> a.deviceId
            else -> maxOf(a.deviceId, b.deviceId) // commutative tiebreaker
        }

        return NodeSyncRecord(
            id = a.id,
            documentId = a.documentId,
            content = content,
            contentHlc = contentHlc,
            note = note,
            noteHlc = noteHlc,
            parentId = parentId,
            parentIdHlc = parentIdHlc,
            sortOrder = sortOrder,
            sortOrderHlc = sortOrderHlc,
            completed = completed,
            completedHlc = completedHlc,
            color = color,
            colorHlc = colorHlc,
            collapsed = collapsed,
            collapsedHlc = collapsedHlc,
            deletedAt = deletedAt,
            deletedHlc = deletedHlc,
            deviceId = deviceId,
        )
    }
}

// ---------------------------------------------------------------------------
// Sync repository interface — pull-before-push protocol.
// Real implementation will use Ktor Client in Phase 3; this is the contract.
// ---------------------------------------------------------------------------

data class PullResult(
    val serverHlc: String,
    val nodes: List<NodeSyncRecord>,
)

data class PushResult(
    val serverHlc: String,
    val acceptedNodeIds: List<String>,
    val conflicts: List<NodeSyncRecord>,
)

interface SyncRepository {
    /** Pull all changes from the server that occurred after [since] HLC. */
    suspend fun pull(since: String, deviceId: String): PullResult

    /** Push locally pending [records] to the server. */
    suspend fun push(records: List<NodeSyncRecord>, deviceId: String): PushResult
}

/**
 * Stub implementation for the prototype.
 * The real Ktor Client–backed implementation is built in Phase 3.
 */
class StubSyncRepository : SyncRepository {
    override suspend fun pull(since: String, deviceId: String): PullResult =
        PullResult(serverHlc = since, nodes = emptyList())

    override suspend fun push(records: List<NodeSyncRecord>, deviceId: String): PushResult =
        PushResult(serverHlc = "", acceptedNodeIds = emptyList(), conflicts = emptyList())
}

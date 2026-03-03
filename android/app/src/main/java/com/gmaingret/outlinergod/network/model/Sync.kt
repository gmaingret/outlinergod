package com.gmaingret.outlinergod.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NodeSyncRecord(
    @SerialName("id") val id: String,
    @SerialName("document_id") val documentId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("content") val content: String = "",
    @SerialName("content_hlc") val contentHlc: String = "",
    @SerialName("note") val note: String = "",
    @SerialName("note_hlc") val noteHlc: String = "",
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("parent_id_hlc") val parentIdHlc: String = "",
    @SerialName("sort_order") val sortOrder: String = "",
    @SerialName("sort_order_hlc") val sortOrderHlc: String = "",
    @SerialName("completed") val completed: Int = 0,
    @SerialName("completed_hlc") val completedHlc: String = "",
    @SerialName("color") val color: Int = 0,
    @SerialName("color_hlc") val colorHlc: String = "",
    @SerialName("collapsed") val collapsed: Int = 0,
    @SerialName("collapsed_hlc") val collapsedHlc: String = "",
    @SerialName("deleted_at") val deletedAt: Long? = null,
    @SerialName("deleted_hlc") val deletedHlc: String = "",
    @SerialName("device_id") val deviceId: String = "",
    @SerialName("created_at") val createdAt: Long = 0L,
    @SerialName("updated_at") val updatedAt: Long = 0L
)

@Serializable
data class DocumentSyncRecord(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("title") val title: String = "",
    @SerialName("title_hlc") val titleHlc: String = "",
    @SerialName("type") val type: String = "document",
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("parent_id_hlc") val parentIdHlc: String = "",
    @SerialName("sort_order") val sortOrder: String = "",
    @SerialName("sort_order_hlc") val sortOrderHlc: String = "",
    @SerialName("collapsed") val collapsed: Int = 0,
    @SerialName("collapsed_hlc") val collapsedHlc: String = "",
    @SerialName("deleted_at") val deletedAt: Long? = null,
    @SerialName("deleted_hlc") val deletedHlc: String = "",
    @SerialName("device_id") val deviceId: String = "",
    @SerialName("created_at") val createdAt: Long = 0L,
    @SerialName("updated_at") val updatedAt: Long = 0L
)

@Serializable
data class BookmarkSyncRecord(
    @SerialName("id") val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("title") val title: String = "",
    @SerialName("title_hlc") val titleHlc: String = "",
    @SerialName("target_type") val targetType: String = "",
    @SerialName("target_type_hlc") val targetTypeHlc: String = "",
    @SerialName("target_document_id") val targetDocumentId: String? = null,
    @SerialName("target_document_id_hlc") val targetDocumentIdHlc: String = "",
    @SerialName("target_node_id") val targetNodeId: String? = null,
    @SerialName("target_node_id_hlc") val targetNodeIdHlc: String = "",
    @SerialName("query") val query: String? = null,
    @SerialName("query_hlc") val queryHlc: String = "",
    @SerialName("sort_order") val sortOrder: String = "",
    @SerialName("sort_order_hlc") val sortOrderHlc: String = "",
    @SerialName("deleted_at") val deletedAt: Long? = null,
    @SerialName("deleted_hlc") val deletedHlc: String = "",
    @SerialName("device_id") val deviceId: String = "",
    @SerialName("created_at") val createdAt: Long = 0L,
    @SerialName("updated_at") val updatedAt: Long = 0L
)

@Serializable
data class SettingsSyncRecord(
    @SerialName("id") val id: String = "",
    @SerialName("user_id") val userId: String,
    @SerialName("theme") val theme: String = "dark",
    @SerialName("theme_hlc") val themeHlc: String = "",
    @SerialName("density") val density: String = "cozy",
    @SerialName("density_hlc") val densityHlc: String = "",
    @SerialName("show_guide_lines") val showGuideLines: Int = 1,
    @SerialName("show_guide_lines_hlc") val showGuideLinesHlc: String = "",
    @SerialName("show_backlink_badge") val showBacklinkBadge: Int = 1,
    @SerialName("show_backlink_badge_hlc") val showBacklinkBadgeHlc: String = "",
    @SerialName("device_id") val deviceId: String = "",
    @SerialName("updated_at") val updatedAt: Long = 0L
)

@Serializable
data class SyncChangesResponse(
    @SerialName("server_hlc") val serverHlc: String,
    @SerialName("nodes") val nodes: List<NodeSyncRecord> = emptyList(),
    @SerialName("documents") val documents: List<DocumentSyncRecord> = emptyList(),
    @SerialName("settings") val settings: SettingsSyncRecord? = null,
    @SerialName("bookmarks") val bookmarks: List<BookmarkSyncRecord> = emptyList()
)

@Serializable
data class SyncPushPayload(
    @SerialName("device_id") val deviceId: String,
    @SerialName("nodes") val nodes: List<NodeSyncRecord>? = null,
    @SerialName("documents") val documents: List<DocumentSyncRecord>? = null,
    @SerialName("settings") val settings: SettingsSyncRecord? = null,
    @SerialName("bookmarks") val bookmarks: List<BookmarkSyncRecord>? = null
)

@Serializable
data class SyncConflicts(
    @SerialName("nodes") val nodes: List<NodeSyncRecord> = emptyList(),
    @SerialName("documents") val documents: List<DocumentSyncRecord> = emptyList(),
    @SerialName("bookmarks") val bookmarks: List<BookmarkSyncRecord> = emptyList(),
    @SerialName("settings") val settings: SettingsSyncRecord? = null
)

@Serializable
data class SyncPushResponse(
    @SerialName("server_hlc") val serverHlc: String,
    @SerialName("accepted_node_ids") val acceptedNodeIds: List<String> = emptyList(),
    @SerialName("accepted_document_ids") val acceptedDocumentIds: List<String> = emptyList(),
    @SerialName("accepted_bookmark_ids") val acceptedBookmarkIds: List<String> = emptyList(),
    @SerialName("conflicts") val conflicts: SyncConflicts = SyncConflicts()
)

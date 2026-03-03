package com.gmaingret.outlinergod.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val title: String = "",
    @ColumnInfo(name = "title_hlc") val titleHlc: String = "",
    @ColumnInfo(name = "target_type") val targetType: String = "",
    @ColumnInfo(name = "target_type_hlc") val targetTypeHlc: String = "",
    @ColumnInfo(name = "target_document_id") val targetDocumentId: String? = null,
    @ColumnInfo(name = "target_document_id_hlc") val targetDocumentIdHlc: String = "",
    @ColumnInfo(name = "target_node_id") val targetNodeId: String? = null,
    @ColumnInfo(name = "target_node_id_hlc") val targetNodeIdHlc: String = "",
    val query: String? = null,
    @ColumnInfo(name = "query_hlc") val queryHlc: String = "",
    @ColumnInfo(name = "sort_order") val sortOrder: String = "",
    @ColumnInfo(name = "sort_order_hlc") val sortOrderHlc: String = "",
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "deleted_hlc") val deletedHlc: String = "",
    @ColumnInfo(name = "device_id") val deviceId: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

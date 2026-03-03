package com.gmaingret.outlinergod.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "nodes")
data class NodeEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "document_id") val documentId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val content: String = "",
    @ColumnInfo(name = "content_hlc") val contentHlc: String = "",
    val note: String = "",
    @ColumnInfo(name = "note_hlc") val noteHlc: String = "",
    @ColumnInfo(name = "parent_id") val parentId: String? = null,
    @ColumnInfo(name = "parent_id_hlc") val parentIdHlc: String = "",
    @ColumnInfo(name = "sort_order") val sortOrder: String,
    @ColumnInfo(name = "sort_order_hlc") val sortOrderHlc: String = "",
    val completed: Int = 0,
    @ColumnInfo(name = "completed_hlc") val completedHlc: String = "",
    val color: Int = 0,
    @ColumnInfo(name = "color_hlc") val colorHlc: String = "",
    val collapsed: Int = 0,
    @ColumnInfo(name = "collapsed_hlc") val collapsedHlc: String = "",
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "deleted_hlc") val deletedHlc: String = "",
    @ColumnInfo(name = "device_id") val deviceId: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

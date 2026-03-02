package com.gmaingret.outlinergod.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    val title: String = "",
    @ColumnInfo(name = "title_hlc") val titleHlc: String = "",
    val type: String = "document",
    @ColumnInfo(name = "parent_id") val parentId: String? = null,
    @ColumnInfo(name = "parent_id_hlc") val parentIdHlc: String = "",
    @ColumnInfo(name = "sort_order") val sortOrder: String,
    @ColumnInfo(name = "sort_order_hlc") val sortOrderHlc: String = "",
    val collapsed: Int = 0,
    @ColumnInfo(name = "collapsed_hlc") val collapsedHlc: String = "",
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "deleted_hlc") val deletedHlc: String = "",
    @ColumnInfo(name = "device_id") val deviceId: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "sync_status") val syncStatus: Int = 0,
)

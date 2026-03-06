package com.gmaingret.outlinergod.db.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.RoomRawQuery
import androidx.room.RoomSQLiteQuery
import androidx.room.coroutines.createFlow
import androidx.room.util.appendPlaceholders
import androidx.room.util.getColumnIndex
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.db.SupportSQLiteQuery
import com.gmaingret.outlinergod.db.entity.NodeEntity
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlin.text.StringBuilder
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class NodeDao_Impl(
  __db: RoomDatabase,
) : NodeDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfNodeEntity: EntityInsertAdapter<NodeEntity>

  private val __updateAdapterOfNodeEntity: EntityDeleteOrUpdateAdapter<NodeEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfNodeEntity = object : EntityInsertAdapter<NodeEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `nodes` (`id`,`document_id`,`user_id`,`content`,`content_hlc`,`note`,`note_hlc`,`parent_id`,`parent_id_hlc`,`sort_order`,`sort_order_hlc`,`completed`,`completed_hlc`,`color`,`color_hlc`,`collapsed`,`collapsed_hlc`,`deleted_at`,`deleted_hlc`,`device_id`,`created_at`,`updated_at`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: NodeEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.documentId)
        statement.bindText(3, entity.userId)
        statement.bindText(4, entity.content)
        statement.bindText(5, entity.contentHlc)
        statement.bindText(6, entity.note)
        statement.bindText(7, entity.noteHlc)
        val _tmpParentId: String? = entity.parentId
        if (_tmpParentId == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpParentId)
        }
        statement.bindText(9, entity.parentIdHlc)
        statement.bindText(10, entity.sortOrder)
        statement.bindText(11, entity.sortOrderHlc)
        statement.bindLong(12, entity.completed.toLong())
        statement.bindText(13, entity.completedHlc)
        statement.bindLong(14, entity.color.toLong())
        statement.bindText(15, entity.colorHlc)
        statement.bindLong(16, entity.collapsed.toLong())
        statement.bindText(17, entity.collapsedHlc)
        val _tmpDeletedAt: Long? = entity.deletedAt
        if (_tmpDeletedAt == null) {
          statement.bindNull(18)
        } else {
          statement.bindLong(18, _tmpDeletedAt)
        }
        statement.bindText(19, entity.deletedHlc)
        statement.bindText(20, entity.deviceId)
        statement.bindLong(21, entity.createdAt)
        statement.bindLong(22, entity.updatedAt)
      }
    }
    this.__updateAdapterOfNodeEntity = object : EntityDeleteOrUpdateAdapter<NodeEntity>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `nodes` SET `id` = ?,`document_id` = ?,`user_id` = ?,`content` = ?,`content_hlc` = ?,`note` = ?,`note_hlc` = ?,`parent_id` = ?,`parent_id_hlc` = ?,`sort_order` = ?,`sort_order_hlc` = ?,`completed` = ?,`completed_hlc` = ?,`color` = ?,`color_hlc` = ?,`collapsed` = ?,`collapsed_hlc` = ?,`deleted_at` = ?,`deleted_hlc` = ?,`device_id` = ?,`created_at` = ?,`updated_at` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: NodeEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.documentId)
        statement.bindText(3, entity.userId)
        statement.bindText(4, entity.content)
        statement.bindText(5, entity.contentHlc)
        statement.bindText(6, entity.note)
        statement.bindText(7, entity.noteHlc)
        val _tmpParentId: String? = entity.parentId
        if (_tmpParentId == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpParentId)
        }
        statement.bindText(9, entity.parentIdHlc)
        statement.bindText(10, entity.sortOrder)
        statement.bindText(11, entity.sortOrderHlc)
        statement.bindLong(12, entity.completed.toLong())
        statement.bindText(13, entity.completedHlc)
        statement.bindLong(14, entity.color.toLong())
        statement.bindText(15, entity.colorHlc)
        statement.bindLong(16, entity.collapsed.toLong())
        statement.bindText(17, entity.collapsedHlc)
        val _tmpDeletedAt: Long? = entity.deletedAt
        if (_tmpDeletedAt == null) {
          statement.bindNull(18)
        } else {
          statement.bindLong(18, _tmpDeletedAt)
        }
        statement.bindText(19, entity.deletedHlc)
        statement.bindText(20, entity.deviceId)
        statement.bindLong(21, entity.createdAt)
        statement.bindLong(22, entity.updatedAt)
        statement.bindText(23, entity.id)
      }
    }
  }

  public override suspend fun insertNode(node: NodeEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfNodeEntity.insert(_connection, node)
  }

  public override suspend fun upsertNodes(nodes: List<NodeEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfNodeEntity.insert(_connection, nodes)
  }

  public override suspend fun updateNode(node: NodeEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfNodeEntity.handle(_connection, node)
  }

  public override fun getNodesByDocument(documentId: String): Flow<List<NodeEntity>> {
    val _sql: String = "SELECT * FROM nodes WHERE document_id = ? AND deleted_at IS NULL"
    return createFlow(__db, false, arrayOf("nodes")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, documentId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfDocumentId: Int = getColumnIndexOrThrow(_stmt, "document_id")
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "user_id")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfContentHlc: Int = getColumnIndexOrThrow(_stmt, "content_hlc")
        val _columnIndexOfNote: Int = getColumnIndexOrThrow(_stmt, "note")
        val _columnIndexOfNoteHlc: Int = getColumnIndexOrThrow(_stmt, "note_hlc")
        val _columnIndexOfParentId: Int = getColumnIndexOrThrow(_stmt, "parent_id")
        val _columnIndexOfParentIdHlc: Int = getColumnIndexOrThrow(_stmt, "parent_id_hlc")
        val _columnIndexOfSortOrder: Int = getColumnIndexOrThrow(_stmt, "sort_order")
        val _columnIndexOfSortOrderHlc: Int = getColumnIndexOrThrow(_stmt, "sort_order_hlc")
        val _columnIndexOfCompleted: Int = getColumnIndexOrThrow(_stmt, "completed")
        val _columnIndexOfCompletedHlc: Int = getColumnIndexOrThrow(_stmt, "completed_hlc")
        val _columnIndexOfColor: Int = getColumnIndexOrThrow(_stmt, "color")
        val _columnIndexOfColorHlc: Int = getColumnIndexOrThrow(_stmt, "color_hlc")
        val _columnIndexOfCollapsed: Int = getColumnIndexOrThrow(_stmt, "collapsed")
        val _columnIndexOfCollapsedHlc: Int = getColumnIndexOrThrow(_stmt, "collapsed_hlc")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfDeletedHlc: Int = getColumnIndexOrThrow(_stmt, "deleted_hlc")
        val _columnIndexOfDeviceId: Int = getColumnIndexOrThrow(_stmt, "device_id")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: MutableList<NodeEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: NodeEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpDocumentId: String
          _tmpDocumentId = _stmt.getText(_columnIndexOfDocumentId)
          val _tmpUserId: String
          _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpContentHlc: String
          _tmpContentHlc = _stmt.getText(_columnIndexOfContentHlc)
          val _tmpNote: String
          _tmpNote = _stmt.getText(_columnIndexOfNote)
          val _tmpNoteHlc: String
          _tmpNoteHlc = _stmt.getText(_columnIndexOfNoteHlc)
          val _tmpParentId: String?
          if (_stmt.isNull(_columnIndexOfParentId)) {
            _tmpParentId = null
          } else {
            _tmpParentId = _stmt.getText(_columnIndexOfParentId)
          }
          val _tmpParentIdHlc: String
          _tmpParentIdHlc = _stmt.getText(_columnIndexOfParentIdHlc)
          val _tmpSortOrder: String
          _tmpSortOrder = _stmt.getText(_columnIndexOfSortOrder)
          val _tmpSortOrderHlc: String
          _tmpSortOrderHlc = _stmt.getText(_columnIndexOfSortOrderHlc)
          val _tmpCompleted: Int
          _tmpCompleted = _stmt.getLong(_columnIndexOfCompleted).toInt()
          val _tmpCompletedHlc: String
          _tmpCompletedHlc = _stmt.getText(_columnIndexOfCompletedHlc)
          val _tmpColor: Int
          _tmpColor = _stmt.getLong(_columnIndexOfColor).toInt()
          val _tmpColorHlc: String
          _tmpColorHlc = _stmt.getText(_columnIndexOfColorHlc)
          val _tmpCollapsed: Int
          _tmpCollapsed = _stmt.getLong(_columnIndexOfCollapsed).toInt()
          val _tmpCollapsedHlc: String
          _tmpCollapsedHlc = _stmt.getText(_columnIndexOfCollapsedHlc)
          val _tmpDeletedAt: Long?
          if (_stmt.isNull(_columnIndexOfDeletedAt)) {
            _tmpDeletedAt = null
          } else {
            _tmpDeletedAt = _stmt.getLong(_columnIndexOfDeletedAt)
          }
          val _tmpDeletedHlc: String
          _tmpDeletedHlc = _stmt.getText(_columnIndexOfDeletedHlc)
          val _tmpDeviceId: String
          _tmpDeviceId = _stmt.getText(_columnIndexOfDeviceId)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _item = NodeEntity(_tmpId,_tmpDocumentId,_tmpUserId,_tmpContent,_tmpContentHlc,_tmpNote,_tmpNoteHlc,_tmpParentId,_tmpParentIdHlc,_tmpSortOrder,_tmpSortOrderHlc,_tmpCompleted,_tmpCompletedHlc,_tmpColor,_tmpColorHlc,_tmpCollapsed,_tmpCollapsedHlc,_tmpDeletedAt,_tmpDeletedHlc,_tmpDeviceId,_tmpCreatedAt,_tmpUpdatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getNodeById(nodeId: String): Flow<NodeEntity?> {
    val _sql: String = "SELECT * FROM nodes WHERE id = ? LIMIT 1"
    return createFlow(__db, false, arrayOf("nodes")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, nodeId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfDocumentId: Int = getColumnIndexOrThrow(_stmt, "document_id")
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "user_id")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfContentHlc: Int = getColumnIndexOrThrow(_stmt, "content_hlc")
        val _columnIndexOfNote: Int = getColumnIndexOrThrow(_stmt, "note")
        val _columnIndexOfNoteHlc: Int = getColumnIndexOrThrow(_stmt, "note_hlc")
        val _columnIndexOfParentId: Int = getColumnIndexOrThrow(_stmt, "parent_id")
        val _columnIndexOfParentIdHlc: Int = getColumnIndexOrThrow(_stmt, "parent_id_hlc")
        val _columnIndexOfSortOrder: Int = getColumnIndexOrThrow(_stmt, "sort_order")
        val _columnIndexOfSortOrderHlc: Int = getColumnIndexOrThrow(_stmt, "sort_order_hlc")
        val _columnIndexOfCompleted: Int = getColumnIndexOrThrow(_stmt, "completed")
        val _columnIndexOfCompletedHlc: Int = getColumnIndexOrThrow(_stmt, "completed_hlc")
        val _columnIndexOfColor: Int = getColumnIndexOrThrow(_stmt, "color")
        val _columnIndexOfColorHlc: Int = getColumnIndexOrThrow(_stmt, "color_hlc")
        val _columnIndexOfCollapsed: Int = getColumnIndexOrThrow(_stmt, "collapsed")
        val _columnIndexOfCollapsedHlc: Int = getColumnIndexOrThrow(_stmt, "collapsed_hlc")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfDeletedHlc: Int = getColumnIndexOrThrow(_stmt, "deleted_hlc")
        val _columnIndexOfDeviceId: Int = getColumnIndexOrThrow(_stmt, "device_id")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: NodeEntity?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpDocumentId: String
          _tmpDocumentId = _stmt.getText(_columnIndexOfDocumentId)
          val _tmpUserId: String
          _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpContentHlc: String
          _tmpContentHlc = _stmt.getText(_columnIndexOfContentHlc)
          val _tmpNote: String
          _tmpNote = _stmt.getText(_columnIndexOfNote)
          val _tmpNoteHlc: String
          _tmpNoteHlc = _stmt.getText(_columnIndexOfNoteHlc)
          val _tmpParentId: String?
          if (_stmt.isNull(_columnIndexOfParentId)) {
            _tmpParentId = null
          } else {
            _tmpParentId = _stmt.getText(_columnIndexOfParentId)
          }
          val _tmpParentIdHlc: String
          _tmpParentIdHlc = _stmt.getText(_columnIndexOfParentIdHlc)
          val _tmpSortOrder: String
          _tmpSortOrder = _stmt.getText(_columnIndexOfSortOrder)
          val _tmpSortOrderHlc: String
          _tmpSortOrderHlc = _stmt.getText(_columnIndexOfSortOrderHlc)
          val _tmpCompleted: Int
          _tmpCompleted = _stmt.getLong(_columnIndexOfCompleted).toInt()
          val _tmpCompletedHlc: String
          _tmpCompletedHlc = _stmt.getText(_columnIndexOfCompletedHlc)
          val _tmpColor: Int
          _tmpColor = _stmt.getLong(_columnIndexOfColor).toInt()
          val _tmpColorHlc: String
          _tmpColorHlc = _stmt.getText(_columnIndexOfColorHlc)
          val _tmpCollapsed: Int
          _tmpCollapsed = _stmt.getLong(_columnIndexOfCollapsed).toInt()
          val _tmpCollapsedHlc: String
          _tmpCollapsedHlc = _stmt.getText(_columnIndexOfCollapsedHlc)
          val _tmpDeletedAt: Long?
          if (_stmt.isNull(_columnIndexOfDeletedAt)) {
            _tmpDeletedAt = null
          } else {
            _tmpDeletedAt = _stmt.getLong(_columnIndexOfDeletedAt)
          }
          val _tmpDeletedHlc: String
          _tmpDeletedHlc = _stmt.getText(_columnIndexOfDeletedHlc)
          val _tmpDeviceId: String
          _tmpDeviceId = _stmt.getText(_columnIndexOfDeviceId)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _result = NodeEntity(_tmpId,_tmpDocumentId,_tmpUserId,_tmpContent,_tmpContentHlc,_tmpNote,_tmpNoteHlc,_tmpParentId,_tmpParentIdHlc,_tmpSortOrder,_tmpSortOrderHlc,_tmpCompleted,_tmpCompletedHlc,_tmpColor,_tmpColorHlc,_tmpCollapsed,_tmpCollapsedHlc,_tmpDeletedAt,_tmpDeletedHlc,_tmpDeviceId,_tmpCreatedAt,_tmpUpdatedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getNodesByDocumentSync(documentId: String): List<NodeEntity> {
    val _sql: String = "SELECT * FROM nodes WHERE document_id = ? AND deleted_at IS NULL"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, documentId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfDocumentId: Int = getColumnIndexOrThrow(_stmt, "document_id")
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "user_id")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfContentHlc: Int = getColumnIndexOrThrow(_stmt, "content_hlc")
        val _columnIndexOfNote: Int = getColumnIndexOrThrow(_stmt, "note")
        val _columnIndexOfNoteHlc: Int = getColumnIndexOrThrow(_stmt, "note_hlc")
        val _columnIndexOfParentId: Int = getColumnIndexOrThrow(_stmt, "parent_id")
        val _columnIndexOfParentIdHlc: Int = getColumnIndexOrThrow(_stmt, "parent_id_hlc")
        val _columnIndexOfSortOrder: Int = getColumnIndexOrThrow(_stmt, "sort_order")
        val _columnIndexOfSortOrderHlc: Int = getColumnIndexOrThrow(_stmt, "sort_order_hlc")
        val _columnIndexOfCompleted: Int = getColumnIndexOrThrow(_stmt, "completed")
        val _columnIndexOfCompletedHlc: Int = getColumnIndexOrThrow(_stmt, "completed_hlc")
        val _columnIndexOfColor: Int = getColumnIndexOrThrow(_stmt, "color")
        val _columnIndexOfColorHlc: Int = getColumnIndexOrThrow(_stmt, "color_hlc")
        val _columnIndexOfCollapsed: Int = getColumnIndexOrThrow(_stmt, "collapsed")
        val _columnIndexOfCollapsedHlc: Int = getColumnIndexOrThrow(_stmt, "collapsed_hlc")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfDeletedHlc: Int = getColumnIndexOrThrow(_stmt, "deleted_hlc")
        val _columnIndexOfDeviceId: Int = getColumnIndexOrThrow(_stmt, "device_id")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: MutableList<NodeEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: NodeEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpDocumentId: String
          _tmpDocumentId = _stmt.getText(_columnIndexOfDocumentId)
          val _tmpUserId: String
          _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpContentHlc: String
          _tmpContentHlc = _stmt.getText(_columnIndexOfContentHlc)
          val _tmpNote: String
          _tmpNote = _stmt.getText(_columnIndexOfNote)
          val _tmpNoteHlc: String
          _tmpNoteHlc = _stmt.getText(_columnIndexOfNoteHlc)
          val _tmpParentId: String?
          if (_stmt.isNull(_columnIndexOfParentId)) {
            _tmpParentId = null
          } else {
            _tmpParentId = _stmt.getText(_columnIndexOfParentId)
          }
          val _tmpParentIdHlc: String
          _tmpParentIdHlc = _stmt.getText(_columnIndexOfParentIdHlc)
          val _tmpSortOrder: String
          _tmpSortOrder = _stmt.getText(_columnIndexOfSortOrder)
          val _tmpSortOrderHlc: String
          _tmpSortOrderHlc = _stmt.getText(_columnIndexOfSortOrderHlc)
          val _tmpCompleted: Int
          _tmpCompleted = _stmt.getLong(_columnIndexOfCompleted).toInt()
          val _tmpCompletedHlc: String
          _tmpCompletedHlc = _stmt.getText(_columnIndexOfCompletedHlc)
          val _tmpColor: Int
          _tmpColor = _stmt.getLong(_columnIndexOfColor).toInt()
          val _tmpColorHlc: String
          _tmpColorHlc = _stmt.getText(_columnIndexOfColorHlc)
          val _tmpCollapsed: Int
          _tmpCollapsed = _stmt.getLong(_columnIndexOfCollapsed).toInt()
          val _tmpCollapsedHlc: String
          _tmpCollapsedHlc = _stmt.getText(_columnIndexOfCollapsedHlc)
          val _tmpDeletedAt: Long?
          if (_stmt.isNull(_columnIndexOfDeletedAt)) {
            _tmpDeletedAt = null
          } else {
            _tmpDeletedAt = _stmt.getLong(_columnIndexOfDeletedAt)
          }
          val _tmpDeletedHlc: String
          _tmpDeletedHlc = _stmt.getText(_columnIndexOfDeletedHlc)
          val _tmpDeviceId: String
          _tmpDeviceId = _stmt.getText(_columnIndexOfDeviceId)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _item = NodeEntity(_tmpId,_tmpDocumentId,_tmpUserId,_tmpContent,_tmpContentHlc,_tmpNote,_tmpNoteHlc,_tmpParentId,_tmpParentIdHlc,_tmpSortOrder,_tmpSortOrderHlc,_tmpCompleted,_tmpCompletedHlc,_tmpColor,_tmpColorHlc,_tmpCollapsed,_tmpCollapsedHlc,_tmpDeletedAt,_tmpDeletedHlc,_tmpDeviceId,_tmpCreatedAt,_tmpUpdatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getPendingChanges(sinceHlc: String, deviceId: String): List<NodeEntity> {
    val _sql: String = """
        |
        |        SELECT * FROM nodes
        |        WHERE device_id = ?
        |        AND (content_hlc > ?
        |          OR note_hlc > ?
        |          OR parent_id_hlc > ?
        |          OR sort_order_hlc > ?
        |          OR completed_hlc > ?
        |          OR color_hlc > ?
        |          OR collapsed_hlc > ?
        |          OR deleted_hlc > ?)
        |    
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, deviceId)
        _argIndex = 2
        _stmt.bindText(_argIndex, sinceHlc)
        _argIndex = 3
        _stmt.bindText(_argIndex, sinceHlc)
        _argIndex = 4
        _stmt.bindText(_argIndex, sinceHlc)
        _argIndex = 5
        _stmt.bindText(_argIndex, sinceHlc)
        _argIndex = 6
        _stmt.bindText(_argIndex, sinceHlc)
        _argIndex = 7
        _stmt.bindText(_argIndex, sinceHlc)
        _argIndex = 8
        _stmt.bindText(_argIndex, sinceHlc)
        _argIndex = 9
        _stmt.bindText(_argIndex, sinceHlc)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfDocumentId: Int = getColumnIndexOrThrow(_stmt, "document_id")
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "user_id")
        val _columnIndexOfContent: Int = getColumnIndexOrThrow(_stmt, "content")
        val _columnIndexOfContentHlc: Int = getColumnIndexOrThrow(_stmt, "content_hlc")
        val _columnIndexOfNote: Int = getColumnIndexOrThrow(_stmt, "note")
        val _columnIndexOfNoteHlc: Int = getColumnIndexOrThrow(_stmt, "note_hlc")
        val _columnIndexOfParentId: Int = getColumnIndexOrThrow(_stmt, "parent_id")
        val _columnIndexOfParentIdHlc: Int = getColumnIndexOrThrow(_stmt, "parent_id_hlc")
        val _columnIndexOfSortOrder: Int = getColumnIndexOrThrow(_stmt, "sort_order")
        val _columnIndexOfSortOrderHlc: Int = getColumnIndexOrThrow(_stmt, "sort_order_hlc")
        val _columnIndexOfCompleted: Int = getColumnIndexOrThrow(_stmt, "completed")
        val _columnIndexOfCompletedHlc: Int = getColumnIndexOrThrow(_stmt, "completed_hlc")
        val _columnIndexOfColor: Int = getColumnIndexOrThrow(_stmt, "color")
        val _columnIndexOfColorHlc: Int = getColumnIndexOrThrow(_stmt, "color_hlc")
        val _columnIndexOfCollapsed: Int = getColumnIndexOrThrow(_stmt, "collapsed")
        val _columnIndexOfCollapsedHlc: Int = getColumnIndexOrThrow(_stmt, "collapsed_hlc")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfDeletedHlc: Int = getColumnIndexOrThrow(_stmt, "deleted_hlc")
        val _columnIndexOfDeviceId: Int = getColumnIndexOrThrow(_stmt, "device_id")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: MutableList<NodeEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: NodeEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpDocumentId: String
          _tmpDocumentId = _stmt.getText(_columnIndexOfDocumentId)
          val _tmpUserId: String
          _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          val _tmpContent: String
          _tmpContent = _stmt.getText(_columnIndexOfContent)
          val _tmpContentHlc: String
          _tmpContentHlc = _stmt.getText(_columnIndexOfContentHlc)
          val _tmpNote: String
          _tmpNote = _stmt.getText(_columnIndexOfNote)
          val _tmpNoteHlc: String
          _tmpNoteHlc = _stmt.getText(_columnIndexOfNoteHlc)
          val _tmpParentId: String?
          if (_stmt.isNull(_columnIndexOfParentId)) {
            _tmpParentId = null
          } else {
            _tmpParentId = _stmt.getText(_columnIndexOfParentId)
          }
          val _tmpParentIdHlc: String
          _tmpParentIdHlc = _stmt.getText(_columnIndexOfParentIdHlc)
          val _tmpSortOrder: String
          _tmpSortOrder = _stmt.getText(_columnIndexOfSortOrder)
          val _tmpSortOrderHlc: String
          _tmpSortOrderHlc = _stmt.getText(_columnIndexOfSortOrderHlc)
          val _tmpCompleted: Int
          _tmpCompleted = _stmt.getLong(_columnIndexOfCompleted).toInt()
          val _tmpCompletedHlc: String
          _tmpCompletedHlc = _stmt.getText(_columnIndexOfCompletedHlc)
          val _tmpColor: Int
          _tmpColor = _stmt.getLong(_columnIndexOfColor).toInt()
          val _tmpColorHlc: String
          _tmpColorHlc = _stmt.getText(_columnIndexOfColorHlc)
          val _tmpCollapsed: Int
          _tmpCollapsed = _stmt.getLong(_columnIndexOfCollapsed).toInt()
          val _tmpCollapsedHlc: String
          _tmpCollapsedHlc = _stmt.getText(_columnIndexOfCollapsedHlc)
          val _tmpDeletedAt: Long?
          if (_stmt.isNull(_columnIndexOfDeletedAt)) {
            _tmpDeletedAt = null
          } else {
            _tmpDeletedAt = _stmt.getLong(_columnIndexOfDeletedAt)
          }
          val _tmpDeletedHlc: String
          _tmpDeletedHlc = _stmt.getText(_columnIndexOfDeletedHlc)
          val _tmpDeviceId: String
          _tmpDeviceId = _stmt.getText(_columnIndexOfDeviceId)
          val _tmpCreatedAt: Long
          _tmpCreatedAt = _stmt.getLong(_columnIndexOfCreatedAt)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _item = NodeEntity(_tmpId,_tmpDocumentId,_tmpUserId,_tmpContent,_tmpContentHlc,_tmpNote,_tmpNoteHlc,_tmpParentId,_tmpParentIdHlc,_tmpSortOrder,_tmpSortOrderHlc,_tmpCompleted,_tmpCompletedHlc,_tmpColor,_tmpColorHlc,_tmpCollapsed,_tmpCollapsedHlc,_tmpDeletedAt,_tmpDeletedHlc,_tmpDeviceId,_tmpCreatedAt,_tmpUpdatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun softDeleteNode(
    nodeId: String,
    deletedAt: Long,
    deletedHlc: String,
    updatedAt: Long,
  ) {
    val _sql: String = "UPDATE nodes SET deleted_at = ?, deleted_hlc = ?, updated_at = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, deletedAt)
        _argIndex = 2
        _stmt.bindText(_argIndex, deletedHlc)
        _argIndex = 3
        _stmt.bindLong(_argIndex, updatedAt)
        _argIndex = 4
        _stmt.bindText(_argIndex, nodeId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun softDeleteNodes(
    nodeIds: List<String>,
    deletedAt: Long,
    deletedHlc: String,
    updatedAt: Long,
  ) {
    val _stringBuilder: StringBuilder = StringBuilder()
    _stringBuilder.append("UPDATE nodes SET deleted_at = ")
    _stringBuilder.append("?")
    _stringBuilder.append(", deleted_hlc = ")
    _stringBuilder.append("?")
    _stringBuilder.append(", updated_at = ")
    _stringBuilder.append("?")
    _stringBuilder.append(" WHERE id IN (")
    val _inputSize: Int = nodeIds.size
    appendPlaceholders(_stringBuilder, _inputSize)
    _stringBuilder.append(")")
    val _sql: String = _stringBuilder.toString()
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, deletedAt)
        _argIndex = 2
        _stmt.bindText(_argIndex, deletedHlc)
        _argIndex = 3
        _stmt.bindLong(_argIndex, updatedAt)
        _argIndex = 4
        for (_item: String in nodeIds) {
          _stmt.bindText(_argIndex, _item)
          _argIndex++
        }
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun searchFts(query: SupportSQLiteQuery): List<NodeEntity> {
    val _rawQuery: RoomRawQuery = RoomSQLiteQuery.copyFrom(query).toRoomRawQuery()
    val _sql: String = _rawQuery.sql
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _rawQuery.getBindingFunction().invoke(_stmt)
        val _result: MutableList<NodeEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: NodeEntity
          _item = __entityStatementConverter_comGmaingretOutlinergodDbEntityNodeEntity(_stmt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  private fun __entityStatementConverter_comGmaingretOutlinergodDbEntityNodeEntity(statement: SQLiteStatement): NodeEntity {
    val _entity: NodeEntity
    val _columnIndexOfId: Int = getColumnIndex(statement, "id")
    val _columnIndexOfDocumentId: Int = getColumnIndex(statement, "document_id")
    val _columnIndexOfUserId: Int = getColumnIndex(statement, "user_id")
    val _columnIndexOfContent: Int = getColumnIndex(statement, "content")
    val _columnIndexOfContentHlc: Int = getColumnIndex(statement, "content_hlc")
    val _columnIndexOfNote: Int = getColumnIndex(statement, "note")
    val _columnIndexOfNoteHlc: Int = getColumnIndex(statement, "note_hlc")
    val _columnIndexOfParentId: Int = getColumnIndex(statement, "parent_id")
    val _columnIndexOfParentIdHlc: Int = getColumnIndex(statement, "parent_id_hlc")
    val _columnIndexOfSortOrder: Int = getColumnIndex(statement, "sort_order")
    val _columnIndexOfSortOrderHlc: Int = getColumnIndex(statement, "sort_order_hlc")
    val _columnIndexOfCompleted: Int = getColumnIndex(statement, "completed")
    val _columnIndexOfCompletedHlc: Int = getColumnIndex(statement, "completed_hlc")
    val _columnIndexOfColor: Int = getColumnIndex(statement, "color")
    val _columnIndexOfColorHlc: Int = getColumnIndex(statement, "color_hlc")
    val _columnIndexOfCollapsed: Int = getColumnIndex(statement, "collapsed")
    val _columnIndexOfCollapsedHlc: Int = getColumnIndex(statement, "collapsed_hlc")
    val _columnIndexOfDeletedAt: Int = getColumnIndex(statement, "deleted_at")
    val _columnIndexOfDeletedHlc: Int = getColumnIndex(statement, "deleted_hlc")
    val _columnIndexOfDeviceId: Int = getColumnIndex(statement, "device_id")
    val _columnIndexOfCreatedAt: Int = getColumnIndex(statement, "created_at")
    val _columnIndexOfUpdatedAt: Int = getColumnIndex(statement, "updated_at")
    val _tmpId: String
    if (_columnIndexOfId == -1) {
      error("Missing column 'id' for a NON-NULL value, column not found in result.")
    } else {
      _tmpId = statement.getText(_columnIndexOfId)
    }
    val _tmpDocumentId: String
    if (_columnIndexOfDocumentId == -1) {
      error("Missing column 'document_id' for a NON-NULL value, column not found in result.")
    } else {
      _tmpDocumentId = statement.getText(_columnIndexOfDocumentId)
    }
    val _tmpUserId: String
    if (_columnIndexOfUserId == -1) {
      error("Missing column 'user_id' for a NON-NULL value, column not found in result.")
    } else {
      _tmpUserId = statement.getText(_columnIndexOfUserId)
    }
    val _tmpContent: String
    if (_columnIndexOfContent == -1) {
      error("Missing column 'content' for a NON-NULL value, column not found in result.")
    } else {
      _tmpContent = statement.getText(_columnIndexOfContent)
    }
    val _tmpContentHlc: String
    if (_columnIndexOfContentHlc == -1) {
      error("Missing column 'content_hlc' for a NON-NULL value, column not found in result.")
    } else {
      _tmpContentHlc = statement.getText(_columnIndexOfContentHlc)
    }
    val _tmpNote: String
    if (_columnIndexOfNote == -1) {
      error("Missing column 'note' for a NON-NULL value, column not found in result.")
    } else {
      _tmpNote = statement.getText(_columnIndexOfNote)
    }
    val _tmpNoteHlc: String
    if (_columnIndexOfNoteHlc == -1) {
      error("Missing column 'note_hlc' for a NON-NULL value, column not found in result.")
    } else {
      _tmpNoteHlc = statement.getText(_columnIndexOfNoteHlc)
    }
    val _tmpParentId: String?
    if (_columnIndexOfParentId == -1) {
      _tmpParentId = null
    } else {
      if (statement.isNull(_columnIndexOfParentId)) {
        _tmpParentId = null
      } else {
        _tmpParentId = statement.getText(_columnIndexOfParentId)
      }
    }
    val _tmpParentIdHlc: String
    if (_columnIndexOfParentIdHlc == -1) {
      error("Missing column 'parent_id_hlc' for a NON-NULL value, column not found in result.")
    } else {
      _tmpParentIdHlc = statement.getText(_columnIndexOfParentIdHlc)
    }
    val _tmpSortOrder: String
    if (_columnIndexOfSortOrder == -1) {
      error("Missing column 'sort_order' for a NON-NULL value, column not found in result.")
    } else {
      _tmpSortOrder = statement.getText(_columnIndexOfSortOrder)
    }
    val _tmpSortOrderHlc: String
    if (_columnIndexOfSortOrderHlc == -1) {
      error("Missing column 'sort_order_hlc' for a NON-NULL value, column not found in result.")
    } else {
      _tmpSortOrderHlc = statement.getText(_columnIndexOfSortOrderHlc)
    }
    val _tmpCompleted: Int
    if (_columnIndexOfCompleted == -1) {
      _tmpCompleted = 0
    } else {
      _tmpCompleted = statement.getLong(_columnIndexOfCompleted).toInt()
    }
    val _tmpCompletedHlc: String
    if (_columnIndexOfCompletedHlc == -1) {
      error("Missing column 'completed_hlc' for a NON-NULL value, column not found in result.")
    } else {
      _tmpCompletedHlc = statement.getText(_columnIndexOfCompletedHlc)
    }
    val _tmpColor: Int
    if (_columnIndexOfColor == -1) {
      _tmpColor = 0
    } else {
      _tmpColor = statement.getLong(_columnIndexOfColor).toInt()
    }
    val _tmpColorHlc: String
    if (_columnIndexOfColorHlc == -1) {
      error("Missing column 'color_hlc' for a NON-NULL value, column not found in result.")
    } else {
      _tmpColorHlc = statement.getText(_columnIndexOfColorHlc)
    }
    val _tmpCollapsed: Int
    if (_columnIndexOfCollapsed == -1) {
      _tmpCollapsed = 0
    } else {
      _tmpCollapsed = statement.getLong(_columnIndexOfCollapsed).toInt()
    }
    val _tmpCollapsedHlc: String
    if (_columnIndexOfCollapsedHlc == -1) {
      error("Missing column 'collapsed_hlc' for a NON-NULL value, column not found in result.")
    } else {
      _tmpCollapsedHlc = statement.getText(_columnIndexOfCollapsedHlc)
    }
    val _tmpDeletedAt: Long?
    if (_columnIndexOfDeletedAt == -1) {
      _tmpDeletedAt = null
    } else {
      if (statement.isNull(_columnIndexOfDeletedAt)) {
        _tmpDeletedAt = null
      } else {
        _tmpDeletedAt = statement.getLong(_columnIndexOfDeletedAt)
      }
    }
    val _tmpDeletedHlc: String
    if (_columnIndexOfDeletedHlc == -1) {
      error("Missing column 'deleted_hlc' for a NON-NULL value, column not found in result.")
    } else {
      _tmpDeletedHlc = statement.getText(_columnIndexOfDeletedHlc)
    }
    val _tmpDeviceId: String
    if (_columnIndexOfDeviceId == -1) {
      error("Missing column 'device_id' for a NON-NULL value, column not found in result.")
    } else {
      _tmpDeviceId = statement.getText(_columnIndexOfDeviceId)
    }
    val _tmpCreatedAt: Long
    if (_columnIndexOfCreatedAt == -1) {
      _tmpCreatedAt = 0
    } else {
      _tmpCreatedAt = statement.getLong(_columnIndexOfCreatedAt)
    }
    val _tmpUpdatedAt: Long
    if (_columnIndexOfUpdatedAt == -1) {
      _tmpUpdatedAt = 0
    } else {
      _tmpUpdatedAt = statement.getLong(_columnIndexOfUpdatedAt)
    }
    _entity = NodeEntity(_tmpId,_tmpDocumentId,_tmpUserId,_tmpContent,_tmpContentHlc,_tmpNote,_tmpNoteHlc,_tmpParentId,_tmpParentIdHlc,_tmpSortOrder,_tmpSortOrderHlc,_tmpCompleted,_tmpCompletedHlc,_tmpColor,_tmpColorHlc,_tmpCollapsed,_tmpCollapsedHlc,_tmpDeletedAt,_tmpDeletedHlc,_tmpDeviceId,_tmpCreatedAt,_tmpUpdatedAt)
    return _entity
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}

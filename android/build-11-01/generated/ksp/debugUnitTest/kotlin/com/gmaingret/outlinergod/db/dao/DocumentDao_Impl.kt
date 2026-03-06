package com.gmaingret.outlinergod.db.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.gmaingret.outlinergod.db.entity.DocumentEntity
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
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class DocumentDao_Impl(
  __db: RoomDatabase,
) : DocumentDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfDocumentEntity: EntityInsertAdapter<DocumentEntity>

  private val __updateAdapterOfDocumentEntity: EntityDeleteOrUpdateAdapter<DocumentEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfDocumentEntity = object : EntityInsertAdapter<DocumentEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `documents` (`id`,`user_id`,`title`,`title_hlc`,`type`,`parent_id`,`parent_id_hlc`,`sort_order`,`sort_order_hlc`,`collapsed`,`collapsed_hlc`,`deleted_at`,`deleted_hlc`,`device_id`,`created_at`,`updated_at`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: DocumentEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.userId)
        statement.bindText(3, entity.title)
        statement.bindText(4, entity.titleHlc)
        statement.bindText(5, entity.type)
        val _tmpParentId: String? = entity.parentId
        if (_tmpParentId == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpParentId)
        }
        statement.bindText(7, entity.parentIdHlc)
        statement.bindText(8, entity.sortOrder)
        statement.bindText(9, entity.sortOrderHlc)
        statement.bindLong(10, entity.collapsed.toLong())
        statement.bindText(11, entity.collapsedHlc)
        val _tmpDeletedAt: Long? = entity.deletedAt
        if (_tmpDeletedAt == null) {
          statement.bindNull(12)
        } else {
          statement.bindLong(12, _tmpDeletedAt)
        }
        statement.bindText(13, entity.deletedHlc)
        statement.bindText(14, entity.deviceId)
        statement.bindLong(15, entity.createdAt)
        statement.bindLong(16, entity.updatedAt)
      }
    }
    this.__updateAdapterOfDocumentEntity = object : EntityDeleteOrUpdateAdapter<DocumentEntity>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `documents` SET `id` = ?,`user_id` = ?,`title` = ?,`title_hlc` = ?,`type` = ?,`parent_id` = ?,`parent_id_hlc` = ?,`sort_order` = ?,`sort_order_hlc` = ?,`collapsed` = ?,`collapsed_hlc` = ?,`deleted_at` = ?,`deleted_hlc` = ?,`device_id` = ?,`created_at` = ?,`updated_at` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: DocumentEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.userId)
        statement.bindText(3, entity.title)
        statement.bindText(4, entity.titleHlc)
        statement.bindText(5, entity.type)
        val _tmpParentId: String? = entity.parentId
        if (_tmpParentId == null) {
          statement.bindNull(6)
        } else {
          statement.bindText(6, _tmpParentId)
        }
        statement.bindText(7, entity.parentIdHlc)
        statement.bindText(8, entity.sortOrder)
        statement.bindText(9, entity.sortOrderHlc)
        statement.bindLong(10, entity.collapsed.toLong())
        statement.bindText(11, entity.collapsedHlc)
        val _tmpDeletedAt: Long? = entity.deletedAt
        if (_tmpDeletedAt == null) {
          statement.bindNull(12)
        } else {
          statement.bindLong(12, _tmpDeletedAt)
        }
        statement.bindText(13, entity.deletedHlc)
        statement.bindText(14, entity.deviceId)
        statement.bindLong(15, entity.createdAt)
        statement.bindLong(16, entity.updatedAt)
        statement.bindText(17, entity.id)
      }
    }
  }

  public override suspend fun insertDocument(document: DocumentEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfDocumentEntity.insert(_connection, document)
  }

  public override suspend fun upsertDocuments(documents: List<DocumentEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfDocumentEntity.insert(_connection, documents)
  }

  public override suspend fun updateDocument(document: DocumentEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfDocumentEntity.handle(_connection, document)
  }

  public override fun getAllDocuments(userId: String): Flow<List<DocumentEntity>> {
    val _sql: String = "SELECT * FROM documents WHERE user_id = ? AND deleted_at IS NULL ORDER BY sort_order ASC, id ASC"
    return createFlow(__db, false, arrayOf("documents")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, userId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "user_id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfTitleHlc: Int = getColumnIndexOrThrow(_stmt, "title_hlc")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfParentId: Int = getColumnIndexOrThrow(_stmt, "parent_id")
        val _columnIndexOfParentIdHlc: Int = getColumnIndexOrThrow(_stmt, "parent_id_hlc")
        val _columnIndexOfSortOrder: Int = getColumnIndexOrThrow(_stmt, "sort_order")
        val _columnIndexOfSortOrderHlc: Int = getColumnIndexOrThrow(_stmt, "sort_order_hlc")
        val _columnIndexOfCollapsed: Int = getColumnIndexOrThrow(_stmt, "collapsed")
        val _columnIndexOfCollapsedHlc: Int = getColumnIndexOrThrow(_stmt, "collapsed_hlc")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfDeletedHlc: Int = getColumnIndexOrThrow(_stmt, "deleted_hlc")
        val _columnIndexOfDeviceId: Int = getColumnIndexOrThrow(_stmt, "device_id")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: MutableList<DocumentEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: DocumentEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpUserId: String
          _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpTitleHlc: String
          _tmpTitleHlc = _stmt.getText(_columnIndexOfTitleHlc)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
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
          _item = DocumentEntity(_tmpId,_tmpUserId,_tmpTitle,_tmpTitleHlc,_tmpType,_tmpParentId,_tmpParentIdHlc,_tmpSortOrder,_tmpSortOrderHlc,_tmpCollapsed,_tmpCollapsedHlc,_tmpDeletedAt,_tmpDeletedHlc,_tmpDeviceId,_tmpCreatedAt,_tmpUpdatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getDocumentById(id: String): Flow<DocumentEntity?> {
    val _sql: String = "SELECT * FROM documents WHERE id = ? LIMIT 1"
    return createFlow(__db, false, arrayOf("documents")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "user_id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfTitleHlc: Int = getColumnIndexOrThrow(_stmt, "title_hlc")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfParentId: Int = getColumnIndexOrThrow(_stmt, "parent_id")
        val _columnIndexOfParentIdHlc: Int = getColumnIndexOrThrow(_stmt, "parent_id_hlc")
        val _columnIndexOfSortOrder: Int = getColumnIndexOrThrow(_stmt, "sort_order")
        val _columnIndexOfSortOrderHlc: Int = getColumnIndexOrThrow(_stmt, "sort_order_hlc")
        val _columnIndexOfCollapsed: Int = getColumnIndexOrThrow(_stmt, "collapsed")
        val _columnIndexOfCollapsedHlc: Int = getColumnIndexOrThrow(_stmt, "collapsed_hlc")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfDeletedHlc: Int = getColumnIndexOrThrow(_stmt, "deleted_hlc")
        val _columnIndexOfDeviceId: Int = getColumnIndexOrThrow(_stmt, "device_id")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: DocumentEntity?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpUserId: String
          _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpTitleHlc: String
          _tmpTitleHlc = _stmt.getText(_columnIndexOfTitleHlc)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
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
          _result = DocumentEntity(_tmpId,_tmpUserId,_tmpTitle,_tmpTitleHlc,_tmpType,_tmpParentId,_tmpParentIdHlc,_tmpSortOrder,_tmpSortOrderHlc,_tmpCollapsed,_tmpCollapsedHlc,_tmpDeletedAt,_tmpDeletedHlc,_tmpDeviceId,_tmpCreatedAt,_tmpUpdatedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getDocumentByIdSync(id: String): DocumentEntity? {
    val _sql: String = "SELECT * FROM documents WHERE id = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "user_id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfTitleHlc: Int = getColumnIndexOrThrow(_stmt, "title_hlc")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfParentId: Int = getColumnIndexOrThrow(_stmt, "parent_id")
        val _columnIndexOfParentIdHlc: Int = getColumnIndexOrThrow(_stmt, "parent_id_hlc")
        val _columnIndexOfSortOrder: Int = getColumnIndexOrThrow(_stmt, "sort_order")
        val _columnIndexOfSortOrderHlc: Int = getColumnIndexOrThrow(_stmt, "sort_order_hlc")
        val _columnIndexOfCollapsed: Int = getColumnIndexOrThrow(_stmt, "collapsed")
        val _columnIndexOfCollapsedHlc: Int = getColumnIndexOrThrow(_stmt, "collapsed_hlc")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfDeletedHlc: Int = getColumnIndexOrThrow(_stmt, "deleted_hlc")
        val _columnIndexOfDeviceId: Int = getColumnIndexOrThrow(_stmt, "device_id")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: DocumentEntity?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpUserId: String
          _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpTitleHlc: String
          _tmpTitleHlc = _stmt.getText(_columnIndexOfTitleHlc)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
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
          _result = DocumentEntity(_tmpId,_tmpUserId,_tmpTitle,_tmpTitleHlc,_tmpType,_tmpParentId,_tmpParentIdHlc,_tmpSortOrder,_tmpSortOrderHlc,_tmpCollapsed,_tmpCollapsedHlc,_tmpDeletedAt,_tmpDeletedHlc,_tmpDeviceId,_tmpCreatedAt,_tmpUpdatedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getPendingChanges(
    userId: String,
    sinceHlc: String,
    deviceId: String,
  ): List<DocumentEntity> {
    val _sql: String = """
        |
        |        SELECT * FROM documents
        |        WHERE user_id = ?
        |        AND device_id = ?
        |        AND (title_hlc > ?
        |          OR parent_id_hlc > ?
        |          OR sort_order_hlc > ?
        |          OR collapsed_hlc > ?
        |          OR deleted_hlc > ?)
        |    
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, userId)
        _argIndex = 2
        _stmt.bindText(_argIndex, deviceId)
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
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "user_id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfTitleHlc: Int = getColumnIndexOrThrow(_stmt, "title_hlc")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfParentId: Int = getColumnIndexOrThrow(_stmt, "parent_id")
        val _columnIndexOfParentIdHlc: Int = getColumnIndexOrThrow(_stmt, "parent_id_hlc")
        val _columnIndexOfSortOrder: Int = getColumnIndexOrThrow(_stmt, "sort_order")
        val _columnIndexOfSortOrderHlc: Int = getColumnIndexOrThrow(_stmt, "sort_order_hlc")
        val _columnIndexOfCollapsed: Int = getColumnIndexOrThrow(_stmt, "collapsed")
        val _columnIndexOfCollapsedHlc: Int = getColumnIndexOrThrow(_stmt, "collapsed_hlc")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfDeletedHlc: Int = getColumnIndexOrThrow(_stmt, "deleted_hlc")
        val _columnIndexOfDeviceId: Int = getColumnIndexOrThrow(_stmt, "device_id")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: MutableList<DocumentEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: DocumentEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpUserId: String
          _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpTitleHlc: String
          _tmpTitleHlc = _stmt.getText(_columnIndexOfTitleHlc)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
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
          _item = DocumentEntity(_tmpId,_tmpUserId,_tmpTitle,_tmpTitleHlc,_tmpType,_tmpParentId,_tmpParentIdHlc,_tmpSortOrder,_tmpSortOrderHlc,_tmpCollapsed,_tmpCollapsedHlc,_tmpDeletedAt,_tmpDeletedHlc,_tmpDeviceId,_tmpCreatedAt,_tmpUpdatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun softDeleteDocument(
    id: String,
    deletedAt: Long,
    deletedHlc: String,
    updatedAt: Long,
  ) {
    val _sql: String = "UPDATE documents SET deleted_at = ?, deleted_hlc = ?, updated_at = ? WHERE id = ?"
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
        _stmt.bindText(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}

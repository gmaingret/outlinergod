package com.gmaingret.outlinergod.db.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.gmaingret.outlinergod.db.entity.BookmarkEntity
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
public class BookmarkDao_Impl(
  __db: RoomDatabase,
) : BookmarkDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfBookmarkEntity: EntityInsertAdapter<BookmarkEntity>

  private val __updateAdapterOfBookmarkEntity: EntityDeleteOrUpdateAdapter<BookmarkEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfBookmarkEntity = object : EntityInsertAdapter<BookmarkEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `bookmarks` (`id`,`user_id`,`title`,`title_hlc`,`target_type`,`target_type_hlc`,`target_document_id`,`target_document_id_hlc`,`target_node_id`,`target_node_id_hlc`,`query`,`query_hlc`,`sort_order`,`sort_order_hlc`,`deleted_at`,`deleted_hlc`,`device_id`,`created_at`,`updated_at`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: BookmarkEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.userId)
        statement.bindText(3, entity.title)
        statement.bindText(4, entity.titleHlc)
        statement.bindText(5, entity.targetType)
        statement.bindText(6, entity.targetTypeHlc)
        val _tmpTargetDocumentId: String? = entity.targetDocumentId
        if (_tmpTargetDocumentId == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpTargetDocumentId)
        }
        statement.bindText(8, entity.targetDocumentIdHlc)
        val _tmpTargetNodeId: String? = entity.targetNodeId
        if (_tmpTargetNodeId == null) {
          statement.bindNull(9)
        } else {
          statement.bindText(9, _tmpTargetNodeId)
        }
        statement.bindText(10, entity.targetNodeIdHlc)
        val _tmpQuery: String? = entity.query
        if (_tmpQuery == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpQuery)
        }
        statement.bindText(12, entity.queryHlc)
        statement.bindText(13, entity.sortOrder)
        statement.bindText(14, entity.sortOrderHlc)
        val _tmpDeletedAt: Long? = entity.deletedAt
        if (_tmpDeletedAt == null) {
          statement.bindNull(15)
        } else {
          statement.bindLong(15, _tmpDeletedAt)
        }
        statement.bindText(16, entity.deletedHlc)
        statement.bindText(17, entity.deviceId)
        statement.bindLong(18, entity.createdAt)
        statement.bindLong(19, entity.updatedAt)
      }
    }
    this.__updateAdapterOfBookmarkEntity = object : EntityDeleteOrUpdateAdapter<BookmarkEntity>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `bookmarks` SET `id` = ?,`user_id` = ?,`title` = ?,`title_hlc` = ?,`target_type` = ?,`target_type_hlc` = ?,`target_document_id` = ?,`target_document_id_hlc` = ?,`target_node_id` = ?,`target_node_id_hlc` = ?,`query` = ?,`query_hlc` = ?,`sort_order` = ?,`sort_order_hlc` = ?,`deleted_at` = ?,`deleted_hlc` = ?,`device_id` = ?,`created_at` = ?,`updated_at` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: BookmarkEntity) {
        statement.bindText(1, entity.id)
        statement.bindText(2, entity.userId)
        statement.bindText(3, entity.title)
        statement.bindText(4, entity.titleHlc)
        statement.bindText(5, entity.targetType)
        statement.bindText(6, entity.targetTypeHlc)
        val _tmpTargetDocumentId: String? = entity.targetDocumentId
        if (_tmpTargetDocumentId == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpTargetDocumentId)
        }
        statement.bindText(8, entity.targetDocumentIdHlc)
        val _tmpTargetNodeId: String? = entity.targetNodeId
        if (_tmpTargetNodeId == null) {
          statement.bindNull(9)
        } else {
          statement.bindText(9, _tmpTargetNodeId)
        }
        statement.bindText(10, entity.targetNodeIdHlc)
        val _tmpQuery: String? = entity.query
        if (_tmpQuery == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpQuery)
        }
        statement.bindText(12, entity.queryHlc)
        statement.bindText(13, entity.sortOrder)
        statement.bindText(14, entity.sortOrderHlc)
        val _tmpDeletedAt: Long? = entity.deletedAt
        if (_tmpDeletedAt == null) {
          statement.bindNull(15)
        } else {
          statement.bindLong(15, _tmpDeletedAt)
        }
        statement.bindText(16, entity.deletedHlc)
        statement.bindText(17, entity.deviceId)
        statement.bindLong(18, entity.createdAt)
        statement.bindLong(19, entity.updatedAt)
        statement.bindText(20, entity.id)
      }
    }
  }

  public override suspend fun insertBookmark(bookmark: BookmarkEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfBookmarkEntity.insert(_connection, bookmark)
  }

  public override suspend fun upsertBookmarks(bookmarks: List<BookmarkEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfBookmarkEntity.insert(_connection, bookmarks)
  }

  public override suspend fun updateBookmark(bookmark: BookmarkEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfBookmarkEntity.handle(_connection, bookmark)
  }

  public override fun observeAllActive(userId: String): Flow<List<BookmarkEntity>> {
    val _sql: String = "SELECT * FROM bookmarks WHERE user_id = ? AND deleted_at IS NULL ORDER BY sort_order ASC, id ASC"
    return createFlow(__db, false, arrayOf("bookmarks")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, userId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "user_id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfTitleHlc: Int = getColumnIndexOrThrow(_stmt, "title_hlc")
        val _columnIndexOfTargetType: Int = getColumnIndexOrThrow(_stmt, "target_type")
        val _columnIndexOfTargetTypeHlc: Int = getColumnIndexOrThrow(_stmt, "target_type_hlc")
        val _columnIndexOfTargetDocumentId: Int = getColumnIndexOrThrow(_stmt, "target_document_id")
        val _columnIndexOfTargetDocumentIdHlc: Int = getColumnIndexOrThrow(_stmt, "target_document_id_hlc")
        val _columnIndexOfTargetNodeId: Int = getColumnIndexOrThrow(_stmt, "target_node_id")
        val _columnIndexOfTargetNodeIdHlc: Int = getColumnIndexOrThrow(_stmt, "target_node_id_hlc")
        val _columnIndexOfQuery: Int = getColumnIndexOrThrow(_stmt, "query")
        val _columnIndexOfQueryHlc: Int = getColumnIndexOrThrow(_stmt, "query_hlc")
        val _columnIndexOfSortOrder: Int = getColumnIndexOrThrow(_stmt, "sort_order")
        val _columnIndexOfSortOrderHlc: Int = getColumnIndexOrThrow(_stmt, "sort_order_hlc")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfDeletedHlc: Int = getColumnIndexOrThrow(_stmt, "deleted_hlc")
        val _columnIndexOfDeviceId: Int = getColumnIndexOrThrow(_stmt, "device_id")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: MutableList<BookmarkEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: BookmarkEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpUserId: String
          _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpTitleHlc: String
          _tmpTitleHlc = _stmt.getText(_columnIndexOfTitleHlc)
          val _tmpTargetType: String
          _tmpTargetType = _stmt.getText(_columnIndexOfTargetType)
          val _tmpTargetTypeHlc: String
          _tmpTargetTypeHlc = _stmt.getText(_columnIndexOfTargetTypeHlc)
          val _tmpTargetDocumentId: String?
          if (_stmt.isNull(_columnIndexOfTargetDocumentId)) {
            _tmpTargetDocumentId = null
          } else {
            _tmpTargetDocumentId = _stmt.getText(_columnIndexOfTargetDocumentId)
          }
          val _tmpTargetDocumentIdHlc: String
          _tmpTargetDocumentIdHlc = _stmt.getText(_columnIndexOfTargetDocumentIdHlc)
          val _tmpTargetNodeId: String?
          if (_stmt.isNull(_columnIndexOfTargetNodeId)) {
            _tmpTargetNodeId = null
          } else {
            _tmpTargetNodeId = _stmt.getText(_columnIndexOfTargetNodeId)
          }
          val _tmpTargetNodeIdHlc: String
          _tmpTargetNodeIdHlc = _stmt.getText(_columnIndexOfTargetNodeIdHlc)
          val _tmpQuery: String?
          if (_stmt.isNull(_columnIndexOfQuery)) {
            _tmpQuery = null
          } else {
            _tmpQuery = _stmt.getText(_columnIndexOfQuery)
          }
          val _tmpQueryHlc: String
          _tmpQueryHlc = _stmt.getText(_columnIndexOfQueryHlc)
          val _tmpSortOrder: String
          _tmpSortOrder = _stmt.getText(_columnIndexOfSortOrder)
          val _tmpSortOrderHlc: String
          _tmpSortOrderHlc = _stmt.getText(_columnIndexOfSortOrderHlc)
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
          _item = BookmarkEntity(_tmpId,_tmpUserId,_tmpTitle,_tmpTitleHlc,_tmpTargetType,_tmpTargetTypeHlc,_tmpTargetDocumentId,_tmpTargetDocumentIdHlc,_tmpTargetNodeId,_tmpTargetNodeIdHlc,_tmpQuery,_tmpQueryHlc,_tmpSortOrder,_tmpSortOrderHlc,_tmpDeletedAt,_tmpDeletedHlc,_tmpDeviceId,_tmpCreatedAt,_tmpUpdatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getBookmarkByIdSync(id: String): BookmarkEntity? {
    val _sql: String = "SELECT * FROM bookmarks WHERE id = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "user_id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfTitleHlc: Int = getColumnIndexOrThrow(_stmt, "title_hlc")
        val _columnIndexOfTargetType: Int = getColumnIndexOrThrow(_stmt, "target_type")
        val _columnIndexOfTargetTypeHlc: Int = getColumnIndexOrThrow(_stmt, "target_type_hlc")
        val _columnIndexOfTargetDocumentId: Int = getColumnIndexOrThrow(_stmt, "target_document_id")
        val _columnIndexOfTargetDocumentIdHlc: Int = getColumnIndexOrThrow(_stmt, "target_document_id_hlc")
        val _columnIndexOfTargetNodeId: Int = getColumnIndexOrThrow(_stmt, "target_node_id")
        val _columnIndexOfTargetNodeIdHlc: Int = getColumnIndexOrThrow(_stmt, "target_node_id_hlc")
        val _columnIndexOfQuery: Int = getColumnIndexOrThrow(_stmt, "query")
        val _columnIndexOfQueryHlc: Int = getColumnIndexOrThrow(_stmt, "query_hlc")
        val _columnIndexOfSortOrder: Int = getColumnIndexOrThrow(_stmt, "sort_order")
        val _columnIndexOfSortOrderHlc: Int = getColumnIndexOrThrow(_stmt, "sort_order_hlc")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfDeletedHlc: Int = getColumnIndexOrThrow(_stmt, "deleted_hlc")
        val _columnIndexOfDeviceId: Int = getColumnIndexOrThrow(_stmt, "device_id")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: BookmarkEntity?
        if (_stmt.step()) {
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpUserId: String
          _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpTitleHlc: String
          _tmpTitleHlc = _stmt.getText(_columnIndexOfTitleHlc)
          val _tmpTargetType: String
          _tmpTargetType = _stmt.getText(_columnIndexOfTargetType)
          val _tmpTargetTypeHlc: String
          _tmpTargetTypeHlc = _stmt.getText(_columnIndexOfTargetTypeHlc)
          val _tmpTargetDocumentId: String?
          if (_stmt.isNull(_columnIndexOfTargetDocumentId)) {
            _tmpTargetDocumentId = null
          } else {
            _tmpTargetDocumentId = _stmt.getText(_columnIndexOfTargetDocumentId)
          }
          val _tmpTargetDocumentIdHlc: String
          _tmpTargetDocumentIdHlc = _stmt.getText(_columnIndexOfTargetDocumentIdHlc)
          val _tmpTargetNodeId: String?
          if (_stmt.isNull(_columnIndexOfTargetNodeId)) {
            _tmpTargetNodeId = null
          } else {
            _tmpTargetNodeId = _stmt.getText(_columnIndexOfTargetNodeId)
          }
          val _tmpTargetNodeIdHlc: String
          _tmpTargetNodeIdHlc = _stmt.getText(_columnIndexOfTargetNodeIdHlc)
          val _tmpQuery: String?
          if (_stmt.isNull(_columnIndexOfQuery)) {
            _tmpQuery = null
          } else {
            _tmpQuery = _stmt.getText(_columnIndexOfQuery)
          }
          val _tmpQueryHlc: String
          _tmpQueryHlc = _stmt.getText(_columnIndexOfQueryHlc)
          val _tmpSortOrder: String
          _tmpSortOrder = _stmt.getText(_columnIndexOfSortOrder)
          val _tmpSortOrderHlc: String
          _tmpSortOrderHlc = _stmt.getText(_columnIndexOfSortOrderHlc)
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
          _result = BookmarkEntity(_tmpId,_tmpUserId,_tmpTitle,_tmpTitleHlc,_tmpTargetType,_tmpTargetTypeHlc,_tmpTargetDocumentId,_tmpTargetDocumentIdHlc,_tmpTargetNodeId,_tmpTargetNodeIdHlc,_tmpQuery,_tmpQueryHlc,_tmpSortOrder,_tmpSortOrderHlc,_tmpDeletedAt,_tmpDeletedHlc,_tmpDeviceId,_tmpCreatedAt,_tmpUpdatedAt)
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
  ): List<BookmarkEntity> {
    val _sql: String = """
        |
        |        SELECT * FROM bookmarks
        |        WHERE user_id = ?
        |        AND device_id = ?
        |        AND (title_hlc > ?
        |          OR target_type_hlc > ?
        |          OR target_document_id_hlc > ?
        |          OR target_node_id_hlc > ?
        |          OR query_hlc > ?
        |          OR sort_order_hlc > ?
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
        _argIndex = 8
        _stmt.bindText(_argIndex, sinceHlc)
        _argIndex = 9
        _stmt.bindText(_argIndex, sinceHlc)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "user_id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfTitleHlc: Int = getColumnIndexOrThrow(_stmt, "title_hlc")
        val _columnIndexOfTargetType: Int = getColumnIndexOrThrow(_stmt, "target_type")
        val _columnIndexOfTargetTypeHlc: Int = getColumnIndexOrThrow(_stmt, "target_type_hlc")
        val _columnIndexOfTargetDocumentId: Int = getColumnIndexOrThrow(_stmt, "target_document_id")
        val _columnIndexOfTargetDocumentIdHlc: Int = getColumnIndexOrThrow(_stmt, "target_document_id_hlc")
        val _columnIndexOfTargetNodeId: Int = getColumnIndexOrThrow(_stmt, "target_node_id")
        val _columnIndexOfTargetNodeIdHlc: Int = getColumnIndexOrThrow(_stmt, "target_node_id_hlc")
        val _columnIndexOfQuery: Int = getColumnIndexOrThrow(_stmt, "query")
        val _columnIndexOfQueryHlc: Int = getColumnIndexOrThrow(_stmt, "query_hlc")
        val _columnIndexOfSortOrder: Int = getColumnIndexOrThrow(_stmt, "sort_order")
        val _columnIndexOfSortOrderHlc: Int = getColumnIndexOrThrow(_stmt, "sort_order_hlc")
        val _columnIndexOfDeletedAt: Int = getColumnIndexOrThrow(_stmt, "deleted_at")
        val _columnIndexOfDeletedHlc: Int = getColumnIndexOrThrow(_stmt, "deleted_hlc")
        val _columnIndexOfDeviceId: Int = getColumnIndexOrThrow(_stmt, "device_id")
        val _columnIndexOfCreatedAt: Int = getColumnIndexOrThrow(_stmt, "created_at")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: MutableList<BookmarkEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: BookmarkEntity
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpUserId: String
          _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpTitleHlc: String
          _tmpTitleHlc = _stmt.getText(_columnIndexOfTitleHlc)
          val _tmpTargetType: String
          _tmpTargetType = _stmt.getText(_columnIndexOfTargetType)
          val _tmpTargetTypeHlc: String
          _tmpTargetTypeHlc = _stmt.getText(_columnIndexOfTargetTypeHlc)
          val _tmpTargetDocumentId: String?
          if (_stmt.isNull(_columnIndexOfTargetDocumentId)) {
            _tmpTargetDocumentId = null
          } else {
            _tmpTargetDocumentId = _stmt.getText(_columnIndexOfTargetDocumentId)
          }
          val _tmpTargetDocumentIdHlc: String
          _tmpTargetDocumentIdHlc = _stmt.getText(_columnIndexOfTargetDocumentIdHlc)
          val _tmpTargetNodeId: String?
          if (_stmt.isNull(_columnIndexOfTargetNodeId)) {
            _tmpTargetNodeId = null
          } else {
            _tmpTargetNodeId = _stmt.getText(_columnIndexOfTargetNodeId)
          }
          val _tmpTargetNodeIdHlc: String
          _tmpTargetNodeIdHlc = _stmt.getText(_columnIndexOfTargetNodeIdHlc)
          val _tmpQuery: String?
          if (_stmt.isNull(_columnIndexOfQuery)) {
            _tmpQuery = null
          } else {
            _tmpQuery = _stmt.getText(_columnIndexOfQuery)
          }
          val _tmpQueryHlc: String
          _tmpQueryHlc = _stmt.getText(_columnIndexOfQueryHlc)
          val _tmpSortOrder: String
          _tmpSortOrder = _stmt.getText(_columnIndexOfSortOrder)
          val _tmpSortOrderHlc: String
          _tmpSortOrderHlc = _stmt.getText(_columnIndexOfSortOrderHlc)
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
          _item = BookmarkEntity(_tmpId,_tmpUserId,_tmpTitle,_tmpTitleHlc,_tmpTargetType,_tmpTargetTypeHlc,_tmpTargetDocumentId,_tmpTargetDocumentIdHlc,_tmpTargetNodeId,_tmpTargetNodeIdHlc,_tmpQuery,_tmpQueryHlc,_tmpSortOrder,_tmpSortOrderHlc,_tmpDeletedAt,_tmpDeletedHlc,_tmpDeviceId,_tmpCreatedAt,_tmpUpdatedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun softDeleteBookmark(
    id: String,
    deletedAt: Long,
    deletedHlc: String,
    updatedAt: Long,
  ) {
    val _sql: String = "UPDATE bookmarks SET deleted_at = ?, deleted_hlc = ?, updated_at = ? WHERE id = ?"
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

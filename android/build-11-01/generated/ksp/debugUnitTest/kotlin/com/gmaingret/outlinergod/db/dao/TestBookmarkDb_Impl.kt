package com.gmaingret.outlinergod.db.dao

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class TestBookmarkDb_Impl : TestBookmarkDb() {
  private val _bookmarkDao: Lazy<BookmarkDao> = lazy {
    BookmarkDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1, "adbb6abf55226f5053a569905868b06b", "b001ecf2718ae43e405fecf1d6ec928b") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `bookmarks` (`id` TEXT NOT NULL, `user_id` TEXT NOT NULL, `title` TEXT NOT NULL, `title_hlc` TEXT NOT NULL, `target_type` TEXT NOT NULL, `target_type_hlc` TEXT NOT NULL, `target_document_id` TEXT, `target_document_id_hlc` TEXT NOT NULL, `target_node_id` TEXT, `target_node_id_hlc` TEXT NOT NULL, `query` TEXT, `query_hlc` TEXT NOT NULL, `sort_order` TEXT NOT NULL, `sort_order_hlc` TEXT NOT NULL, `deleted_at` INTEGER, `deleted_hlc` TEXT NOT NULL, `device_id` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'adbb6abf55226f5053a569905868b06b')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `bookmarks`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection): RoomOpenDelegate.ValidationResult {
        val _columnsBookmarks: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsBookmarks.put("id", TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBookmarks.put("user_id", TableInfo.Column("user_id", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBookmarks.put("title", TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBookmarks.put("title_hlc", TableInfo.Column("title_hlc", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBookmarks.put("target_type", TableInfo.Column("target_type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBookmarks.put("target_type_hlc", TableInfo.Column("target_type_hlc", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBookmarks.put("target_document_id", TableInfo.Column("target_document_id", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBookmarks.put("target_document_id_hlc", TableInfo.Column("target_document_id_hlc", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBookmarks.put("target_node_id", TableInfo.Column("target_node_id", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBookmarks.put("target_node_id_hlc", TableInfo.Column("target_node_id_hlc", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBookmarks.put("query", TableInfo.Column("query", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBookmarks.put("query_hlc", TableInfo.Column("query_hlc", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBookmarks.put("sort_order", TableInfo.Column("sort_order", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBookmarks.put("sort_order_hlc", TableInfo.Column("sort_order_hlc", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBookmarks.put("deleted_at", TableInfo.Column("deleted_at", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBookmarks.put("deleted_hlc", TableInfo.Column("deleted_hlc", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBookmarks.put("device_id", TableInfo.Column("device_id", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBookmarks.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBookmarks.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysBookmarks: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesBookmarks: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoBookmarks: TableInfo = TableInfo("bookmarks", _columnsBookmarks, _foreignKeysBookmarks, _indicesBookmarks)
        val _existingBookmarks: TableInfo = read(connection, "bookmarks")
        if (!_infoBookmarks.equals(_existingBookmarks)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |bookmarks(com.gmaingret.outlinergod.db.entity.BookmarkEntity).
              | Expected:
              |""".trimMargin() + _infoBookmarks + """
              |
              | Found:
              |""".trimMargin() + _existingBookmarks)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "bookmarks")
  }

  public override fun clearAllTables() {
    super.performClear(false, "bookmarks")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(BookmarkDao::class, BookmarkDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>): List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun bookmarkDao(): BookmarkDao = _bookmarkDao.value
}

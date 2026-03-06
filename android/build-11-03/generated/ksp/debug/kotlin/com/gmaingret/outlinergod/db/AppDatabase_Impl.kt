package com.gmaingret.outlinergod.db

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.gmaingret.outlinergod.db.dao.BookmarkDao
import com.gmaingret.outlinergod.db.dao.BookmarkDao_Impl
import com.gmaingret.outlinergod.db.dao.DocumentDao
import com.gmaingret.outlinergod.db.dao.DocumentDao_Impl
import com.gmaingret.outlinergod.db.dao.NodeDao
import com.gmaingret.outlinergod.db.dao.NodeDao_Impl
import com.gmaingret.outlinergod.db.dao.SettingsDao
import com.gmaingret.outlinergod.db.dao.SettingsDao_Impl
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
public class AppDatabase_Impl : AppDatabase() {
  private val _nodeDao: Lazy<NodeDao> = lazy {
    NodeDao_Impl(this)
  }

  private val _documentDao: Lazy<DocumentDao> = lazy {
    DocumentDao_Impl(this)
  }

  private val _bookmarkDao: Lazy<BookmarkDao> = lazy {
    BookmarkDao_Impl(this)
  }

  private val _settingsDao: Lazy<SettingsDao> = lazy {
    SettingsDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(2, "12ed424a1730003985135c3ab4b27d30", "97ea06ed780953f69546bc958ffab6e7") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `nodes` (`id` TEXT NOT NULL, `document_id` TEXT NOT NULL, `user_id` TEXT NOT NULL, `content` TEXT NOT NULL, `content_hlc` TEXT NOT NULL, `note` TEXT NOT NULL, `note_hlc` TEXT NOT NULL, `parent_id` TEXT, `parent_id_hlc` TEXT NOT NULL, `sort_order` TEXT NOT NULL, `sort_order_hlc` TEXT NOT NULL, `completed` INTEGER NOT NULL, `completed_hlc` TEXT NOT NULL, `color` INTEGER NOT NULL, `color_hlc` TEXT NOT NULL, `collapsed` INTEGER NOT NULL, `collapsed_hlc` TEXT NOT NULL, `deleted_at` INTEGER, `deleted_hlc` TEXT NOT NULL, `device_id` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `documents` (`id` TEXT NOT NULL, `user_id` TEXT NOT NULL, `title` TEXT NOT NULL, `title_hlc` TEXT NOT NULL, `type` TEXT NOT NULL, `parent_id` TEXT, `parent_id_hlc` TEXT NOT NULL, `sort_order` TEXT NOT NULL, `sort_order_hlc` TEXT NOT NULL, `collapsed` INTEGER NOT NULL, `collapsed_hlc` TEXT NOT NULL, `deleted_at` INTEGER, `deleted_hlc` TEXT NOT NULL, `device_id` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `bookmarks` (`id` TEXT NOT NULL, `user_id` TEXT NOT NULL, `title` TEXT NOT NULL, `title_hlc` TEXT NOT NULL, `target_type` TEXT NOT NULL, `target_type_hlc` TEXT NOT NULL, `target_document_id` TEXT, `target_document_id_hlc` TEXT NOT NULL, `target_node_id` TEXT, `target_node_id_hlc` TEXT NOT NULL, `query` TEXT, `query_hlc` TEXT NOT NULL, `sort_order` TEXT NOT NULL, `sort_order_hlc` TEXT NOT NULL, `deleted_at` INTEGER, `deleted_hlc` TEXT NOT NULL, `device_id` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `settings` (`user_id` TEXT NOT NULL, `theme` TEXT NOT NULL, `theme_hlc` TEXT NOT NULL, `density` TEXT NOT NULL, `density_hlc` TEXT NOT NULL, `show_guide_lines` INTEGER NOT NULL, `show_guide_lines_hlc` TEXT NOT NULL, `show_backlink_badge` INTEGER NOT NULL, `show_backlink_badge_hlc` TEXT NOT NULL, `device_id` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`user_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '12ed424a1730003985135c3ab4b27d30')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `nodes`")
        connection.execSQL("DROP TABLE IF EXISTS `documents`")
        connection.execSQL("DROP TABLE IF EXISTS `bookmarks`")
        connection.execSQL("DROP TABLE IF EXISTS `settings`")
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
        val _columnsNodes: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsNodes.put("id", TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNodes.put("document_id", TableInfo.Column("document_id", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNodes.put("user_id", TableInfo.Column("user_id", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNodes.put("content", TableInfo.Column("content", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNodes.put("content_hlc", TableInfo.Column("content_hlc", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNodes.put("note", TableInfo.Column("note", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNodes.put("note_hlc", TableInfo.Column("note_hlc", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNodes.put("parent_id", TableInfo.Column("parent_id", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNodes.put("parent_id_hlc", TableInfo.Column("parent_id_hlc", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNodes.put("sort_order", TableInfo.Column("sort_order", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNodes.put("sort_order_hlc", TableInfo.Column("sort_order_hlc", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNodes.put("completed", TableInfo.Column("completed", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNodes.put("completed_hlc", TableInfo.Column("completed_hlc", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNodes.put("color", TableInfo.Column("color", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNodes.put("color_hlc", TableInfo.Column("color_hlc", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNodes.put("collapsed", TableInfo.Column("collapsed", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNodes.put("collapsed_hlc", TableInfo.Column("collapsed_hlc", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNodes.put("deleted_at", TableInfo.Column("deleted_at", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNodes.put("deleted_hlc", TableInfo.Column("deleted_hlc", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNodes.put("device_id", TableInfo.Column("device_id", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNodes.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsNodes.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysNodes: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesNodes: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoNodes: TableInfo = TableInfo("nodes", _columnsNodes, _foreignKeysNodes, _indicesNodes)
        val _existingNodes: TableInfo = read(connection, "nodes")
        if (!_infoNodes.equals(_existingNodes)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |nodes(com.gmaingret.outlinergod.db.entity.NodeEntity).
              | Expected:
              |""".trimMargin() + _infoNodes + """
              |
              | Found:
              |""".trimMargin() + _existingNodes)
        }
        val _columnsDocuments: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsDocuments.put("id", TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDocuments.put("user_id", TableInfo.Column("user_id", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDocuments.put("title", TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDocuments.put("title_hlc", TableInfo.Column("title_hlc", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDocuments.put("type", TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDocuments.put("parent_id", TableInfo.Column("parent_id", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDocuments.put("parent_id_hlc", TableInfo.Column("parent_id_hlc", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDocuments.put("sort_order", TableInfo.Column("sort_order", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDocuments.put("sort_order_hlc", TableInfo.Column("sort_order_hlc", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDocuments.put("collapsed", TableInfo.Column("collapsed", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDocuments.put("collapsed_hlc", TableInfo.Column("collapsed_hlc", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDocuments.put("deleted_at", TableInfo.Column("deleted_at", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDocuments.put("deleted_hlc", TableInfo.Column("deleted_hlc", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDocuments.put("device_id", TableInfo.Column("device_id", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDocuments.put("created_at", TableInfo.Column("created_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDocuments.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysDocuments: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesDocuments: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoDocuments: TableInfo = TableInfo("documents", _columnsDocuments, _foreignKeysDocuments, _indicesDocuments)
        val _existingDocuments: TableInfo = read(connection, "documents")
        if (!_infoDocuments.equals(_existingDocuments)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |documents(com.gmaingret.outlinergod.db.entity.DocumentEntity).
              | Expected:
              |""".trimMargin() + _infoDocuments + """
              |
              | Found:
              |""".trimMargin() + _existingDocuments)
        }
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
        val _columnsSettings: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsSettings.put("user_id", TableInfo.Column("user_id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSettings.put("theme", TableInfo.Column("theme", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSettings.put("theme_hlc", TableInfo.Column("theme_hlc", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSettings.put("density", TableInfo.Column("density", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSettings.put("density_hlc", TableInfo.Column("density_hlc", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSettings.put("show_guide_lines", TableInfo.Column("show_guide_lines", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSettings.put("show_guide_lines_hlc", TableInfo.Column("show_guide_lines_hlc", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSettings.put("show_backlink_badge", TableInfo.Column("show_backlink_badge", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSettings.put("show_backlink_badge_hlc", TableInfo.Column("show_backlink_badge_hlc", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSettings.put("device_id", TableInfo.Column("device_id", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSettings.put("updated_at", TableInfo.Column("updated_at", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysSettings: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesSettings: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoSettings: TableInfo = TableInfo("settings", _columnsSettings, _foreignKeysSettings, _indicesSettings)
        val _existingSettings: TableInfo = read(connection, "settings")
        if (!_infoSettings.equals(_existingSettings)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |settings(com.gmaingret.outlinergod.db.entity.SettingsEntity).
              | Expected:
              |""".trimMargin() + _infoSettings + """
              |
              | Found:
              |""".trimMargin() + _existingSettings)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "nodes", "documents", "bookmarks", "settings")
  }

  public override fun clearAllTables() {
    super.performClear(false, "nodes", "documents", "bookmarks", "settings")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(NodeDao::class, NodeDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(DocumentDao::class, DocumentDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(BookmarkDao::class, BookmarkDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(SettingsDao::class, SettingsDao_Impl.getRequiredConverters())
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

  public override fun nodeDao(): NodeDao = _nodeDao.value

  public override fun documentDao(): DocumentDao = _documentDao.value

  public override fun bookmarkDao(): BookmarkDao = _bookmarkDao.value

  public override fun settingsDao(): SettingsDao = _settingsDao.value
}

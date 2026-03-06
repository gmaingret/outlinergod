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
public class TestNodeDb_Impl : TestNodeDb() {
  private val _nodeDao: Lazy<NodeDao> = lazy {
    NodeDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1, "7e9366d8080511f523e52ba4e19f8036", "ba5fedfb8dfc084e8257db6e05cf6304") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `nodes` (`id` TEXT NOT NULL, `document_id` TEXT NOT NULL, `user_id` TEXT NOT NULL, `content` TEXT NOT NULL, `content_hlc` TEXT NOT NULL, `note` TEXT NOT NULL, `note_hlc` TEXT NOT NULL, `parent_id` TEXT, `parent_id_hlc` TEXT NOT NULL, `sort_order` TEXT NOT NULL, `sort_order_hlc` TEXT NOT NULL, `completed` INTEGER NOT NULL, `completed_hlc` TEXT NOT NULL, `color` INTEGER NOT NULL, `color_hlc` TEXT NOT NULL, `collapsed` INTEGER NOT NULL, `collapsed_hlc` TEXT NOT NULL, `deleted_at` INTEGER, `deleted_hlc` TEXT NOT NULL, `device_id` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '7e9366d8080511f523e52ba4e19f8036')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `nodes`")
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
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "nodes")
  }

  public override fun clearAllTables() {
    super.performClear(false, "nodes")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(NodeDao::class, NodeDao_Impl.getRequiredConverters())
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
}

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
public class TestDocumentDb_Impl : TestDocumentDb() {
  private val _documentDao: Lazy<DocumentDao> = lazy {
    DocumentDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1, "897a1f9470c0bbc9905ff39c778a49e9", "c1f18e94317c9223a751b61410f9df5d") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `documents` (`id` TEXT NOT NULL, `user_id` TEXT NOT NULL, `title` TEXT NOT NULL, `title_hlc` TEXT NOT NULL, `type` TEXT NOT NULL, `parent_id` TEXT, `parent_id_hlc` TEXT NOT NULL, `sort_order` TEXT NOT NULL, `sort_order_hlc` TEXT NOT NULL, `collapsed` INTEGER NOT NULL, `collapsed_hlc` TEXT NOT NULL, `deleted_at` INTEGER, `deleted_hlc` TEXT NOT NULL, `device_id` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '897a1f9470c0bbc9905ff39c778a49e9')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `documents`")
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
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "documents")
  }

  public override fun clearAllTables() {
    super.performClear(false, "documents")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(DocumentDao::class, DocumentDao_Impl.getRequiredConverters())
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

  public override fun documentDao(): DocumentDao = _documentDao.value
}

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
public class TestSettingsDb_Impl : TestSettingsDb() {
  private val _settingsDao: Lazy<SettingsDao> = lazy {
    SettingsDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(1, "0711e4799acd60704834cbe3bcd4452c", "088ac3e38ea1c63f00e19b811f9b7672") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `settings` (`user_id` TEXT NOT NULL, `theme` TEXT NOT NULL, `theme_hlc` TEXT NOT NULL, `density` TEXT NOT NULL, `density_hlc` TEXT NOT NULL, `show_guide_lines` INTEGER NOT NULL, `show_guide_lines_hlc` TEXT NOT NULL, `show_backlink_badge` INTEGER NOT NULL, `show_backlink_badge_hlc` TEXT NOT NULL, `device_id` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`user_id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '0711e4799acd60704834cbe3bcd4452c')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
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
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "settings")
  }

  public override fun clearAllTables() {
    super.performClear(false, "settings")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
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

  public override fun settingsDao(): SettingsDao = _settingsDao.value
}

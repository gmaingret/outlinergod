package com.gmaingret.outlinergod.db.dao

import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.gmaingret.outlinergod.db.entity.SettingsEntity
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class SettingsDao_Impl(
  __db: RoomDatabase,
) : SettingsDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfSettingsEntity: EntityInsertAdapter<SettingsEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfSettingsEntity = object : EntityInsertAdapter<SettingsEntity>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `settings` (`user_id`,`theme`,`theme_hlc`,`density`,`density_hlc`,`show_guide_lines`,`show_guide_lines_hlc`,`show_backlink_badge`,`show_backlink_badge_hlc`,`device_id`,`updated_at`) VALUES (?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: SettingsEntity) {
        statement.bindText(1, entity.userId)
        statement.bindText(2, entity.theme)
        statement.bindText(3, entity.themeHlc)
        statement.bindText(4, entity.density)
        statement.bindText(5, entity.densityHlc)
        statement.bindLong(6, entity.showGuideLines.toLong())
        statement.bindText(7, entity.showGuideLinesHlc)
        statement.bindLong(8, entity.showBacklinkBadge.toLong())
        statement.bindText(9, entity.showBacklinkBadgeHlc)
        statement.bindText(10, entity.deviceId)
        statement.bindLong(11, entity.updatedAt)
      }
    }
  }

  public override suspend fun upsertSettings(settings: SettingsEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfSettingsEntity.insert(_connection, settings)
  }

  public override fun getSettings(userId: String): Flow<SettingsEntity?> {
    val _sql: String = "SELECT * FROM settings WHERE user_id = ? LIMIT 1"
    return createFlow(__db, false, arrayOf("settings")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, userId)
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "user_id")
        val _columnIndexOfTheme: Int = getColumnIndexOrThrow(_stmt, "theme")
        val _columnIndexOfThemeHlc: Int = getColumnIndexOrThrow(_stmt, "theme_hlc")
        val _columnIndexOfDensity: Int = getColumnIndexOrThrow(_stmt, "density")
        val _columnIndexOfDensityHlc: Int = getColumnIndexOrThrow(_stmt, "density_hlc")
        val _columnIndexOfShowGuideLines: Int = getColumnIndexOrThrow(_stmt, "show_guide_lines")
        val _columnIndexOfShowGuideLinesHlc: Int = getColumnIndexOrThrow(_stmt, "show_guide_lines_hlc")
        val _columnIndexOfShowBacklinkBadge: Int = getColumnIndexOrThrow(_stmt, "show_backlink_badge")
        val _columnIndexOfShowBacklinkBadgeHlc: Int = getColumnIndexOrThrow(_stmt, "show_backlink_badge_hlc")
        val _columnIndexOfDeviceId: Int = getColumnIndexOrThrow(_stmt, "device_id")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: SettingsEntity?
        if (_stmt.step()) {
          val _tmpUserId: String
          _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          val _tmpTheme: String
          _tmpTheme = _stmt.getText(_columnIndexOfTheme)
          val _tmpThemeHlc: String
          _tmpThemeHlc = _stmt.getText(_columnIndexOfThemeHlc)
          val _tmpDensity: String
          _tmpDensity = _stmt.getText(_columnIndexOfDensity)
          val _tmpDensityHlc: String
          _tmpDensityHlc = _stmt.getText(_columnIndexOfDensityHlc)
          val _tmpShowGuideLines: Int
          _tmpShowGuideLines = _stmt.getLong(_columnIndexOfShowGuideLines).toInt()
          val _tmpShowGuideLinesHlc: String
          _tmpShowGuideLinesHlc = _stmt.getText(_columnIndexOfShowGuideLinesHlc)
          val _tmpShowBacklinkBadge: Int
          _tmpShowBacklinkBadge = _stmt.getLong(_columnIndexOfShowBacklinkBadge).toInt()
          val _tmpShowBacklinkBadgeHlc: String
          _tmpShowBacklinkBadgeHlc = _stmt.getText(_columnIndexOfShowBacklinkBadgeHlc)
          val _tmpDeviceId: String
          _tmpDeviceId = _stmt.getText(_columnIndexOfDeviceId)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _result = SettingsEntity(_tmpUserId,_tmpTheme,_tmpThemeHlc,_tmpDensity,_tmpDensityHlc,_tmpShowGuideLines,_tmpShowGuideLinesHlc,_tmpShowBacklinkBadge,_tmpShowBacklinkBadgeHlc,_tmpDeviceId,_tmpUpdatedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getSettingsSync(userId: String): SettingsEntity? {
    val _sql: String = "SELECT * FROM settings WHERE user_id = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, userId)
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "user_id")
        val _columnIndexOfTheme: Int = getColumnIndexOrThrow(_stmt, "theme")
        val _columnIndexOfThemeHlc: Int = getColumnIndexOrThrow(_stmt, "theme_hlc")
        val _columnIndexOfDensity: Int = getColumnIndexOrThrow(_stmt, "density")
        val _columnIndexOfDensityHlc: Int = getColumnIndexOrThrow(_stmt, "density_hlc")
        val _columnIndexOfShowGuideLines: Int = getColumnIndexOrThrow(_stmt, "show_guide_lines")
        val _columnIndexOfShowGuideLinesHlc: Int = getColumnIndexOrThrow(_stmt, "show_guide_lines_hlc")
        val _columnIndexOfShowBacklinkBadge: Int = getColumnIndexOrThrow(_stmt, "show_backlink_badge")
        val _columnIndexOfShowBacklinkBadgeHlc: Int = getColumnIndexOrThrow(_stmt, "show_backlink_badge_hlc")
        val _columnIndexOfDeviceId: Int = getColumnIndexOrThrow(_stmt, "device_id")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: SettingsEntity?
        if (_stmt.step()) {
          val _tmpUserId: String
          _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          val _tmpTheme: String
          _tmpTheme = _stmt.getText(_columnIndexOfTheme)
          val _tmpThemeHlc: String
          _tmpThemeHlc = _stmt.getText(_columnIndexOfThemeHlc)
          val _tmpDensity: String
          _tmpDensity = _stmt.getText(_columnIndexOfDensity)
          val _tmpDensityHlc: String
          _tmpDensityHlc = _stmt.getText(_columnIndexOfDensityHlc)
          val _tmpShowGuideLines: Int
          _tmpShowGuideLines = _stmt.getLong(_columnIndexOfShowGuideLines).toInt()
          val _tmpShowGuideLinesHlc: String
          _tmpShowGuideLinesHlc = _stmt.getText(_columnIndexOfShowGuideLinesHlc)
          val _tmpShowBacklinkBadge: Int
          _tmpShowBacklinkBadge = _stmt.getLong(_columnIndexOfShowBacklinkBadge).toInt()
          val _tmpShowBacklinkBadgeHlc: String
          _tmpShowBacklinkBadgeHlc = _stmt.getText(_columnIndexOfShowBacklinkBadgeHlc)
          val _tmpDeviceId: String
          _tmpDeviceId = _stmt.getText(_columnIndexOfDeviceId)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _result = SettingsEntity(_tmpUserId,_tmpTheme,_tmpThemeHlc,_tmpDensity,_tmpDensityHlc,_tmpShowGuideLines,_tmpShowGuideLinesHlc,_tmpShowBacklinkBadge,_tmpShowBacklinkBadgeHlc,_tmpDeviceId,_tmpUpdatedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getPendingSettings(
    userId: String,
    sinceHlc: String,
    deviceId: String,
  ): SettingsEntity? {
    val _sql: String = """
        |
        |        SELECT * FROM settings
        |        WHERE user_id = ?
        |        AND device_id = ?
        |        AND (theme_hlc > ?
        |          OR density_hlc > ?
        |          OR show_guide_lines_hlc > ?
        |          OR show_backlink_badge_hlc > ?)
        |        LIMIT 1
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
        val _columnIndexOfUserId: Int = getColumnIndexOrThrow(_stmt, "user_id")
        val _columnIndexOfTheme: Int = getColumnIndexOrThrow(_stmt, "theme")
        val _columnIndexOfThemeHlc: Int = getColumnIndexOrThrow(_stmt, "theme_hlc")
        val _columnIndexOfDensity: Int = getColumnIndexOrThrow(_stmt, "density")
        val _columnIndexOfDensityHlc: Int = getColumnIndexOrThrow(_stmt, "density_hlc")
        val _columnIndexOfShowGuideLines: Int = getColumnIndexOrThrow(_stmt, "show_guide_lines")
        val _columnIndexOfShowGuideLinesHlc: Int = getColumnIndexOrThrow(_stmt, "show_guide_lines_hlc")
        val _columnIndexOfShowBacklinkBadge: Int = getColumnIndexOrThrow(_stmt, "show_backlink_badge")
        val _columnIndexOfShowBacklinkBadgeHlc: Int = getColumnIndexOrThrow(_stmt, "show_backlink_badge_hlc")
        val _columnIndexOfDeviceId: Int = getColumnIndexOrThrow(_stmt, "device_id")
        val _columnIndexOfUpdatedAt: Int = getColumnIndexOrThrow(_stmt, "updated_at")
        val _result: SettingsEntity?
        if (_stmt.step()) {
          val _tmpUserId: String
          _tmpUserId = _stmt.getText(_columnIndexOfUserId)
          val _tmpTheme: String
          _tmpTheme = _stmt.getText(_columnIndexOfTheme)
          val _tmpThemeHlc: String
          _tmpThemeHlc = _stmt.getText(_columnIndexOfThemeHlc)
          val _tmpDensity: String
          _tmpDensity = _stmt.getText(_columnIndexOfDensity)
          val _tmpDensityHlc: String
          _tmpDensityHlc = _stmt.getText(_columnIndexOfDensityHlc)
          val _tmpShowGuideLines: Int
          _tmpShowGuideLines = _stmt.getLong(_columnIndexOfShowGuideLines).toInt()
          val _tmpShowGuideLinesHlc: String
          _tmpShowGuideLinesHlc = _stmt.getText(_columnIndexOfShowGuideLinesHlc)
          val _tmpShowBacklinkBadge: Int
          _tmpShowBacklinkBadge = _stmt.getLong(_columnIndexOfShowBacklinkBadge).toInt()
          val _tmpShowBacklinkBadgeHlc: String
          _tmpShowBacklinkBadgeHlc = _stmt.getText(_columnIndexOfShowBacklinkBadgeHlc)
          val _tmpDeviceId: String
          _tmpDeviceId = _stmt.getText(_columnIndexOfDeviceId)
          val _tmpUpdatedAt: Long
          _tmpUpdatedAt = _stmt.getLong(_columnIndexOfUpdatedAt)
          _result = SettingsEntity(_tmpUserId,_tmpTheme,_tmpThemeHlc,_tmpDensity,_tmpDensityHlc,_tmpShowGuideLines,_tmpShowGuideLinesHlc,_tmpShowBacklinkBadge,_tmpShowBacklinkBadgeHlc,_tmpDeviceId,_tmpUpdatedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}

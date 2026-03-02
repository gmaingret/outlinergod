package com.gmaingret.outlinergod.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey @ColumnInfo(name = "user_id") val userId: String,
    val theme: String = "dark",
    @ColumnInfo(name = "theme_hlc") val themeHlc: String = "",
    val density: String = "cozy",
    @ColumnInfo(name = "density_hlc") val densityHlc: String = "",
    @ColumnInfo(name = "show_guide_lines") val showGuideLines: Int = 1,
    @ColumnInfo(name = "show_guide_lines_hlc") val showGuideLinesHlc: String = "",
    @ColumnInfo(name = "show_backlink_badge") val showBacklinkBadge: Int = 1,
    @ColumnInfo(name = "show_backlink_badge_hlc") val showBacklinkBadgeHlc: String = "",
    @ColumnInfo(name = "device_id") val deviceId: String = "",
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

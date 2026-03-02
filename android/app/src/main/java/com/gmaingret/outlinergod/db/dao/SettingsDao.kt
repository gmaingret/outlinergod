package com.gmaingret.outlinergod.db.dao

import androidx.room.*
import com.gmaingret.outlinergod.db.entity.SettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE user_id = :userId LIMIT 1")
    fun getSettings(userId: String): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE user_id = :userId LIMIT 1")
    suspend fun getSettingsSync(userId: String): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: SettingsEntity)
}

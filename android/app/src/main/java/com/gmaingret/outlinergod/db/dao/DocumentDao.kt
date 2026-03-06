package com.gmaingret.outlinergod.db.dao

import androidx.room.*
import com.gmaingret.outlinergod.db.entity.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE user_id = :userId AND deleted_at IS NULL ORDER BY sort_order ASC, id ASC")
    fun getAllDocuments(userId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    fun getDocumentById(id: String): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentByIdSync(id: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocuments(documents: List<DocumentEntity>)

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Query("UPDATE documents SET deleted_at = :deletedAt, deleted_hlc = :deletedHlc, updated_at = :updatedAt, device_id = :deviceId WHERE id = :id")
    suspend fun softDeleteDocument(id: String, deletedAt: Long, deletedHlc: String, updatedAt: Long, deviceId: String)

    @Query("""
        SELECT * FROM documents
        WHERE user_id = :userId
        AND device_id = :deviceId
        AND (title_hlc > :sinceHlc
          OR parent_id_hlc > :sinceHlc
          OR sort_order_hlc > :sinceHlc
          OR collapsed_hlc > :sinceHlc
          OR deleted_hlc > :sinceHlc)
    """)
    suspend fun getPendingChanges(userId: String, sinceHlc: String, deviceId: String): List<DocumentEntity>
}

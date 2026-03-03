package com.gmaingret.outlinergod.db.dao

import androidx.room.*
import com.gmaingret.outlinergod.db.entity.NodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeDao {
    @Query("SELECT * FROM nodes WHERE document_id = :documentId AND deleted_at IS NULL")
    fun getNodesByDocument(documentId: String): Flow<List<NodeEntity>>

    @Query("SELECT * FROM nodes WHERE id = :nodeId LIMIT 1")
    fun getNodeById(nodeId: String): Flow<NodeEntity?>

    @Query("SELECT * FROM nodes WHERE document_id = :documentId AND deleted_at IS NULL")
    suspend fun getNodesByDocumentSync(documentId: String): List<NodeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: NodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNodes(nodes: List<NodeEntity>)

    @Update
    suspend fun updateNode(node: NodeEntity)

    @Query("UPDATE nodes SET deleted_at = :deletedAt, deleted_hlc = :deletedHlc, updated_at = :updatedAt WHERE id = :nodeId")
    suspend fun softDeleteNode(nodeId: String, deletedAt: Long, deletedHlc: String, updatedAt: Long)

    @Query("""
        SELECT * FROM nodes
        WHERE device_id = :deviceId
        AND (content_hlc > :sinceHlc
          OR note_hlc > :sinceHlc
          OR parent_id_hlc > :sinceHlc
          OR sort_order_hlc > :sinceHlc
          OR completed_hlc > :sinceHlc
          OR color_hlc > :sinceHlc
          OR collapsed_hlc > :sinceHlc
          OR deleted_hlc > :sinceHlc)
    """)
    suspend fun getPendingChanges(sinceHlc: String, deviceId: String): List<NodeEntity>
}

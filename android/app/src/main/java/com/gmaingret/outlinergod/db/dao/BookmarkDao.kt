package com.gmaingret.outlinergod.db.dao

import androidx.room.*
import com.gmaingret.outlinergod.db.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE user_id = :userId AND deleted_at IS NULL ORDER BY sort_order ASC, id ASC")
    fun observeAllActive(userId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE id = :id LIMIT 1")
    suspend fun getBookmarkByIdSync(id: String): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmarks(bookmarks: List<BookmarkEntity>)

    @Update
    suspend fun updateBookmark(bookmark: BookmarkEntity)

    @Query("UPDATE bookmarks SET deleted_at = :deletedAt, deleted_hlc = :deletedHlc, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDeleteBookmark(id: String, deletedAt: Long, deletedHlc: String, updatedAt: Long)

    @Query("""
        SELECT * FROM bookmarks
        WHERE user_id = :userId
        AND device_id != :deviceId
        AND (title_hlc > :sinceHlc
          OR target_type_hlc > :sinceHlc
          OR target_document_id_hlc > :sinceHlc
          OR target_node_id_hlc > :sinceHlc
          OR query_hlc > :sinceHlc
          OR sort_order_hlc > :sinceHlc
          OR deleted_hlc > :sinceHlc)
    """)
    suspend fun getPendingChanges(userId: String, sinceHlc: String, deviceId: String): List<BookmarkEntity>
}

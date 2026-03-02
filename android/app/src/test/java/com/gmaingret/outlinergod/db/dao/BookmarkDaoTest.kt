package com.gmaingret.outlinergod.db.dao

import androidx.room.*
import androidx.test.core.app.ApplicationProvider
import com.gmaingret.outlinergod.db.entity.BookmarkEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Database(entities = [BookmarkEntity::class], version = 1, exportSchema = false)
abstract class TestBookmarkDb : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookmarkDaoTest {
    private lateinit var db: TestBookmarkDb
    private lateinit var dao: BookmarkDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TestBookmarkDb::class.java
        ).allowMainThreadQueries().build()
        dao = db.bookmarkDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun makeBookmark(
        id: String = "b1",
        userId: String = "u1",
        sortOrder: String = "a0",
        deletedAt: Long? = null,
        targetType: String = "document",
        targetDocumentId: String? = null,
        targetNodeId: String? = null,
        query: String? = null
    ) = BookmarkEntity(
        id = id,
        userId = userId,
        sortOrder = sortOrder,
        deletedAt = deletedAt,
        targetType = targetType,
        targetDocumentId = targetDocumentId,
        targetNodeId = targetNodeId,
        query = query,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun observeAllActive_excludesSoftDeletedBookmarks() = runTest {
        dao.insertBookmark(makeBookmark(id = "b1"))
        dao.insertBookmark(makeBookmark(id = "b2"))
        dao.insertBookmark(makeBookmark(id = "b3", deletedAt = 1000L))
        val bookmarks = dao.observeAllActive("u1").first()
        assertEquals(2, bookmarks.size)
        assertTrue(bookmarks.none { it.deletedAt != null })
    }

    @Test
    fun observeAllActive_excludesOtherUsers() = runTest {
        dao.insertBookmark(makeBookmark(id = "b1", userId = "userA"))
        dao.insertBookmark(makeBookmark(id = "b2", userId = "userB"))
        val bookmarks = dao.observeAllActive("userA").first()
        assertEquals(1, bookmarks.size)
        assertEquals("b1", bookmarks[0].id)
    }

    @Test
    fun upsertBookmarks_insertsBatch() = runTest {
        val bookmarks = listOf(
            makeBookmark(id = "b1"),
            makeBookmark(id = "b2"),
            makeBookmark(id = "b3")
        )
        dao.upsertBookmarks(bookmarks)
        val result = dao.observeAllActive("u1").first()
        assertEquals(3, result.size)
    }

    @Test
    fun softDeleteBookmark_setsTombstone() = runTest {
        dao.insertBookmark(makeBookmark(id = "b1"))
        dao.softDeleteBookmark("b1", deletedAt = 1000L, deletedHlc = "ZZZZ", updatedAt = 1000L)
        val result = dao.observeAllActive("u1").first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun searchBookmark_withNullTargetIds_roundtrip() = runTest {
        dao.insertBookmark(makeBookmark(
            id = "b1",
            targetType = "search",
            targetDocumentId = null,
            targetNodeId = null,
            query = "hello"
        ))
        val result = dao.getBookmarkByIdSync("b1")
        assertNotNull(result)
        assertEquals("hello", result!!.query)
        assertNull(result.targetDocumentId)
        assertNull(result.targetNodeId)
    }
}

package com.gmaingret.outlinergod.db.dao

import androidx.room.*
import androidx.test.core.app.ApplicationProvider
import com.gmaingret.outlinergod.db.entity.DocumentEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Database(entities = [DocumentEntity::class], version = 1, exportSchema = false)
abstract class TestDocumentDb : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocumentDaoTest {
    private lateinit var db: TestDocumentDb
    private lateinit var dao: DocumentDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TestDocumentDb::class.java
        ).allowMainThreadQueries().build()
        dao = db.documentDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun makeDoc(
        id: String = "d1",
        userId: String = "u1",
        sortOrder: String = "a0",
        deletedAt: Long? = null,
        title: String = "Untitled"
    ) = DocumentEntity(
        id = id,
        userId = userId,
        sortOrder = sortOrder,
        deletedAt = deletedAt,
        title = title,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun getAllDocuments_returnsActiveDocuments() = runTest {
        dao.insertDocument(makeDoc(id = "d1"))
        dao.insertDocument(makeDoc(id = "d2"))
        dao.insertDocument(makeDoc(id = "d3"))
        dao.insertDocument(makeDoc(id = "d4", deletedAt = 1000L))
        val docs = dao.getAllDocuments("u1").first()
        assertEquals(3, docs.size)
        assertTrue(docs.none { it.deletedAt != null })
    }

    @Test
    fun getAllDocuments_sortsByFractionalIndex_lexicographically() = runTest {
        // ASCII order: 'V'(86) < 'Z'(90) < 'a'(97)
        dao.insertDocument(makeDoc(id = "d1", sortOrder = "a"))
        dao.insertDocument(makeDoc(id = "d2", sortOrder = "Z"))
        dao.insertDocument(makeDoc(id = "d3", sortOrder = "V"))
        val docs = dao.getAllDocuments("u1").first()
        assertEquals(listOf("V", "Z", "a"), docs.map { it.sortOrder })
    }

    @Test
    fun getAllDocuments_tiesInSortOrder_brokenById() = runTest {
        dao.insertDocument(makeDoc(id = "z-id", sortOrder = "a0"))
        dao.insertDocument(makeDoc(id = "a-id", sortOrder = "a0"))
        val docs = dao.getAllDocuments("u1").first()
        assertEquals(2, docs.size)
        assertEquals("a-id", docs[0].id)
    }

    @Test
    fun getAllDocuments_excludesOtherUsers() = runTest {
        dao.insertDocument(makeDoc(id = "d1", userId = "userA"))
        dao.insertDocument(makeDoc(id = "d2", userId = "userB"))
        val docs = dao.getAllDocuments("userA").first()
        assertEquals(1, docs.size)
        assertEquals("d1", docs[0].id)
    }

    @Test
    fun upsertDocuments_updatesExistingDocument() = runTest {
        dao.insertDocument(makeDoc(id = "d1", title = "old"))
        dao.upsertDocuments(listOf(makeDoc(id = "d1", title = "new")))
        val doc = dao.getDocumentByIdSync("d1")
        assertNotNull(doc)
        assertEquals("new", doc!!.title)
    }

    @Test
    fun getDocumentByIdSync_returnsNull_forUnknownId() = runTest {
        val doc = dao.getDocumentByIdSync("nonexistent-id")
        assertNull(doc)
    }

    @Test
    fun softDeleteDocument_excludesFromActiveList() = runTest {
        dao.insertDocument(makeDoc(id = "d1"))
        dao.softDeleteDocument("d1", deletedAt = 1000L, deletedHlc = "ZZZZ", updatedAt = 1000L)
        val docs = dao.getAllDocuments("u1").first()
        assertTrue(docs.isEmpty())
    }
}

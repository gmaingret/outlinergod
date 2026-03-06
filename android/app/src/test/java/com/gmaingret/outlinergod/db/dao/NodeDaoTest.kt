package com.gmaingret.outlinergod.db.dao

import androidx.room.*
import androidx.test.core.app.ApplicationProvider
import com.gmaingret.outlinergod.db.entity.NodeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Database(entities = [NodeEntity::class], version = 1, exportSchema = false)
abstract class TestNodeDb : RoomDatabase() {
    abstract fun nodeDao(): NodeDao
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NodeDaoTest {
    private lateinit var db: TestNodeDb
    private lateinit var dao: NodeDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TestNodeDb::class.java
        ).allowMainThreadQueries().build()
        dao = db.nodeDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun makeNode(
        id: String = "n1",
        documentId: String = "doc1",
        userId: String = "u1",
        sortOrder: String = "a0",
        deletedAt: Long? = null,
        deviceId: String = "deviceA",
        contentHlc: String = "0000000000000000-0000-deviceA"
    ) = NodeEntity(
        id = id,
        documentId = documentId,
        userId = userId,
        sortOrder = sortOrder,
        deletedAt = deletedAt,
        deviceId = deviceId,
        contentHlc = contentHlc,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun getNodesByDocument_returnsActiveNodes() = runTest {
        dao.insertNode(makeNode(id = "n1"))
        dao.insertNode(makeNode(id = "n2"))
        dao.insertNode(makeNode(id = "n3", deletedAt = 1000L))
        val nodes = dao.getNodesByDocument("doc1").first()
        assertEquals(2, nodes.size)
        assertTrue(nodes.none { it.deletedAt != null })
    }

    @Test
    fun getNodesByDocument_excludesDifferentDocument() = runTest {
        dao.insertNode(makeNode(id = "nA", documentId = "docA"))
        dao.insertNode(makeNode(id = "nB", documentId = "docB"))
        val nodes = dao.getNodesByDocument("docA").first()
        assertEquals(1, nodes.size)
        assertEquals("nA", nodes[0].id)
    }

    @Test
    fun upsertNodes_insertsNewNodes() = runTest {
        val nodes = listOf(
            makeNode(id = "n1"),
            makeNode(id = "n2"),
            makeNode(id = "n3")
        )
        dao.upsertNodes(nodes)
        val result = dao.getNodesByDocumentSync("doc1")
        assertEquals(3, result.size)
    }

    @Test
    fun upsertNodes_updatesExistingNode_byId() = runTest {
        dao.insertNode(makeNode(id = "n1", sortOrder = "a0").copy(content = "old"))
        dao.upsertNodes(listOf(makeNode(id = "n1", sortOrder = "a0").copy(content = "new")))
        val result = dao.getNodesByDocumentSync("doc1")
        assertEquals(1, result.size)
        assertEquals("new", result[0].content)
    }

    @Test
    fun softDeleteNode_setsTombstone_andExcludesFromActiveList() = runTest {
        dao.insertNode(makeNode(id = "n1"))
        dao.softDeleteNode("n1", deletedAt = 1000L, deletedHlc = "ZZZZ", updatedAt = 1000L, deviceId = "test-device")
        val active = dao.getNodesByDocument("doc1").first()
        assertTrue(active.isEmpty())
        val node = dao.getNodeById("n1").first()
        assertNotNull(node)
        assertNotNull(node!!.deletedAt)
    }

    @Test
    fun getPendingChanges_includesNode_fromSameDevice() = runTest {
        dao.insertNode(makeNode(id = "n1", userId = "u1", deviceId = "deviceA", contentHlc = "ZZZZZZZZZZZZZZZZ-0000-deviceA"))
        val result = dao.getPendingChanges("u1", "0", "deviceA")
        assertEquals(1, result.size)
    }

    @Test
    fun getPendingChanges_excludesNode_fromDifferentDevice() = runTest {
        dao.insertNode(makeNode(id = "n1", userId = "u1", deviceId = "deviceB", contentHlc = "ZZZZZZZZZZZZZZZZ-0000-deviceB"))
        val result = dao.getPendingChanges("u1", "0", "deviceA")
        assertTrue(result.isEmpty())
    }

    @Test
    fun getPendingChanges_excludesNode_fromDifferentUser() = runTest {
        dao.insertNode(makeNode(id = "n1", userId = "otherUser", deviceId = "deviceA", contentHlc = "ZZZZZZZZZZZZZZZZ-0000-deviceA"))
        val result = dao.getPendingChanges("u1", "0", "deviceA")
        assertTrue(result.isEmpty())
    }
}

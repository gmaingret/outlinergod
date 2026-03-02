package com.gmaingret.outlinergod.db

import androidx.test.core.app.ApplicationProvider
import com.gmaingret.outlinergod.db.dao.NodeDao
import com.gmaingret.outlinergod.db.entity.NodeEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = AppDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun buildInMemory_returnsUsableDatabase() {
        // Database is usable if we can obtain a DAO without error
        assertNotNull(db.nodeDao())
        assertNotNull(db.openHelper.writableDatabase)
    }

    @Test
    fun buildInMemory_exposesNodeDao() {
        assertNotNull(db.nodeDao())
        assertTrue(db.nodeDao() is NodeDao)
    }

    @Test
    fun buildInMemory_exposesDocumentDao() {
        assertNotNull(db.documentDao())
    }

    @Test
    fun buildInMemory_exposesBookmarkDao() {
        assertNotNull(db.bookmarkDao())
    }

    @Test
    fun buildInMemory_exposesSettingsDao() {
        assertNotNull(db.settingsDao())
    }

    @Test
    fun twoInMemoryInstances_areIndependent() = runTest {
        val db2 = AppDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        try {
            db.nodeDao().insertNode(
                NodeEntity(
                    id = "n1", documentId = "d1", userId = "u1",
                    sortOrder = "a0", createdAt = 0L, updatedAt = 0L
                )
            )
            val result = db2.nodeDao().getNodesByDocumentSync("d1")
            assertTrue(result.isEmpty())
        } finally {
            db2.close()
        }
    }
}

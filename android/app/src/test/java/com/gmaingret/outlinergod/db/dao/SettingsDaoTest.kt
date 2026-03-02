package com.gmaingret.outlinergod.db.dao

import androidx.room.*
import androidx.test.core.app.ApplicationProvider
import com.gmaingret.outlinergod.db.entity.SettingsEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Database(entities = [SettingsEntity::class], version = 1, exportSchema = false)
abstract class TestSettingsDb : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsDaoTest {
    private lateinit var db: TestSettingsDb
    private lateinit var dao: SettingsDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TestSettingsDb::class.java
        ).allowMainThreadQueries().build()
        dao = db.settingsDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun makeSettings(
        userId: String = "u1",
        theme: String = "dark",
        density: String = "cozy"
    ) = SettingsEntity(
        userId = userId,
        theme = theme,
        density = density,
        updatedAt = 0L
    )

    @Test
    fun getSettings_emitsNull_whenNoRow() = runTest {
        val result = dao.getSettings("u1").first()
        assertNull(result)
    }

    @Test
    fun upsertSettings_insertsNewRow() = runTest {
        dao.upsertSettings(makeSettings(theme = "dark", density = "cozy"))
        val result = dao.getSettingsSync("u1")
        assertNotNull(result)
        assertEquals("dark", result!!.theme)
        assertEquals("cozy", result.density)
    }

    @Test
    fun upsertSettings_replacesExistingRow() = runTest {
        dao.upsertSettings(makeSettings(theme = "dark"))
        dao.upsertSettings(makeSettings(theme = "light"))
        val result = dao.getSettingsSync("u1")
        assertNotNull(result)
        assertEquals("light", result!!.theme)
    }

    @Test
    fun getSettings_emitsUpdatedValue_afterUpsert() = runTest {
        dao.upsertSettings(makeSettings(theme = "dark"))
        val result = dao.getSettings("u1").first()
        assertNotNull(result)
        assertEquals("dark", result!!.theme)
    }
}

package com.gmaingret.outlinergod.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gmaingret.outlinergod.db.dao.BookmarkDao
import com.gmaingret.outlinergod.db.dao.DocumentDao
import com.gmaingret.outlinergod.db.dao.NodeDao
import com.gmaingret.outlinergod.db.dao.SettingsDao
import com.gmaingret.outlinergod.db.entity.BookmarkEntity
import com.gmaingret.outlinergod.db.entity.DocumentEntity
import com.gmaingret.outlinergod.db.entity.NodeEntity
import com.gmaingret.outlinergod.db.entity.SettingsEntity

@Database(
    entities = [
        NodeEntity::class,
        DocumentEntity::class,
        BookmarkEntity::class,
        SettingsEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao
    abstract fun documentDao(): DocumentDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun settingsDao(): SettingsDao

    companion object {

        /**
         * Creates the FTS5 virtual table, triggers, and populates the index.
         *
         * Called from both MIGRATION_1_2 (upgrade path) and the onCreate callback (fresh install path).
         * Must NOT include the populate-from-existing-nodes step in the onCreate path (no existing
         * nodes on a brand-new database), but it's safe to call with the INSERT ... SELECT because
         * it just returns 0 rows on an empty database.
         */
        private fun createFts5(db: SupportSQLiteDatabase) {
            // Create FTS5 external content table backed by the nodes table
            db.execSQL(
                """CREATE VIRTUAL TABLE IF NOT EXISTS nodes_fts
                   USING fts5(content, note, content=nodes, content_rowid=_rowid,
                              tokenize='porter unicode61')"""
            )
            // Populate FTS index from existing active nodes (no-op on empty DB)
            db.execSQL(
                """INSERT INTO nodes_fts(rowid, content, note)
                   SELECT _rowid, content, note FROM nodes WHERE deleted_at IS NULL"""
            )
            // AFTER INSERT trigger: add new node to FTS index
            db.execSQL(
                """CREATE TRIGGER IF NOT EXISTS nodes_fts_insert AFTER INSERT ON nodes BEGIN
                       INSERT INTO nodes_fts(rowid, content, note)
                       VALUES(new._rowid, new.content, new.note);
                   END"""
            )
            // AFTER UPDATE trigger: remove old row, insert updated row
            // Uses the special 'delete' command — never use plain DELETE on external content FTS5
            db.execSQL(
                """CREATE TRIGGER IF NOT EXISTS nodes_fts_update AFTER UPDATE ON nodes BEGIN
                       INSERT INTO nodes_fts(nodes_fts, rowid, content, note)
                       VALUES('delete', old._rowid, old.content, old.note);
                       INSERT INTO nodes_fts(rowid, content, note)
                       VALUES(new._rowid, new.content, new.note);
                   END"""
            )
            // AFTER UPDATE OF deleted_at trigger: remove soft-deleted nodes from FTS index
            db.execSQL(
                """CREATE TRIGGER IF NOT EXISTS nodes_fts_delete
                   AFTER UPDATE OF deleted_at ON nodes
                   WHEN new.deleted_at IS NOT NULL AND old.deleted_at IS NULL BEGIN
                       INSERT INTO nodes_fts(nodes_fts, rowid, content, note)
                       VALUES('delete', old._rowid, old.content, old.note);
                   END"""
            )
        }

        /**
         * Migration from version 1 to 2: adds FTS5 virtual table + triggers.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createFts5(db)
            }
        }

        /**
         * Callback that sets up FTS5 on a brand-new database (version 2 fresh install).
         * Room does NOT run migrations when creating a database from scratch — the callback
         * ensures nodes_fts and its triggers exist on initial creation too.
         */
        private val FTS5_CALLBACK = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                createFts5(db)
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "outlinergod.db")
                .addMigrations(MIGRATION_1_2)
                .addCallback(FTS5_CALLBACK)
                .build()

        /**
         * Builds an in-memory database for tests.
         *
         * Does NOT include FTS5_CALLBACK because Robolectric's sqlite4java does not support
         * the FTS5 module. The FTS5 index and triggers are set up via MIGRATION_1_2 (for upgrade
         * path) and FTS5_CALLBACK (for fresh production installs).
         *
         * FTS5-dependent search logic is tested via SearchRepositoryTest with a mocked NodeDao.
         */
        fun buildInMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .addMigrations(MIGRATION_1_2)
                .allowMainThreadQueries()
                .build()
    }
}

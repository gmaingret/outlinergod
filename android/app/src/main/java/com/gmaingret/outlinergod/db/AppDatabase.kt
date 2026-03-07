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
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nodeDao(): NodeDao
    abstract fun documentDao(): DocumentDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun settingsDao(): SettingsDao

    companion object {

        /**
         * Creates the FTS4 virtual table, triggers, and populates the index.
         *
         * Called from both MIGRATION_1_2 (upgrade path) and the onCreate callback (fresh install path).
         * Uses regular FTS4 (not external content) so triggers can DELETE by rowid.
         * The INSERT...SELECT is a no-op on an empty database.
         *
         * FTS4 is supported on all Android versions (SQLite 3.7.4+, Android 3.0+).
         */
        private fun createFts(db: SupportSQLiteDatabase) {
            // Regular FTS4 table — stores index data directly alongside the nodes table
            // unicode61 tokenizer: unicode-aware case-folding, available since SQLite 3.7.13
            db.execSQL(
                """CREATE VIRTUAL TABLE IF NOT EXISTS nodes_fts
                   USING fts4(content, note, tokenize=unicode61)"""
            )
            // Populate FTS index from existing active nodes (no-op on empty DB)
            db.execSQL(
                """INSERT INTO nodes_fts(rowid, content, note)
                   SELECT rowid, content, note FROM nodes WHERE deleted_at IS NULL"""
            )
            // AFTER INSERT trigger: add new node to FTS index
            db.execSQL(
                """CREATE TRIGGER IF NOT EXISTS nodes_fts_insert AFTER INSERT ON nodes BEGIN
                       INSERT INTO nodes_fts(rowid, content, note)
                       VALUES(new.rowid, new.content, new.note);
                   END"""
            )
            // AFTER UPDATE trigger: remove old entry, re-insert only if not soft-deleted
            // A single trigger handles both regular updates and soft-deletes.
            db.execSQL(
                """CREATE TRIGGER IF NOT EXISTS nodes_fts_update AFTER UPDATE ON nodes BEGIN
                       DELETE FROM nodes_fts WHERE rowid = old.rowid;
                       INSERT INTO nodes_fts(rowid, content, note)
                       SELECT new.rowid, new.content, new.note WHERE new.deleted_at IS NULL;
                   END"""
            )
        }

        /**
         * Migration from version 1 to 2: adds FTS4 virtual table + triggers.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createFts(db)
            }
        }

        /**
         * Migration from version 2 to 3: adds attachment_url and attachment_mime columns.
         *
         * Any existing nodes with the old ATTACH|mime|filename|url pipe-delimited content
         * are parsed via SQLite SUBSTR/INSTR and the values written to the new columns.
         * The content field is cleared to "" for attachment nodes.
         *
         * Format: ATTACH|mime|filename|url (4 pipe-delimited segments, 3 pipes total)
         *   - SUBSTR(content, 8) skips the "ATTACH|" prefix
         *   - First INSTR finds the mime/filename boundary → extracts mime
         *   - Second INSTR finds the filename/url boundary → extracts url
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE nodes ADD COLUMN attachment_url TEXT")
                db.execSQL("ALTER TABLE nodes ADD COLUMN attachment_mime TEXT")
                db.execSQL("""
                    UPDATE nodes SET
                      attachment_mime = SUBSTR(content, 8, INSTR(SUBSTR(content, 8), '|') - 1),
                      attachment_url  = SUBSTR(content,
                          8 + INSTR(SUBSTR(content, 8), '|')
                            + INSTR(SUBSTR(content, 8 + INSTR(SUBSTR(content, 8), '|')), '|')),
                      content = ''
                    WHERE content LIKE 'ATTACH|%'
                """.trimIndent())
            }
        }

        /**
         * Callback that sets up FTS4 on a brand-new database (version 2 fresh install).
         * Room does NOT run migrations when creating a database from scratch — the callback
         * ensures nodes_fts and its triggers exist on initial creation too.
         */
        private val FTS_CALLBACK = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                createFts(db)
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "outlinergod.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(FTS_CALLBACK)
                .build()

        /**
         * Builds an in-memory database for tests.
         *
         * Does NOT include FTS_CALLBACK because Robolectric's sqlite4java does not support
         * FTS virtual tables. FTS-dependent search logic is tested via SearchRepositoryTest
         * with a mocked NodeDao.
         */
        fun buildInMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .allowMainThreadQueries()
                .build()
    }
}

package com.gmaingret.outlinergod.db

import com.gmaingret.outlinergod.db.dao.BookmarkDao
import com.gmaingret.outlinergod.db.dao.DocumentDao
import com.gmaingret.outlinergod.db.dao.NodeDao
import com.gmaingret.outlinergod.db.dao.SettingsDao

/**
 * Stub — real Room @Database annotation and DAO wiring added in P3-8.
 */
abstract class AppDatabase {
    abstract fun nodeDao(): NodeDao
    abstract fun documentDao(): DocumentDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun settingsDao(): SettingsDao
}

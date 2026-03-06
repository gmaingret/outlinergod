package com.gmaingret.outlinergod.sync

import androidx.datastore.preferences.core.stringPreferencesKey

object SyncConstants {
    // Per-user key so switching accounts doesn't contaminate the sync cursor
    fun lastSyncHlcKey(userId: String) = stringPreferencesKey("last_sync_hlc_$userId")
}

package com.gmaingret.outlinergod.sync

import androidx.datastore.preferences.core.stringPreferencesKey

object SyncConstants {
    val LAST_SYNC_HLC_KEY = stringPreferencesKey("last_sync_hlc")
}

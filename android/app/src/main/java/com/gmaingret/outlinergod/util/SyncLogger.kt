package com.gmaingret.outlinergod.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory ring buffer logger for sync diagnostics.
 * All entries are mirrored to Logcat (tag: OutlinerGodSync).
 * Call getLogs() to obtain all entries as a plain string suitable for sharing.
 */
object SyncLogger {

    private const val MAX_ENTRIES = 1000
    private const val LOGCAT_TAG = "OutlinerGodSync"
    private val entries = CopyOnWriteArrayList<String>()
    private val dateFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun log(tag: String, message: String) {
        val entry = "${dateFmt.format(Date())} [$tag] $message"
        Log.d(LOGCAT_TAG, entry)
        entries.add(entry)
        trimIfNeeded()
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        val stackSnippet = throwable?.let {
            "\n  ${it.javaClass.name}: ${it.message}" +
                "\n  " + it.stackTraceToString().lines().take(10).joinToString("\n  ")
        } ?: ""
        val entry = "${dateFmt.format(Date())} [ERR/$tag] $message$stackSnippet"
        Log.e(LOGCAT_TAG, entry, throwable)
        entries.add(entry)
        trimIfNeeded()
    }

    fun getLogs(): String = buildString {
        appendLine("=== OutlinerGod Sync Debug Logs ===")
        appendLine("Generated: ${Date()}")
        appendLine("Entries: ${entries.size}")
        appendLine()
        entries.forEach { appendLine(it) }
    }

    fun clear() = entries.clear()

    private fun trimIfNeeded() {
        while (entries.size > MAX_ENTRIES) {
            entries.removeAt(0)
        }
    }
}

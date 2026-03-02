package com.gmaingret.outlinergod.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Minimal stub -- expanded in P3-13
@Serializable
data class SyncChangesResponse(
    @SerialName("server_hlc") val serverHlc: String,
    @SerialName("nodes") val nodes: List<String> = emptyList(),
    @SerialName("documents") val documents: List<String> = emptyList(),
    @SerialName("settings") val settings: String? = null,
    @SerialName("bookmarks") val bookmarks: List<String> = emptyList()
)

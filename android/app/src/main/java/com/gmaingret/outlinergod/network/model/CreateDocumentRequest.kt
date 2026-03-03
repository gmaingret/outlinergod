package com.gmaingret.outlinergod.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateDocumentRequest(
    @SerialName("id") val id: String,
    @SerialName("title") val title: String,
    @SerialName("type") val type: String,
    @SerialName("parent_id") val parentId: String?,
    @SerialName("sort_order") val sortOrder: String
)

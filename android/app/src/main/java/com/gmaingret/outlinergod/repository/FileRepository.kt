package com.gmaingret.outlinergod.repository

import android.net.Uri

interface FileRepository {
    suspend fun uploadFile(nodeId: String, uri: Uri, mimeType: String): Result<UploadedFile>
}

data class UploadedFile(val url: String, val filename: String, val mimeType: String)

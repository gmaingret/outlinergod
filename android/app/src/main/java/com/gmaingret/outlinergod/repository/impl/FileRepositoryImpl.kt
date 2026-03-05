package com.gmaingret.outlinergod.repository.impl

import android.content.Context
import android.net.Uri
import com.gmaingret.outlinergod.repository.FileRepository
import com.gmaingret.outlinergod.repository.UploadedFile
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Named

@Serializable
private data class UploadResponse(
    val url: String,
    val uuid: String,
    val filename: String,
    val size: Long,
    @SerialName("mime_type") val mimeType: String,
)

class FileRepositoryImpl @Inject constructor(
    private val httpClient: HttpClient,
    @Named("baseUrl") private val baseUrl: String,
    @ApplicationContext private val context: Context,
) : FileRepository {

    override suspend fun uploadFile(nodeId: String, uri: Uri, mimeType: String): Result<UploadedFile> {
        return runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Cannot open URI: $uri")

            val response = httpClient.submitFormWithBinaryData(
                url = "$baseUrl/api/files/upload",
                formData = formData {
                    append("node_id", nodeId)
                    append("file", bytes, Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"attachment\"")
                        append(HttpHeaders.ContentType, mimeType)
                    })
                }
            )

            val body = response.body<UploadResponse>()
            UploadedFile(
                url = body.url,
                filename = body.filename,
                mimeType = body.mimeType,
            )
        }
    }
}

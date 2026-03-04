package com.gmaingret.outlinergod.repository.impl

import android.content.Context
import com.gmaingret.outlinergod.repository.ExportRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import java.io.File
import javax.inject.Inject
import javax.inject.Named

class ExportRepositoryImpl @Inject constructor(
    private val httpClient: HttpClient,
    @Named("baseUrl") private val baseUrl: String,
    @ApplicationContext private val context: Context
) : ExportRepository {

    override suspend fun exportAll(): Result<File> = runCatching {
        val response = httpClient.get("$baseUrl/api/export")
        val bytes = response.body<ByteArray>()
        val file = File(context.cacheDir, "outlinergod-export-${System.currentTimeMillis()}.zip")
        file.writeBytes(bytes)
        file
    }
}

package com.gmaingret.outlinergod.repository

import java.io.File

interface ExportRepository {
    suspend fun exportAll(): Result<File>
}

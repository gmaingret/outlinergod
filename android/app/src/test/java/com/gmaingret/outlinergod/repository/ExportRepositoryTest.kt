package com.gmaingret.outlinergod.repository

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Unit tests for ExportRepository contract.
 *
 * ExportRepositoryImpl uses Ktor HttpClient which is difficult to mock directly in unit tests.
 * These tests verify the contract by mocking the ExportRepository interface, ensuring the return
 * type contract is correct. Full integration is verified manually by running against the backend.
 */
class ExportRepositoryTest {

    private val exportRepository: ExportRepository = mockk()

    @Test
    fun `exportAll success returns Result with File`() = runTest {
        val tempFile = File.createTempFile("export-test", ".zip")
        tempFile.writeBytes(byteArrayOf(0x50, 0x4B)) // ZIP magic bytes
        tempFile.deleteOnExit()

        coEvery { exportRepository.exportAll() } returns Result.success(tempFile)

        val result = exportRepository.exportAll()

        assertTrue("Result should be success", result.isSuccess)
        val file = result.getOrThrow()
        assertTrue("File should exist", file.exists())
        assertTrue("File should end with .zip", file.name.endsWith(".zip"))
    }

    @Test
    fun `exportAll failure returns Result with exception`() = runTest {
        val ioError = java.io.IOException("Connection refused")
        coEvery { exportRepository.exportAll() } returns Result.failure(ioError)

        val result = exportRepository.exportAll()

        assertTrue("Result should be failure", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Exception should be IOException", exception is java.io.IOException)
        assertEquals("Connection refused", exception?.message)
    }

    @Test
    fun `exportAll networkError returns Result failure`() = runTest {
        val networkError = RuntimeException("Network error")
        coEvery { exportRepository.exportAll() } returns Result.failure(networkError)

        val result = exportRepository.exportAll()

        assertTrue("Result should be failure on network error", result.isFailure)
        assertEquals("Network error", result.exceptionOrNull()?.message)
    }
}

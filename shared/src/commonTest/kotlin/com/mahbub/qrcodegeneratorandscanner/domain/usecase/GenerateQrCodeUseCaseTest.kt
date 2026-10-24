package com.mahbub.qrcodegeneratorandscanner.domain.usecase

import com.mahbub.qrcodegeneratorandscanner.domain.model.GeneratedQr
import com.mahbub.qrcodegeneratorandscanner.domain.model.QrErrorLevel
import com.mahbub.qrcodegeneratorandscanner.domain.model.QrMatrix
import com.mahbub.qrcodegeneratorandscanner.domain.repository.QrCodeRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerateQrCodeUseCaseTest {

    @Test
    fun blankContentFailsWithoutCallingRepository() {
        val repository = FakeQrCodeRepository(Result.success(sampleQr("")))
        val useCase = GenerateQrCodeUseCase(repository)

        val result = useCase("   ", QrErrorLevel.LOW)

        assertTrue(result.isFailure)
        assertEquals(0, repository.callCount)
    }

    @Test
    fun validContentIsDelegatedToRepository() {
        val expected = sampleQr("https://example.com")
        val repository = FakeQrCodeRepository(Result.success(expected))
        val useCase = GenerateQrCodeUseCase(repository)

        val result = useCase("https://example.com", QrErrorLevel.HIGH)

        assertEquals(expected, result.getOrThrow())
        assertEquals(1, repository.callCount)
        assertEquals("https://example.com", repository.lastData)
        assertEquals(QrErrorLevel.HIGH, repository.lastErrorLevel)
    }

    @Test
    fun repositoryFailureIsPropagated() {
        val repository = FakeQrCodeRepository(Result.failure(IllegalArgumentException("too long")))
        val useCase = GenerateQrCodeUseCase(repository)

        val result = useCase("data", QrErrorLevel.MEDIUM)

        assertEquals("too long", result.exceptionOrNull()?.message)
    }

    private fun sampleQr(data: String) = GeneratedQr(
        data = data,
        version = 1,
        errorLevel = QrErrorLevel.MEDIUM,
        matrix = QrMatrix(size = 21, modules = BooleanArray(21 * 21)),
    )

    private class FakeQrCodeRepository(
        private val result: Result<GeneratedQr>,
    ) : QrCodeRepository {
        var callCount = 0
        var lastData: String? = null
        var lastErrorLevel: QrErrorLevel? = null

        override fun generate(
            data: String,
            errorLevel: QrErrorLevel,
            version: Int,
            mask: Int,
        ): Result<GeneratedQr> {
            callCount++
            lastData = data
            lastErrorLevel = errorLevel
            return result
        }
    }
}

package com.mahbub.qrcodegeneratorandscanner.domain.usecase

import com.mahbub.qrcodegeneratorandscanner.domain.model.GeneratedQr
import com.mahbub.qrcodegeneratorandscanner.domain.model.QrErrorLevel
import com.mahbub.qrcodegeneratorandscanner.domain.repository.QrCodeRepository

class GenerateQrCodeUseCase(
    private val repository: QrCodeRepository,
) {
    operator fun invoke(
        data: String,
        errorLevel: QrErrorLevel,
        version: Int = 0,
        mask: Int = -1,
    ): Result<GeneratedQr> {
        if (data.isBlank()) {
            return Result.failure(IllegalArgumentException("Enter text to generate a QR code"))
        }
        return repository.generate(data, errorLevel, version, mask)
    }
}

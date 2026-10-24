package ltd.softvinax.qrkit.app.domain.usecase

import ltd.softvinax.qrkit.app.domain.model.GeneratedQr
import ltd.softvinax.qrkit.app.domain.model.QrErrorLevel
import ltd.softvinax.qrkit.app.domain.repository.QrCodeRepository

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

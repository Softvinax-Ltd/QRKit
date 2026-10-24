package ltd.softvinax.qrkit.app.data.repository

import ltd.softvinax.qrkit.app.data.mapper.toDomain
import ltd.softvinax.qrkit.app.data.mapper.toLibrary
import ltd.softvinax.qrkit.app.domain.model.GeneratedQr
import ltd.softvinax.qrkit.app.domain.model.QrErrorLevel
import ltd.softvinax.qrkit.app.domain.repository.QrCodeRepository
import ltd.softvinax.qrkit.QrCodeGenerator
import ltd.softvinax.qrkit.QrCodeOptions

class QrCodeRepositoryImpl : QrCodeRepository {
    override fun generate(
        data: String,
        errorLevel: QrErrorLevel,
        version: Int,
        mask: Int,
    ): Result<GeneratedQr> = runCatching {
        QrCodeGenerator.generate(
            data = data,
            options = QrCodeOptions(
                errorCorrection = errorLevel.toLibrary(),
                version = version,
                mask = mask,
            ),
        ).toDomain(data)
    }
}

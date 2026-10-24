package ltd.softvinax.qrkit.app.domain.repository

import ltd.softvinax.qrkit.app.domain.model.GeneratedQr
import ltd.softvinax.qrkit.app.domain.model.QrErrorLevel

interface QrCodeRepository {
    /**
     * Encodes [data] into a QR symbol.
     *
     * @param version fixed symbol version (1..40), or 0 to pick the smallest that fits.
     * @param mask fixed mask pattern (0..7), or -1 to pick the lowest-penalty mask.
     */
    fun generate(
        data: String,
        errorLevel: QrErrorLevel,
        version: Int = 0,
        mask: Int = -1,
    ): Result<GeneratedQr>
}

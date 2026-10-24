package ltd.softvinax.qrkit.app.presentation.scan

import ltd.softvinax.qrkit.app.domain.model.QrErrorLevel
import ltd.softvinax.qrkit.app.domain.model.ScannedQr
import ltd.softvinax.qrkit.QrErrorCorrection
import ltd.softvinax.qrkit.QrScanResult

internal fun QrScanResult.toScannedQr(): ScannedQr = ScannedQr(
    text = text,
    contentType = contentType,
    version = version,
    errorLevel = when (errorCorrection) {
        QrErrorCorrection.LOW -> QrErrorLevel.LOW
        QrErrorCorrection.MEDIUM -> QrErrorLevel.MEDIUM
        QrErrorCorrection.QUARTILE -> QrErrorLevel.QUARTILE
        QrErrorCorrection.HIGH -> QrErrorLevel.HIGH
        null -> null
    },
)

package ltd.softvinax.qrkit.app.data.mapper

import ltd.softvinax.qrkit.app.domain.model.GeneratedQr
import ltd.softvinax.qrkit.app.domain.model.QrErrorLevel
import ltd.softvinax.qrkit.app.domain.model.QrMatrix
import ltd.softvinax.qrkit.QrCode
import ltd.softvinax.qrkit.QrErrorCorrection

internal fun QrErrorCorrection.toDomain(): QrErrorLevel = when (this) {
    QrErrorCorrection.LOW -> QrErrorLevel.LOW
    QrErrorCorrection.MEDIUM -> QrErrorLevel.MEDIUM
    QrErrorCorrection.QUARTILE -> QrErrorLevel.QUARTILE
    QrErrorCorrection.HIGH -> QrErrorLevel.HIGH
}

internal fun QrErrorLevel.toLibrary(): QrErrorCorrection = when (this) {
    QrErrorLevel.LOW -> QrErrorCorrection.LOW
    QrErrorLevel.MEDIUM -> QrErrorCorrection.MEDIUM
    QrErrorLevel.QUARTILE -> QrErrorCorrection.QUARTILE
    QrErrorLevel.HIGH -> QrErrorCorrection.HIGH
}

internal fun QrCode.toDomain(data: String): GeneratedQr = GeneratedQr(
    data = data,
    version = version,
    errorLevel = errorCorrection.toDomain(),
    matrix = QrMatrix(
        size = size,
        modules = BooleanArray(size * size) { index -> isDark(index % size, index / size) },
    ),
)

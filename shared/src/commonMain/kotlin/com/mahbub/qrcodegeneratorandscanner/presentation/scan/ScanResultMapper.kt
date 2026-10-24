package com.mahbub.qrcodegeneratorandscanner.presentation.scan

import com.mahbub.qrcodegeneratorandscanner.domain.model.QrErrorLevel
import com.mahbub.qrcodegeneratorandscanner.domain.model.ScannedQr
import com.mahbub.qrkit.QrErrorCorrection
import com.mahbub.qrkit.QrScanResult

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

package com.mahbub.qrcodegeneratorandscanner.presentation.generate

import com.mahbub.qrcodegeneratorandscanner.domain.model.GeneratedQr
import com.mahbub.qrcodegeneratorandscanner.domain.model.QrErrorLevel

data class GenerateUiState(
    val content: String = DEFAULT_CONTENT,
    val errorLevel: QrErrorLevel = QrErrorLevel.MEDIUM,
    val generated: GeneratedQr? = null,
    val errorMessage: String? = null,
) {
    companion object {
        const val DEFAULT_CONTENT = "https://github.com/Softvinax-Ltd/QRKit"
    }
}

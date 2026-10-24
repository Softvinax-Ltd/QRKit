package ltd.softvinax.qrkit.app.presentation.generate

import ltd.softvinax.qrkit.app.domain.model.GeneratedQr
import ltd.softvinax.qrkit.app.domain.model.QrErrorLevel

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

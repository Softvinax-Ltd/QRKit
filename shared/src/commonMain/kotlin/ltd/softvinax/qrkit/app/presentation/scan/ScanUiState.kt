package ltd.softvinax.qrkit.app.presentation.scan

import ltd.softvinax.qrkit.app.domain.model.ScannedQr

data class ScanUiState(
    val result: ScannedQr? = null,
    val errorMessage: String? = null,
)

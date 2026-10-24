package com.mahbub.qrcodegeneratorandscanner.presentation.scan

import com.mahbub.qrcodegeneratorandscanner.domain.model.ScannedQr

data class ScanUiState(
    val result: ScannedQr? = null,
    val errorMessage: String? = null,
)

package com.mahbub.qrcodegeneratorandscanner.domain.model

data class GeneratedQr(
    val data: String,
    val version: Int,
    val errorLevel: QrErrorLevel,
    val matrix: QrMatrix,
)

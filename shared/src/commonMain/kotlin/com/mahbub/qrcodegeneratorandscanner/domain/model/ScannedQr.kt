package com.mahbub.qrcodegeneratorandscanner.domain.model

data class ScannedQr(
    val text: String,
    val contentType: String,
    val version: Int? = null,
    val errorLevel: QrErrorLevel? = null,
)

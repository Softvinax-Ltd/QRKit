package com.mahbub.qrcodegeneratorandscanner.domain.model

/**
 * Error correction level of a QR symbol. Higher levels survive more damage but store less data.
 */
enum class QrErrorLevel {
    LOW,
    MEDIUM,
    QUARTILE,
    HIGH,
}

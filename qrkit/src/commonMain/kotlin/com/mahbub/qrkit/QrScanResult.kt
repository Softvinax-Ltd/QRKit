package com.mahbub.qrkit

/**
 * Result of scanning a QR code.
 *
 * @property text decoded text payload.
 * @property rawBytes corrected data codewords when decoded by the built-in decoder; empty when the
 *   result comes from a platform scanner such as Vision.
 * @property symbology always `QR_CODE` for this library.
 * @property contentType a coarse classification such as `URL`, `WIFI`, `EMAIL` or `TEXT`.
 * @property version QR version (1..40) when known.
 * @property errorCorrection error correction level when known.
 */
data class QrScanResult(
    val text: String,
    val rawBytes: ByteArray = ByteArray(0),
    val symbology: String = "QR_CODE",
    val contentType: String = "TEXT",
    val version: Int? = null,
    val errorCorrection: QrErrorCorrection? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is QrScanResult) return false
        return text == other.text &&
            rawBytes.contentEquals(other.rawBytes) &&
            symbology == other.symbology &&
            contentType == other.contentType &&
            version == other.version &&
            errorCorrection == other.errorCorrection
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + rawBytes.contentHashCode()
        result = 31 * result + symbology.hashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + (version ?: 0)
        result = 31 * result + (errorCorrection?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "QrScanResult(text=$text, contentType=$contentType, version=$version, " +
            "errorCorrection=$errorCorrection)"
}

package ltd.softvinax.qrkit

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Cross-validates the custom encoder and decoder against ZXing, which is used only as a test
 * dependency. These tests never ship with the library.
 */
class ZxingCrossValidationTest {

    private fun QrCode.toZxingMatrix(quietZone: Int = 4): BitMatrix {
        val matrix = BitMatrix(size + quietZone * 2, size + quietZone * 2)
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (isDark(x, y)) matrix.set(x + quietZone, y + quietZone)
            }
        }
        return matrix
    }

    private fun zxingDecode(code: QrCode): String {
        val quietZone = 4
        val scale = 4
        val width = (code.size + quietZone * 2) * scale
        val pixels = ByteArray(width * width) { 0xFF.toByte() }
        for (y in 0 until code.size) {
            for (x in 0 until code.size) {
                if (!code.isDark(x, y)) continue
                for (py in 0 until scale) {
                    val row = (y + quietZone) * scale + py
                    for (px in 0 until scale) {
                        pixels[row * width + (x + quietZone) * scale + px] = 0
                    }
                }
            }
        }
        val source = PlanarYUVLuminanceSource(pixels, width, width, 0, 0, width, width, false)
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return try {
            QRCodeReader().decode(bitmap).text
        } catch (_: NotFoundException) {
            pureZxingDecode(bitmap)
        } catch (_: com.google.zxing.FormatException) {
            pureZxingDecode(bitmap)
        }
    }

    // ZXing's finder heuristic occasionally locks onto a data pattern; PURE_BARCODE skips
    // detection and still validates the encoding, format information and version layout.
    private fun pureZxingDecode(bitmap: BinaryBitmap): String =
        QRCodeReader().decode(
            bitmap,
            mapOf(DecodeHintType.PURE_BARCODE to true),
        ).text

    private fun zxingEncode(
        text: String,
        level: ErrorCorrectionLevel,
        charset: String = "UTF-8",
    ): BitMatrix {
        val hints = mapOf<EncodeHintType, Any>(
            EncodeHintType.ERROR_CORRECTION to level,
            EncodeHintType.CHARACTER_SET to charset,
            EncodeHintType.MARGIN to 1,
        )
        return QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)
    }

    private fun decodeZxingMatrix(matrix: BitMatrix, scale: Int = 4): QrScanResult? {
        val width = matrix.width * scale
        val height = matrix.height * scale
        val pixels = ByteArray(width * height) { 0xFF.toByte() }
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (!matrix[x, y]) continue
                for (py in 0 until scale) {
                    for (px in 0 until scale) {
                        pixels[(y * scale + py) * width + x * scale + px] = 0
                    }
                }
            }
        }
        return QrCodeDecoder.decodeLuminance(pixels, width, height)
    }

    private val zxingLevels = listOf(
        QrErrorCorrection.LOW to ErrorCorrectionLevel.L,
        QrErrorCorrection.MEDIUM to ErrorCorrectionLevel.M,
        QrErrorCorrection.QUARTILE to ErrorCorrectionLevel.Q,
        QrErrorCorrection.HIGH to ErrorCorrectionLevel.H,
    )

    @Test
    fun zxingReadsEveryVersionAndErrorCorrectionLevel() {
        val failures = mutableListOf<String>()
        for (version in 1..40) {
            for ((ourLevel, _) in zxingLevels) {
                val text = "V$version ${ourLevel.name.first()}"
                try {
                    val code = QrCodeGenerator.generate(
                        text,
                        QrCodeOptions(errorCorrection = ourLevel, version = version),
                    )
                    val decoded = zxingDecode(code)
                    if (decoded != text) failures += "v$version ${ourLevel.name}: got '$decoded'"
                } catch (exception: Exception) {
                    failures += "v$version ${ourLevel.name}: ${exception::class.simpleName} ${exception.message}"
                }
            }
        }
        assertEquals(emptyList(), failures)
    }

    @Test
    fun zxingReadsEveryMask() {
        for (mask in 0..7) {
            val text = "MASK $mask"
            val code = QrCodeGenerator.generate(text, QrCodeOptions(mask = mask))
            assertEquals(text, zxingDecode(code), "ZXing failed mask $mask")
        }
    }

    @Test
    fun zxingReadsUnicodeWithEci() {
        val text = "Scan me: héllo — 日本語 ✅"
        val code = QrCodeGenerator.generate(text)
        assertEquals(text, zxingDecode(code))
    }

    @Test
    fun ourDecoderReadsZxingPayloads() {
        val payloads = listOf(
            "HELLO WORLD",
            "https://example.com/path?query=1&value=two",
            "a".repeat(40),
            "Mixed Case Byte Mode " + "x".repeat(60),
            (0 until 300).joinToString("") { ('a' + (it % 26)).toString() },
            (0 until 1200).joinToString("") { ('A' + (it % 26)).toString() },
        )
        for ((_, level) in zxingLevels) {
            for (payload in payloads) {
                val matrix = zxingEncode(payload, level)
                val result = decodeZxingMatrix(matrix)
                assertNotNull(
                    result,
                    "Our decoder failed on ZXing symbol ${matrix.width}x${matrix.height}",
                )
                assertEquals(payload, result.text)
            }
        }
    }

    @Test
    fun ourDecoderReadsZxingUnicode() {
        val payload = "ZXing unicode: বাংলা, 日本語, emoji ✅"
        val matrix = zxingEncode(payload, ErrorCorrectionLevel.M)
        val result = decodeZxingMatrix(matrix)
        assertNotNull(result)
        assertEquals(payload, result.text)
    }

    @Test
    fun ourDecoderReadsZxingNumericAndAlphanumeric() {
        val cases = listOf(
            "0123456789012345678901234567890123456789",
            "HTTPS://EXAMPLE.COM/QRKIT",
            "1234567890ABCDEFGHIJ \$%*+-./:",
        )
        for (case in cases) {
            val matrix = zxingEncode(case, ErrorCorrectionLevel.Q)
            val result = decodeZxingMatrix(matrix)
            assertNotNull(result, "Failed to decode $case")
            assertEquals(case, result.text)
        }
    }

    @Test
    fun largeZxingSymbolsRoundTrip() {
        for (length in listOf(500, 1000, 1500, 2000, 2900)) {
            val payload = buildString { repeat(length) { append(('A'.code + (it % 26)).toChar()) } }
            val matrix = zxingEncode(payload, ErrorCorrectionLevel.L)
            val result = decodeZxingMatrix(matrix, scale = 2)
            assertNotNull(result, "Failed to decode $length byte payload")
            assertEquals(payload, result.text)
        }
    }
}

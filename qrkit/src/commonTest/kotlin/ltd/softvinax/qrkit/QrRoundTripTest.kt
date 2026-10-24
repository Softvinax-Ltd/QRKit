package ltd.softvinax.qrkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class QrRoundTripTest {

    private class Raster(val pixels: ByteArray, val width: Int, val height: Int)

    private fun render(code: QrCode, scale: Int = 5, quietZone: Int = 4): Raster {
        val width = (code.size + quietZone * 2) * scale
        val pixels = ByteArray(width * width) { 0xFF.toByte() }
        for (y in 0 until code.size) {
            for (x in 0 until code.size) {
                if (!code.isDark(x, y)) continue
                for (py in 0 until scale) {
                    val row = (quietZone + y) * scale + py
                    for (px in 0 until scale) {
                        pixels[row * width + (quietZone + x) * scale + px] = 0
                    }
                }
            }
        }
        return Raster(pixels, width, width)
    }

    private fun rotate(raster: Raster, quarterTurns: Int): Raster {
        var current = raster
        repeat(((quarterTurns % 4) + 4) % 4) {
            val pixels = ByteArray(current.pixels.size)
            val size = current.width
            for (y in 0 until size) {
                for (x in 0 until size) {
                    pixels[x * size + (size - 1 - y)] = current.pixels[y * size + x]
                }
            }
            current = Raster(pixels, size, size)
        }
        return current
    }

    private fun assertDecodes(expected: String, code: QrCode, scale: Int = 5) {
        val raster = render(code, scale)
        val result = QrCodeDecoder.decodeLuminance(raster.pixels, raster.width, raster.height)
        assertNotNull(result, "Failed to decode ${code.version}-${code.errorCorrection} mask ${code.mask}")
        assertEquals(expected, result.text)
    }

    @Test
    fun asciiTextRoundTrip() {
        val text = "Hello, QRKit! https://example.com/a?b=1&c=two"
        assertDecodes(text, QrCodeGenerator.generate(text))
    }

    @Test
    fun numericModeRoundTrip() {
        val text = "31415926535897932384"
        val code = QrCodeGenerator.generate(text)
        assertEquals(1, code.version)
        assertDecodes(text, code)
    }

    @Test
    fun alphanumericModeRoundTrip() {
        val text = "HELLO WORLD \$%*+-./: 123"
        val code = QrCodeGenerator.generate(text)
        assertEquals(2, code.version)
        assertDecodes(text, code)
    }

    @Test
    fun unicodeRoundTrip() {
        val text = "শুভেচ্ছা — QR কোড ✅ 日本語"
        val code = QrCodeGenerator.generate(text)
        assertDecodes(text, code)
    }

    @Test
    fun emptyTextRoundTrip() {
        val code = QrCodeGenerator.generate("")
        assertDecodes("", code)
    }

    @Test
    fun everyErrorCorrectionLevelRoundTrip() {
        for (ecc in QrErrorCorrection.entries) {
            val text = "Error correction ${ecc.name} / ${ecc.ordinal}"
            val code = QrCodeGenerator.generate(text, QrCodeOptions(errorCorrection = ecc))
            assertEquals(ecc, code.errorCorrection)
            assertDecodes(text, code)
        }
    }

    @Test
    fun everyMaskRoundTrip() {
        for (mask in 0..7) {
            val text = "Mask pattern $mask"
            val code = QrCodeGenerator.generate(text, QrCodeOptions(mask = mask))
            assertEquals(mask, code.mask)
            assertDecodes(text, code)
        }
    }

    @Test
    fun allVersionsRoundTrip() {
        for (version in 1..40) {
            val text = "VERSION $version OK"
            val code = QrCodeGenerator.generate(
                text,
                QrCodeOptions(errorCorrection = QrErrorCorrection.LOW, version = version),
            )
            assertEquals(version, code.version)
            assertDecodes(text, code)
        }
    }

    @Test
    fun rotationsRoundTrip() {
        val text = "Rotate me 90/180/270"
        val code = QrCodeGenerator.generate(text)
        for (turns in 1..3) {
            val raster = rotate(render(code), turns)
            val result = QrCodeDecoder.decodeLuminance(raster.pixels, raster.width, raster.height)
            assertNotNull(result, "Failed to decode at $turns quarter turns")
            assertEquals(text, result.text)
        }
    }

    @Test
    fun variousScalesRoundTrip() {
        val text = "Scale invariance"
        val code = QrCodeGenerator.generate(text)
        for (scale in 2..7) {
            assertDecodes(text, code, scale)
        }
    }

    @Test
    fun quietZoneRoundTrip() {
        val text = "Quiet zone"
        val code = QrCodeGenerator.generate(text)
        for (quietZone in 2..8) {
            val raster = render(code, quietZone = quietZone)
            val result = QrCodeDecoder.decodeLuminance(raster.pixels, raster.width, raster.height)
            assertNotNull(result, "Failed with quiet zone $quietZone")
            assertEquals(text, result.text)
        }
    }

    @Test
    fun correctedDamageRoundTrip() {
        val text = "Reed-Solomon damage test 0123456789"
        val code = QrCodeGenerator.generate(
            text,
            QrCodeOptions(errorCorrection = QrErrorCorrection.HIGH),
        )
        val damaged = code.toBooleanArray()
        val size = code.size
        // Flip three modules in the central data area, away from finder and format patterns.
        val center = size / 2
        for (offset in 0..2) {
            val x = center + offset * 3
            val y = center - offset * 2
            damaged[y][x] = !damaged[y][x]
        }
        val pixels = ByteArray(((size + 8) * 4) * ((size + 8) * 4)) { 0xFF.toByte() }
        val scale = 4
        val width = (size + 8) * scale
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (!damaged[y][x]) continue
                for (py in 0 until scale) {
                    for (px in 0 until scale) {
                        pixels[((4 + y) * scale + py) * width + (4 + x) * scale + px] = 0
                    }
                }
            }
        }
        val result = QrCodeDecoder.decodeLuminance(pixels, width, width)
        assertNotNull(result)
        assertEquals(text, result.text)
    }

    @Test
    fun contentTypeDetection() {
        val cases = mapOf(
            "https://example.com" to "URL",
            "mailto:hello@example.com" to "EMAIL",
            "tel:+15551234567" to "PHONE",
            "plain text" to "TEXT",
        )
        for ((text, expected) in cases) {
            val code = QrCodeGenerator.generate(text)
            val raster = render(code)
            val result = QrCodeDecoder.decodeLuminance(raster.pixels, raster.width, raster.height)
            assertNotNull(result)
            assertEquals(expected, result.contentType)
        }
    }

    @Test
    fun invalidOptionsAreRejected() {
        kotlin.runCatching {
            QrCodeGenerator.generate("x", QrCodeOptions(mask = 8))
        }.also { assertEquals(true, it.isFailure) }
        kotlin.runCatching {
            QrCodeGenerator.generate("x", QrCodeOptions(version = 41))
        }.also { assertEquals(true, it.isFailure) }
        kotlin.runCatching {
            QrCodeGenerator.generate("x".repeat(5000), QrCodeOptions(version = 1))
        }.also { assertEquals(true, it.isFailure) }
    }
}

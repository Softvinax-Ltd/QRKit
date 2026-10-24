package ltd.softvinax.qrkit

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import ltd.softvinax.qrkit.internal.decoder.Binarizer
import ltd.softvinax.qrkit.internal.decoder.QrDecoderCore
import kotlin.math.roundToInt

/**
 * Decodes QR codes from images or raw luminance data.
 *
 * The decoder is implemented in common Kotlin and has no third-party runtime dependencies. It
 * handles rotation, perspective distortion, damaged format information and Reed-Solomon errors.
 */
object QrCodeDecoder {
    /**
     * Decodes the first QR code found in [image]. Transparent pixels are treated as white.
     */
    fun decode(image: ImageBitmap): QrScanResult? {
        if (image.width <= 0 || image.height <= 0) return null
        val pixels = image.toPixelMap()
        val luminance = ByteArray(image.width * image.height)
        var index = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val color = pixels[x, y]
                luminance[index++] = if (color.alpha < 0.5f) {
                    0xFF.toByte()
                } else {
                    val value = (color.red * 0.299f + color.green * 0.587f + color.blue * 0.114f) * 255f
                    value.roundToInt().coerceIn(0, 255).toByte()
                }
            }
        }
        return decodeLuminance(luminance, image.width, image.height)
    }

    /**
     * Decodes a QR code from an 8-bit grayscale image. Each byte is a luminance value, rows are
     * packed without padding.
     */
    fun decodeLuminance(luminance: ByteArray, width: Int, height: Int): QrScanResult? {
        val prepared = Binarizer.prepare(luminance, width, height)
        if (prepared.width == 0 || prepared.height == 0) return null

        QrDecoderCore.decode(Binarizer.adaptive(prepared))?.let { return it }
        return QrDecoderCore.decode(Binarizer.global(prepared))
    }
}

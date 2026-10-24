package com.mahbub.qrkit

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import com.mahbub.qrkit.internal.PngEncoder
import com.mahbub.qrkit.internal.encoder.QrEncoder

/**
 * Error correction level of a QR code. Higher levels survive more damage but store less data.
 */
enum class QrErrorCorrection(internal val formatBits: Int) {
    LOW(1),
    MEDIUM(0),
    QUARTILE(3),
    HIGH(2),
}

/**
 * Options used when generating a QR code.
 *
 * @property errorCorrection error correction level, [QrErrorCorrection.MEDIUM] by default.
 * @property version fixed symbol version (1..40). Use 0 to let the encoder pick the smallest
 *   version that fits the data.
 * @property mask fixed mask pattern (0..7). Use -1 to let the encoder pick the mask with the
 *   lowest penalty score.
 */
data class QrCodeOptions(
    val errorCorrection: QrErrorCorrection = QrErrorCorrection.MEDIUM,
    val version: Int = 0,
    val mask: Int = -1,
)

/**
 * A generated QR code symbol.
 *
 * Coordinates passed to [isDark] are module coordinates, with `(0, 0)` at the top-left corner of
 * the symbol. The matrix does not include a quiet zone; renderers add one.
 */
class QrCode internal constructor(
    val version: Int,
    val size: Int,
    val errorCorrection: QrErrorCorrection,
    val mask: Int,
    private val modules: BooleanArray,
) {
    init {
        require(size == version * 4 + 17) { "Invalid QR size" }
        require(modules.size == size * size) { "Invalid QR matrix" }
    }

    fun isDark(x: Int, y: Int): Boolean {
        require(x in 0 until size && y in 0 until size) { "Module outside QR symbol" }
        return modules[y * size + x]
    }

    /** Returns a copy of the modules as `[y][x]`, `true` meaning a dark module. */
    fun toBooleanArray(): Array<BooleanArray> =
        Array(size) { y -> BooleanArray(size) { x -> modules[y * size + x] } }

    /**
     * Encodes the symbol as an RGBA PNG. The result is transparent where [lightColor] is
     * transparent, so it can be placed on any background.
     *
     * @param scale pixels per QR module, at least 1.
     * @param quietZone width of the light border in modules, at least 4 for reliable scanning.
     */
    fun toPngBytes(
        scale: Int = 8,
        quietZone: Int = 4,
        darkColor: Int = 0xFF000000.toInt(),
        lightColor: Int = 0xFFFFFFFF.toInt(),
    ): ByteArray {
        require(scale >= 1) { "scale must be at least 1" }
        require(quietZone >= 0) { "quietZone must not be negative" }
        val totalModules = size + quietZone * 2
        val pixelSize = totalModules * scale
        return PngEncoder.encode(pixelSize, pixelSize) { x, y ->
            val moduleX = x / scale - quietZone
            val moduleY = y / scale - quietZone
            val dark = moduleX in 0 until size &&
                moduleY in 0 until size &&
                modules[moduleY * size + moduleX]
            if (dark) darkColor else lightColor
        }
    }

    /** Renders the symbol as an [ImageBitmap] using [toPngBytes]. */
    fun toImageBitmap(
        scale: Int = 8,
        quietZone: Int = 4,
        darkColor: Int = 0xFF000000.toInt(),
        lightColor: Int = 0xFFFFFFFF.toInt(),
    ): ImageBitmap = toPngBytes(scale, quietZone, darkColor, lightColor).decodeToImageBitmap()

    override fun toString(): String =
        "QrCode(version=$version, size=$size, errorCorrection=$errorCorrection, mask=$mask)"
}

/** Thrown when the supplied data cannot fit into a QR code with the requested options. */
class QrCodeException(message: String) : IllegalArgumentException(message)

/**
 * Generates QR codes without any third-party runtime dependencies.
 *
 * Numeric and alphanumeric data use the compact QR modes. Everything else is stored as UTF-8
 * bytes (with an ECI 26 designator for non-ASCII text).
 */
object QrCodeGenerator {
    fun generate(data: String, options: QrCodeOptions = QrCodeOptions()): QrCode =
        QrEncoder.encode(data, options)
}

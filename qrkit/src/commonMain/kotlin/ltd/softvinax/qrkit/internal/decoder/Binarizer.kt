package ltd.softvinax.qrkit.internal.decoder

import kotlin.math.max
import kotlin.math.min

/**
 * Turns an 8-bit grayscale image into a black/white matrix.
 *
 * The adaptive threshold uses an integral image, which handles uneven lighting from a camera far
 * better than a single global threshold. A global Otsu threshold is kept as a fallback for images
 * with almost no local variation.
 */
internal object Binarizer {
    private const val MAX_DIMENSION = 2400

    data class Luminance(val data: ByteArray, val width: Int, val height: Int)

    fun prepare(luminance: ByteArray, width: Int, height: Int): Luminance {
        if (width <= 0 || height <= 0 || luminance.size < width * height) {
            return Luminance(ByteArray(0), 0, 0)
        }
        val maxDimension = max(width, height)
        if (maxDimension <= MAX_DIMENSION) return Luminance(luminance, width, height)

        val factor = (maxDimension + MAX_DIMENSION - 1) / MAX_DIMENSION
        val scaledWidth = max(1, width / factor)
        val scaledHeight = max(1, height / factor)
        val result = ByteArray(scaledWidth * scaledHeight)
        for (y in 0 until scaledHeight) {
            val sourceY = y * factor
            val sourceRow = sourceY * width
            val targetRow = y * scaledWidth
            for (x in 0 until scaledWidth) {
                result[targetRow + x] = luminance[sourceRow + x * factor]
            }
        }
        return Luminance(result, scaledWidth, scaledHeight)
    }

    fun adaptive(image: Luminance): BitMatrix {
        val width = image.width
        val height = image.height
        val matrix = BitMatrix(width, height)
        if (width == 0 || height == 0) return matrix

        val stride = width + 1
        val integral = LongArray(stride * (height + 1))
        for (y in 1..height) {
            val rowStart = (y - 1) * width
            var rowSum = 0L
            val integralRow = y * stride
            val previousRow = (y - 1) * stride
            for (x in 1..width) {
                rowSum += image.data[rowStart + x - 1].toInt() and 0xFF
                integral[integralRow + x] = integral[previousRow + x] + rowSum
            }
        }

        val radius = max(20, min(width, height) / 5)
        val offset = 7
        for (y in 0 until height) {
            val y0 = max(0, y - radius)
            val y1 = min(height, y + radius + 1)
            val y0Stride = y0 * stride
            val y1Stride = y1 * stride
            for (x in 0 until width) {
                val x0 = max(0, x - radius)
                val x1 = min(width, x + radius + 1)
                val sum = integral[y1Stride + x1] - integral[y0Stride + x1] -
                    integral[y1Stride + x0] + integral[y0Stride + x0]
                val area = (x1 - x0) * (y1 - y0)
                val threshold = (sum / area) - offset
                val value = image.data[y * width + x].toInt() and 0xFF
                matrix.set(x, y, value < threshold)
            }
        }
        return matrix
    }

    fun global(image: Luminance): BitMatrix {
        val width = image.width
        val height = image.height
        val matrix = BitMatrix(width, height)
        if (width == 0 || height == 0) return matrix

        val histogram = IntArray(256)
        for (value in image.data) histogram[value.toInt() and 0xFF]++

        val total = width * height
        var sum = 0.0
        for (i in 0 until 256) sum += i.toDouble() * histogram[i]

        var sumBackground = 0.0
        var weightBackground = 0
        var bestVariance = -1.0
        var threshold = 128

        for (i in 0 until 256) {
            weightBackground += histogram[i]
            if (weightBackground == 0) continue
            val weightForeground = total - weightBackground
            if (weightForeground == 0) break
            sumBackground += i.toDouble() * histogram[i]
            val meanBackground = sumBackground / weightBackground
            val meanForeground = (sum - sumBackground) / weightForeground
            val meanDifference = meanBackground - meanForeground
            val variance = weightBackground.toDouble() * weightForeground * meanDifference * meanDifference
            if (variance > bestVariance) {
                bestVariance = variance
                threshold = i
            }
        }

        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                matrix.set(x, y, (image.data[row + x].toInt() and 0xFF) <= threshold)
            }
        }
        return matrix
    }
}

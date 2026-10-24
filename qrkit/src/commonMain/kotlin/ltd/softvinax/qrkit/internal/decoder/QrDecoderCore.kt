package ltd.softvinax.qrkit.internal.decoder

import ltd.softvinax.qrkit.QrErrorCorrection
import ltd.softvinax.qrkit.QrScanResult
import ltd.softvinax.qrkit.internal.encoder.QrMode
import ltd.softvinax.qrkit.internal.encoder.QrVersions
import ltd.softvinax.qrkit.internal.encoder.ReedSolomonDecoder
import kotlin.math.max
import kotlin.math.min

/**
 * Full QR decoding pipeline: locate finder patterns, correct perspective, sample the grid, repair
 * errors and parse the payload.
 */
internal object QrDecoderCore {
    private const val ALPHANUMERIC_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ \$%*+-./:"

    private class FormatInfo(val ecc: QrErrorCorrection, val mask: Int)

    fun decode(matrix: BitMatrix): QrScanResult? {
        val detectedCandidates = FinderDetector.detect(matrix)
        for (detected in detectedCandidates) {
            for (dimension in dimensionCandidates(detected.dimension)) {
                val version = (dimension - 17) / 4
                val alignmentOptions = if (version >= 2) listOf(true, false) else listOf(false)
                for (useAlignment in alignmentOptions) {
                    val grid = sample(matrix, detected, dimension, useAlignment) ?: continue
                    val formats = readFormats(grid, dimension)
                    for (format in formats) {
                        decodeGrid(grid, dimension, version, format.ecc, format.mask)?.let { return it }
                    }
                }
            }
        }
        return null
    }

    private fun dimensionCandidates(base: Int): List<Int> {
        val result = ArrayList<Int>(5)
        result.add(base)
        for (delta in intArrayOf(-4, 4, -8, 8)) {
            val dimension = base + delta
            if (dimension in 21..177 && dimension % 4 == 1) result.add(dimension)
        }
        return result
    }

    private fun sample(
        matrix: BitMatrix,
        detected: DetectedFinders,
        dimension: Int,
        useAlignment: Boolean,
    ): BooleanArray? {
        val version = (dimension - 17) / 4
        val topLeft = detected.topLeft
        val topRight = detected.topRight
        val bottomLeft = detected.bottomLeft

        var bottomRightX = (topRight.x + bottomLeft.x - topLeft.x).toDouble()
        var bottomRightY = (topRight.y + bottomLeft.y - topLeft.y).toDouble()
        var gridBottomRightX = dimension - 3.5
        var gridBottomRightY = dimension - 3.5

        if (useAlignment && version >= 2) {
            val positions = QrVersions.alignmentPositions(version)
            val alignment = positions.last() + 0.5
            val moduleVectorX = (topRight.x - topLeft.x) / (dimension - 7).toDouble()
            val moduleVectorY = (topRight.y - topLeft.y) / (dimension - 7).toDouble()
            val columnVectorX = (bottomLeft.x - topLeft.x) / (dimension - 7).toDouble()
            val columnVectorY = (bottomLeft.y - topLeft.y) / (dimension - 7).toDouble()

            val predictedX = topLeft.x + moduleVectorX * (alignment - 3.5) +
                columnVectorX * (alignment - 3.5)
            val predictedY = topLeft.y + moduleVectorY * (alignment - 3.5) +
                columnVectorY * (alignment - 3.5)

            val found = findAlignmentPattern(
                matrix,
                predictedX,
                predictedY,
                moduleVectorX,
                moduleVectorY,
                columnVectorX,
                columnVectorY,
            ) ?: return null

            bottomRightX = found.first
            bottomRightY = found.second
            gridBottomRightX = alignment
            gridBottomRightY = alignment
        }

        val transform = PerspectiveTransform.create(
            doubleArrayOf(3.5, dimension - 3.5, 3.5, gridBottomRightX),
            doubleArrayOf(3.5, 3.5, dimension - 3.5, gridBottomRightY),
            doubleArrayOf(
                topLeft.x.toDouble(),
                topRight.x.toDouble(),
                bottomLeft.x.toDouble(),
                bottomRightX,
            ),
            doubleArrayOf(
                topLeft.y.toDouble(),
                topRight.y.toDouble(),
                bottomLeft.y.toDouble(),
                bottomRightY,
            ),
        ) ?: return null

        return sampleGrid(matrix, dimension, transform)
    }

    private fun readFormats(grid: BooleanArray, size: Int): List<FormatInfo> {
        fun get(x: Int, y: Int): Boolean = grid[y * size + x]

        var copyOne = 0
        fun readOne(index: Int, x: Int, y: Int) {
            if (get(x, y)) copyOne = copyOne or (1 shl index)
        }
        for (i in 0..5) readOne(i, 8, i)
        readOne(6, 8, 7)
        readOne(7, 8, 8)
        readOne(8, 7, 8)
        for (i in 9..14) readOne(i, 14 - i, 8)

        var copyTwo = 0
        fun readTwo(index: Int, x: Int, y: Int) {
            if (get(x, y)) copyTwo = copyTwo or (1 shl index)
        }
        for (i in 0..7) readTwo(i, size - 1 - i, 8)
        for (i in 8..14) readTwo(i, 8, size - 15 + i)

        val candidates = ArrayList<Triple<FormatInfo, Int, Int>>()
        for (eccBits in 0..3) {
            for (mask in 0..7) {
                val data = (eccBits shl 3) or mask
                val validBits = QrVersions.bchFormatBits(data)
                val distanceOne = popCount(copyOne xor validBits)
                val distanceTwo = popCount(copyTwo xor validBits)
                val bestCopy = min(distanceOne, distanceTwo)
                if (bestCopy <= 3) {
                    candidates.add(
                        Triple(FormatInfo(eccFromBits(eccBits), mask), distanceOne + distanceTwo, bestCopy),
                    )
                }
            }
        }

        if (candidates.isEmpty()) return allFormats()
        return candidates
            .sortedWith(compareBy({ it.third }, { it.second }))
            .map { it.first }
            .distinctBy { it.ecc to it.mask }
            .take(3)
    }

    private fun allFormats(): List<FormatInfo> {
        val result = ArrayList<FormatInfo>(32)
        for (eccBits in 0..3) {
            for (mask in 0..7) result.add(FormatInfo(eccFromBits(eccBits), mask))
        }
        return result
    }

    private fun eccFromBits(bits: Int): QrErrorCorrection = when (bits) {
        0 -> QrErrorCorrection.MEDIUM
        1 -> QrErrorCorrection.LOW
        2 -> QrErrorCorrection.HIGH
        else -> QrErrorCorrection.QUARTILE
    }

    private fun popCount(value: Int): Int {
        var remaining = value
        var count = 0
        while (remaining != 0) {
            count += remaining and 1
            remaining = remaining ushr 1
        }
        return count
    }

    private fun decodeGrid(
        grid: BooleanArray,
        size: Int,
        version: Int,
        ecc: QrErrorCorrection,
        mask: Int,
    ): QrScanResult? {
        val codewords = readCodewords(grid, size, version, mask) ?: return null
        val data = correctErrors(codewords, version, ecc) ?: return null
        val text = runCatching { parse(data, version) }.getOrNull() ?: return null
        return QrScanResult(
            text = text,
            rawBytes = data,
            contentType = inferContentType(text),
            version = version,
            errorCorrection = ecc,
        )
    }

    private fun readCodewords(
        grid: BooleanArray,
        size: Int,
        version: Int,
        mask: Int,
    ): IntArray? {
        val totalCodewords = QrVersions.totalCodewords(version)
        val totalBits = totalCodewords * 8
        val function = QrVersions.functionModules(version)
        val result = IntArray(totalCodewords)
        var bitIndex = 0

        var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vertical in 0 until size) {
                for (j in 0..1) {
                    val x = right - j
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vertical else vertical
                    val index = y * size + x
                    if (function[index]) continue
                    if (bitIndex < totalBits) {
                        var bit = grid[index]
                        if (QrVersions.maskBit(mask, x, y)) bit = !bit
                        if (bit) {
                            result[bitIndex ushr 3] =
                                result[bitIndex ushr 3] or (1 shl (7 - (bitIndex and 7)))
                        }
                        bitIndex++
                    }
                }
            }
            right -= 2
        }

        return if (bitIndex >= totalBits) result else null
    }

    private fun correctErrors(
        codewords: IntArray,
        version: Int,
        ecc: QrErrorCorrection,
    ): ByteArray? {
        val structure = QrVersions.blockStructure(version, ecc)
        if (codewords.size != structure.totalCodewords) return null

        val blocks = Array(structure.numBlocks) { IntArray(structure.totalLength(it)) }
        var index = 0
        for (i in 0 until structure.maxDataLength) {
            for (block in 0 until structure.numBlocks) {
                if (i < structure.dataLength(block)) {
                    blocks[block][i] = codewords[index++]
                }
            }
        }
        for (i in 0 until structure.eccPerBlock) {
            for (block in 0 until structure.numBlocks) {
                blocks[block][structure.dataLength(block) + i] = codewords[index++]
            }
        }
        if (index != structure.totalCodewords) return null

        val data = ByteArray(QrVersions.dataCodewords(version, ecc))
        var output = 0
        for (block in blocks) {
            val corrected = ReedSolomonDecoder.decode(block, structure.eccPerBlock) ?: return null
            for (value in corrected) {
                if (output >= data.size) return null
                data[output++] = value.toByte()
            }
        }
        return if (output == data.size) data else null
    }

    private fun parse(data: ByteArray, version: Int): String? {
        val source = BitSource(data)
        val text = StringBuilder()
        var eci: Int? = null

        while (source.available() >= 4) {
            val mode = source.readBits(4) ?: break
            when (mode) {
                0 -> break
                1 -> {
                    val count = source.readBits(QrVersions.characterCountBits(QrMode.NUMERIC, version))
                        ?: return null
                    var remaining = count
                    while (remaining >= 3) {
                        val value = source.readBits(10) ?: return null
                        if (value > 999) return null
                        text.append(value.toString().padStart(3, '0'))
                        remaining -= 3
                    }
                    if (remaining == 2) {
                        val value = source.readBits(7) ?: return null
                        if (value > 99) return null
                        text.append(value.toString().padStart(2, '0'))
                    } else if (remaining == 1) {
                        val value = source.readBits(4) ?: return null
                        if (value > 9) return null
                        text.append(value)
                    }
                }
                2 -> {
                    val count = source.readBits(
                        QrVersions.characterCountBits(QrMode.ALPHANUMERIC, version),
                    ) ?: return null
                    var remaining = count
                    while (remaining >= 2) {
                        val value = source.readBits(11) ?: return null
                        if (value >= 45 * 45) return null
                        text.append(ALPHANUMERIC_CHARS[value / 45])
                        text.append(ALPHANUMERIC_CHARS[value % 45])
                        remaining -= 2
                    }
                    if (remaining == 1) {
                        val value = source.readBits(6) ?: return null
                        if (value >= 45) return null
                        text.append(ALPHANUMERIC_CHARS[value])
                    }
                }
                3 -> {
                    // Structured append: sequence indicator (8 bits) + parity (8 bits).
                    source.readBits(16) ?: return null
                }
                4 -> {
                    val count = source.readBits(QrVersions.characterCountBits(QrMode.BYTE, version))
                        ?: return null
                    if (count > source.available() / 8) return null
                    val bytes = ByteArray(count)
                    for (i in 0 until count) {
                        val value = source.readBits(8) ?: return null
                        bytes[i] = value.toByte()
                    }
                    text.append(decodeBytes(bytes, eci))
                }
                5 -> {
                    // FNC1 in first position, followed by an application indicator.
                    source.readBits(8) ?: return null
                }
                7 -> {
                    eci = readEci(source) ?: return null
                }
                8, 13 -> return null // Kanji and Hanzi modes are not supported.
                9 -> {
                    // FNC1 in second position.
                    source.readBits(8) ?: return null
                }
                else -> return null
            }
        }

        return text.toString()
    }

    private fun readEci(source: BitSource): Int? {
        val first = source.readBits(8) ?: return null
        return when {
            first and 0x80 == 0 -> first
            first and 0xC0 == 0x80 -> ((first and 0x3F) shl 8) or (source.readBits(8) ?: return null)
            first and 0xE0 == 0xC0 -> ((first and 0x1F) shl 16) or (source.readBits(16) ?: return null)
            else -> null
        }
    }

    private fun decodeBytes(bytes: ByteArray, eci: Int?): String {
        if (eci == 3 || eci == 27) return latin1(bytes)
        val utf8 = runCatching { bytes.decodeToString(throwOnInvalidSequence = true) }.getOrNull()
        return utf8 ?: latin1(bytes)
    }

    private fun latin1(bytes: ByteArray): String = buildString(bytes.size) {
        for (byte in bytes) append((byte.toInt() and 0xFF).toChar())
    }

    private class BitSource(private val bytes: ByteArray) {
        private var bitOffset = 0

        fun available(): Int = bytes.size * 8 - bitOffset

        fun readBits(count: Int): Int? {
            if (count < 0 || count > 32 || available() < count) return null
            var result = 0
            repeat(count) {
                val bit = (bytes[bitOffset ushr 3].toInt() ushr (7 - (bitOffset and 7))) and 1
                result = (result shl 1) or bit
                bitOffset++
            }
            return result
        }
    }
}

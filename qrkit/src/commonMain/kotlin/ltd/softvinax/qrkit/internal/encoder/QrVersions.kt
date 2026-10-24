package ltd.softvinax.qrkit.internal.encoder

import ltd.softvinax.qrkit.QrErrorCorrection
import kotlin.math.abs

/**
 * Version tables and layout helpers shared by the encoder and decoder.
 *
 * The tables follow ISO/IEC 18004 and are indexed `[errorCorrection.ordinal][version]`.
 */
internal object QrVersions {
    const val MIN_VERSION = 1
    const val MAX_VERSION = 40
    const val MIN_SIZE = 21
    const val MAX_SIZE = 177

    private val ECC_CODEWORDS_PER_BLOCK = arrayOf(
        intArrayOf(-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
        intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28),
        intArrayOf(-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
        intArrayOf(-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
    )

    private val NUM_ECC_BLOCKS = arrayOf(
        intArrayOf(-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25),
        intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49),
        intArrayOf(-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68),
        intArrayOf(-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81),
    )

    fun eccCodewordsPerBlock(version: Int, ecc: QrErrorCorrection): Int {
        requireVersion(version)
        return ECC_CODEWORDS_PER_BLOCK[ecc.ordinal][version]
    }

    fun eccBlockCount(version: Int, ecc: QrErrorCorrection): Int {
        requireVersion(version)
        return NUM_ECC_BLOCKS[ecc.ordinal][version]
    }

    fun totalCodewords(version: Int): Int = rawDataModules(version) / 8

    fun dataCodewords(version: Int, ecc: QrErrorCorrection): Int =
        totalCodewords(version) - eccCodewordsPerBlock(version, ecc) * eccBlockCount(version, ecc)

    fun dataCapacityBits(version: Int, ecc: QrErrorCorrection): Int =
        dataCodewords(version, ecc) * 8

    fun sizeForVersion(version: Int): Int {
        requireVersion(version)
        return version * 4 + 17
    }

    fun versionForSize(size: Int): Int {
        require(size in MIN_SIZE..MAX_SIZE && (size - 17) % 4 == 0) { "Invalid QR symbol size" }
        return (size - 17) / 4
    }

    fun alignmentPositions(version: Int): IntArray {
        requireVersion(version)
        if (version == 1) return IntArray(0)
        val numAlign = version / 7 + 2
        val step = (version * 8 + numAlign * 3 + 5) / (numAlign * 4 - 4) * 2
        val result = IntArray(numAlign)
        result[0] = 6
        var pos = version * 4 + 10
        for (i in numAlign - 1 downTo 1) {
            result[i] = pos
            pos -= step
        }
        return result
    }

    /**
     * Number of data bits available after removing function patterns, including remainder bits.
     */
    fun rawDataModules(version: Int): Int {
        requireVersion(version)
        var result = (16 * version + 128) * version + 64
        if (version >= 2) {
            val numAlign = version / 7 + 2
            result -= (25 * numAlign - 10) * numAlign - 55
            if (version >= 7) result -= 36
        }
        return result
    }

    fun characterCountBits(mode: QrMode, version: Int): Int {
        val group = if (version <= 9) 0 else if (version <= 26) 1 else 2
        return when (mode) {
            QrMode.NUMERIC -> intArrayOf(10, 12, 14)[group]
            QrMode.ALPHANUMERIC -> intArrayOf(9, 11, 13)[group]
            QrMode.BYTE -> intArrayOf(8, 16, 16)[group]
            QrMode.KANJI -> intArrayOf(8, 10, 12)[group]
        }
    }

    fun requireVersion(version: Int) {
        require(version in MIN_VERSION..MAX_VERSION) { "QR version must be between 1 and 40" }
    }

    /** Returns true when the square lies on the border of the symbol. */
    fun isFinderCorner(x: Int, y: Int, size: Int): Boolean {
        val nearLeft = x < 8
        val nearRight = x >= size - 8
        val nearTop = y < 8
        val nearBottom = y >= size - 8
        return (nearLeft && nearTop) || (nearRight && nearTop) || (nearLeft && nearBottom)
    }

    fun bchFormatBits(data: Int): Int {
        var rem = data
        repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
        return ((data shl 10) or rem) xor 0x5412
    }

    fun bchVersionBits(version: Int): Int {
        var rem = version
        repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
        return (version shl 12) or rem
    }

    /**
     * Returns the mask bit that is XORed into data module `(x, y)`.
     */
    fun maskBit(mask: Int, x: Int, y: Int): Boolean = when (mask) {
        0 -> (x + y) % 2 == 0
        1 -> y % 2 == 0
        2 -> x % 3 == 0
        3 -> (x + y) % 3 == 0
        4 -> (x / 3 + y / 2) % 2 == 0
        5 -> x * y % 2 + x * y % 3 == 0
        6 -> (x * y % 2 + x * y % 3) % 2 == 0
        7 -> ((x + y) % 2 + x * y % 3) % 2 == 0
        else -> throw IllegalArgumentException("Mask must be between 0 and 7")
    }

    /**
     * Marks every function module of a symbol. Both encoder and decoder rely on this so data
     * modules are read from exactly the same positions.
     */
    fun functionModules(version: Int): BooleanArray {
        val size = sizeForVersion(version)
        val result = BooleanArray(size * size)

        fun mark(x: Int, y: Int) {
            if (x in 0 until size && y in 0 until size) result[y * size + x] = true
        }

        // Timing patterns run across the whole symbol; finder patterns are drawn over them later.
        for (i in 0 until size) {
            mark(i, 6)
            mark(6, i)
        }

        fun markFinder(cx: Int, cy: Int) {
            for (dy in -4..4) {
                for (dx in -4..4) mark(cx + dx, cy + dy)
            }
        }
        markFinder(3, 3)
        markFinder(size - 4, 3)
        markFinder(3, size - 4)

        val positions = alignmentPositions(version)
        if (positions.isNotEmpty()) {
            for (i in positions.indices) {
                for (j in positions.indices) {
                    val isCorner =
                        (i == 0 && j == 0) ||
                            (i == 0 && j == positions.lastIndex) ||
                            (i == positions.lastIndex && j == 0)
                    if (isCorner) continue
                    for (dy in -2..2) {
                        for (dx in -2..2) mark(positions[i] + dx, positions[j] + dy)
                    }
                }
            }
        }

        // Format information, both copies.
        for (i in 0..8) {
            mark(8, i)
            mark(i, 8)
        }
        for (i in 0..7) mark(size - 1 - i, 8)
        for (i in 8..14) mark(8, size - 15 + i)
        mark(8, size - 8)

        // Version information, both copies.
        if (version >= 7) {
            for (i in 0..17) {
                val a = size - 11 + i % 3
                val b = i / 3
                mark(a, b)
                mark(b, a)
            }
        }

        return result
    }

    /**
     * Splits data codewords into the block structure required by the version/ECC level.
     */
    fun blockStructure(version: Int, ecc: QrErrorCorrection): BlockStructure {
        val total = totalCodewords(version)
        val numBlocks = eccBlockCount(version, ecc)
        val eccPerBlock = eccCodewordsPerBlock(version, ecc)
        val shortBlockTotal = total / numBlocks
        val numShortBlocks = numBlocks - total % numBlocks
        val shortDataLength = shortBlockTotal - eccPerBlock
        return BlockStructure(
            totalCodewords = total,
            numBlocks = numBlocks,
            eccPerBlock = eccPerBlock,
            numShortBlocks = numShortBlocks,
            shortBlockTotal = shortBlockTotal,
            shortDataLength = shortDataLength,
        )
    }

    /** Penalty score used to pick the best mask pattern. Lower is better. */
    fun penalty(modules: BooleanArray, size: Int): Int {
        var result = 0

        fun isDark(x: Int, y: Int) = modules[y * size + x]

        // Rule 1: runs of five or more modules of the same colour.
        for (y in 0 until size) {
            var runColour = isDark(0, y)
            var runLength = 1
            for (x in 1 until size) {
                val colour = isDark(x, y)
                if (colour == runColour) {
                    runLength++
                } else {
                    if (runLength >= 5) result += 3 + (runLength - 5)
                    runColour = colour
                    runLength = 1
                }
            }
            if (runLength >= 5) result += 3 + (runLength - 5)
        }
        for (x in 0 until size) {
            var runColour = isDark(x, 0)
            var runLength = 1
            for (y in 1 until size) {
                val colour = isDark(x, y)
                if (colour == runColour) {
                    runLength++
                } else {
                    if (runLength >= 5) result += 3 + (runLength - 5)
                    runColour = colour
                    runLength = 1
                }
            }
            if (runLength >= 5) result += 3 + (runLength - 5)
        }

        // Rule 2: 2x2 blocks of the same colour.
        for (y in 0 until size - 1) {
            for (x in 0 until size - 1) {
                val colour = isDark(x, y)
                if (colour == isDark(x + 1, y) &&
                    colour == isDark(x, y + 1) &&
                    colour == isDark(x + 1, y + 1)
                ) {
                    result += 3
                }
            }
        }

        // Rule 3: finder-like 1:1:3:1:1 patterns with four light modules on either side.
        val finderPattern = intArrayOf(1, 0, 1, 1, 1, 0, 1, 0, 0, 0, 0)
        val reversePattern = finderPattern.reversedArray()
        fun matches(x: Int, y: Int, dx: Int, dy: Int, pattern: IntArray): Boolean {
            for (i in pattern.indices) {
                val px = x + dx * i
                val py = y + dy * i
                if (isDark(px, py) != (pattern[i] == 1)) return false
            }
            return true
        }
        for (y in 0 until size) {
            for (x in 0..size - 11) {
                if (matches(x, y, 1, 0, finderPattern) || matches(x, y, 1, 0, reversePattern)) {
                    result += 40
                }
            }
        }
        for (x in 0 until size) {
            for (y in 0..size - 11) {
                if (matches(x, y, 0, 1, finderPattern) || matches(x, y, 0, 1, reversePattern)) {
                    result += 40
                }
            }
        }

        // Rule 4: proportion of dark modules.
        var dark = 0
        for (value in modules) if (value) dark++
        val total = size * size
        val deviation = abs(dark * 20 - total * 10)
        result += (deviation / total) * 10

        return result
    }
}

internal data class BlockStructure(
    val totalCodewords: Int,
    val numBlocks: Int,
    val eccPerBlock: Int,
    val numShortBlocks: Int,
    val shortBlockTotal: Int,
    val shortDataLength: Int,
) {
    val longDataLength: Int get() = shortDataLength + 1
    val maxDataLength: Int get() = if (numShortBlocks < numBlocks) longDataLength else shortDataLength

    fun dataLength(blockIndex: Int): Int =
        if (blockIndex < numShortBlocks) shortDataLength else longDataLength

    fun totalLength(blockIndex: Int): Int = dataLength(blockIndex) + eccPerBlock
}

internal enum class QrMode(val indicator: Int) {
    NUMERIC(0x1),
    ALPHANUMERIC(0x2),
    BYTE(0x4),
    KANJI(0x8),
}

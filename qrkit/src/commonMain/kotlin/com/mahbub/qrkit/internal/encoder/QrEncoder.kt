package com.mahbub.qrkit.internal.encoder

import com.mahbub.qrkit.QrCode
import com.mahbub.qrkit.QrCodeException
import com.mahbub.qrkit.QrCodeOptions
import com.mahbub.qrkit.QrErrorCorrection
import kotlin.math.abs

internal object QrEncoder {
    private const val ALPHANUMERIC_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ \$%*+-./:"
    private const val ECI_UTF8 = 26

    private class BitBuffer {
        private var data = ByteArray(64)
        var size = 0
            private set

        private fun ensureCapacity(extraBits: Int) {
            val requiredBytes = (size + extraBits + 7) / 8
            if (requiredBytes > data.size) {
                var newSize = data.size
                while (newSize < requiredBytes) newSize *= 2
                data = data.copyOf(newSize)
            }
        }

        fun appendBit(bit: Boolean) {
            ensureCapacity(1)
            if (bit) data[size ushr 3] = (data[size ushr 3].toInt() or (1 shl (7 - (size and 7)))).toByte()
            size++
        }

        fun appendBits(value: Int, count: Int) {
            for (i in count - 1 downTo 0) appendBit(((value ushr i) and 1) != 0)
        }

        fun toByteArray(): ByteArray {
            require(size % 8 == 0) { "Bit buffer is not byte aligned" }
            return data.copyOf(size / 8)
        }
    }

    private class Segment(
        val mode: QrMode,
        val characterCount: Int,
        val dataBits: Int,
        val bytes: ByteArray,
        val digits: String,
        val alphanumeric: String,
        val useEci: Boolean,
    ) {
        fun appendTo(buffer: BitBuffer) {
            if (useEci) {
                buffer.appendBits(0x7, 4)
                buffer.appendBits(ECI_UTF8, 8)
            }
            buffer.appendBits(mode.indicator, 4)
            buffer.appendBits(characterCount, QrVersions.characterCountBits(mode, versionForCount))
            when (mode) {
                QrMode.NUMERIC -> {
                    var i = 0
                    while (i < digits.length) {
                        val remaining = digits.length - i
                        if (remaining >= 3) {
                            buffer.appendBits(digits.substring(i, i + 3).toInt(), 10)
                            i += 3
                        } else if (remaining == 2) {
                            buffer.appendBits(digits.substring(i, i + 2).toInt(), 7)
                            i += 2
                        } else {
                            buffer.appendBits(digits.substring(i, i + 1).toInt(), 4)
                            i += 1
                        }
                    }
                }
                QrMode.ALPHANUMERIC -> {
                    var i = 0
                    while (i < alphanumeric.length) {
                        if (i + 1 < alphanumeric.length) {
                            val first = ALPHANUMERIC_CHARS.indexOf(alphanumeric[i])
                            val second = ALPHANUMERIC_CHARS.indexOf(alphanumeric[i + 1])
                            buffer.appendBits(first * 45 + second, 11)
                            i += 2
                        } else {
                            buffer.appendBits(ALPHANUMERIC_CHARS.indexOf(alphanumeric[i]), 6)
                            i += 1
                        }
                    }
                }
                QrMode.BYTE -> for (byte in bytes) buffer.appendBits(byte.toInt() and 0xFF, 8)
                QrMode.KANJI -> error("Kanji mode is not produced by this encoder")
            }
        }

        // Set immediately before appending; the character count width depends on the version.
        var versionForCount: Int = 1
    }

    fun encode(text: String, options: QrCodeOptions): QrCode {
        val ecc = options.errorCorrection
        if (options.version != 0) QrVersions.requireVersion(options.version)
        if (options.mask != -1 && options.mask !in 0..7) {
            throw QrCodeException("Mask must be between 0 and 7, or -1 for automatic selection")
        }

        val segment = analyse(text)
        val version = selectVersion(segment, options.version, ecc)
        segment.versionForCount = version

        val capacityBytes = QrVersions.dataCodewords(version, ecc)
        val capacityBits = capacityBytes * 8
        val buffer = BitBuffer()
        segment.appendTo(buffer)

        if (buffer.size > capacityBits) {
            throw QrCodeException(
                "The data is too long for QR version $version with ${ecc.name} error correction",
            )
        }

        val terminator = minOf(4, capacityBits - buffer.size)
        repeat(terminator) { buffer.appendBit(false) }
        while (buffer.size % 8 != 0) buffer.appendBit(false)

        val data = buffer.toByteArray()
        val padded = IntArray(capacityBytes)
        for (i in data.indices) padded[i] = data[i].toInt() and 0xFF
        var padIndex = data.size
        var padToggle = true
        while (padIndex < padded.size) {
            padded[padIndex++] = if (padToggle) 0xEC else 0x11
            padToggle = !padToggle
        }

        val codewords = interleave(padded, version, ecc)
        val size = QrVersions.sizeForVersion(version)
        val function = QrVersions.functionModules(version)
        val base = buildBaseMatrix(size, version)

        placeData(base, function, size, codewords)

        val mask = chooseMask(base, function, size, ecc, options.mask)
        val masked = applyMask(base, function, size, mask)
        drawFormatBits(masked, size, ecc, mask)

        return QrCode(
            version = version,
            size = size,
            errorCorrection = ecc,
            mask = mask,
            modules = BooleanArray(masked.size) { masked[it] != 0.toByte() },
        )
    }

    private fun analyse(text: String): Segment {
        val numeric = text.isNotEmpty() && text.all { it in '0'..'9' }
        if (numeric) {
            val bits = (text.length / 3) * 10 + when (text.length % 3) {
                1 -> 4
                2 -> 7
                else -> 0
            }
            return Segment(QrMode.NUMERIC, text.length, bits, ByteArray(0), text, "", false)
        }

        val alphanumeric = text.isNotEmpty() && text.all { it in ALPHANUMERIC_CHARS }
        if (alphanumeric) {
            val bits = (text.length / 2) * 11 + (text.length % 2) * 6
            return Segment(QrMode.ALPHANUMERIC, text.length, bits, ByteArray(0), "", text, false)
        }

        val bytes = text.encodeToByteArray()
        val nonAscii = text.any { it.code > 0x7F }
        return Segment(QrMode.BYTE, bytes.size, bytes.size * 8, bytes, "", "", nonAscii)
    }

    private fun selectVersion(segment: Segment, requested: Int, ecc: QrErrorCorrection): Int {
        if (requested != 0) {
            val overhead = 4 + (if (segment.useEci) 12 else 0) +
                QrVersions.characterCountBits(segment.mode, requested)
            if (overhead + segment.dataBits > QrVersions.dataCapacityBits(requested, ecc)) {
                throw QrCodeException(
                    "The data does not fit in QR version $requested with ${ecc.name} error correction",
                )
            }
            return requested
        }

        for (version in QrVersions.MIN_VERSION..QrVersions.MAX_VERSION) {
            val overhead = 4 + (if (segment.useEci) 12 else 0) +
                QrVersions.characterCountBits(segment.mode, version)
            if (overhead + segment.dataBits <= QrVersions.dataCapacityBits(version, ecc)) {
                return version
            }
        }
        throw QrCodeException("The data is too long to fit in a QR code with ${ecc.name} error correction")
    }

    private fun interleave(data: IntArray, version: Int, ecc: QrErrorCorrection): IntArray {
        val structure = QrVersions.blockStructure(version, ecc)
        val dataBlocks = Array(structure.numBlocks) { IntArray(structure.dataLength(it)) }
        var offset = 0
        for (block in dataBlocks.indices) {
            for (i in dataBlocks[block].indices) {
                dataBlocks[block][i] = data[offset++]
            }
        }
        require(offset == data.size) { "Data codeword count mismatch" }

        val eccBlocks = Array(structure.numBlocks) { block ->
            ReedSolomonEncoder.computeRemainder(dataBlocks[block], structure.eccPerBlock)
        }

        val result = IntArray(structure.totalCodewords)
        var index = 0
        for (i in 0 until structure.maxDataLength) {
            for (block in 0 until structure.numBlocks) {
                if (i < dataBlocks[block].size) result[index++] = dataBlocks[block][i]
            }
        }
        for (i in 0 until structure.eccPerBlock) {
            for (block in 0 until structure.numBlocks) {
                result[index++] = eccBlocks[block][i]
            }
        }
        require(index == result.size) { "Interleaved codeword count mismatch" }
        return result
    }

    private fun buildBaseMatrix(size: Int, version: Int): ByteArray {
        val modules = ByteArray(size * size)

        fun set(x: Int, y: Int, dark: Boolean) {
            if (x in 0 until size && y in 0 until size) {
                modules[y * size + x] = if (dark) 1 else 0
            }
        }

        // Timing patterns. Finder patterns overwrite the overlapping portions.
        for (i in 0 until size) {
            set(i, 6, i % 2 == 0)
            set(6, i, i % 2 == 0)
        }

        fun finder(cx: Int, cy: Int) {
            for (dy in -4..4) {
                for (dx in -4..4) {
                    if (cx + dx !in 0 until size || cy + dy !in 0 until size) continue
                    val distance = maxOf(abs(dx), abs(dy))
                    set(cx + dx, cy + dy, distance != 2 && distance != 4)
                }
            }
        }
        finder(3, 3)
        finder(size - 4, 3)
        finder(3, size - 4)

        val positions = QrVersions.alignmentPositions(version)
        if (positions.isNotEmpty()) {
            for (i in positions.indices) {
                for (j in positions.indices) {
                    val corner =
                        (i == 0 && j == 0) ||
                            (i == 0 && j == positions.lastIndex) ||
                            (i == positions.lastIndex && j == 0)
                    if (corner) continue
                    val cx = positions[i]
                    val cy = positions[j]
                    for (dy in -2..2) {
                        for (dx in -2..2) {
                            set(cx + dx, cy + dy, maxOf(abs(dx), abs(dy)) != 1)
                        }
                    }
                }
            }
        }

        set(8, size - 8, true)

        if (version >= 7) {
            val bits = QrVersions.bchVersionBits(version)
            for (i in 0..17) {
                val bit = ((bits ushr i) and 1) != 0
                val a = size - 11 + i % 3
                val b = i / 3
                set(a, b, bit)
                set(b, a, bit)
            }
        }

        return modules
    }

    private fun placeData(modules: ByteArray, function: BooleanArray, size: Int, codewords: IntArray) {
        val totalBits = codewords.size * 8
        var bitIndex = 0

        var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vertical in 0 until size) {
                for (j in 0..1) {
                    val x = right - j
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vertical else vertical
                    if (function[y * size + x]) continue
                    if (bitIndex < totalBits) {
                        val byte = codewords[bitIndex ushr 3]
                        val dark = ((byte ushr (7 - (bitIndex and 7))) and 1) != 0
                        modules[y * size + x] = if (dark) 1 else 0
                        bitIndex++
                    }
                }
            }
            right -= 2
        }
    }

    private fun applyMask(
        modules: ByteArray,
        function: BooleanArray,
        size: Int,
        mask: Int,
    ): ByteArray {
        val result = modules.copyOf()
        for (y in 0 until size) {
            for (x in 0 until size) {
                val index = y * size + x
                if (function[index]) continue
                if (QrVersions.maskBit(mask, x, y)) {
                    result[index] = (result[index].toInt() xor 1).toByte()
                }
            }
        }
        return result
    }

    private fun chooseMask(
        base: ByteArray,
        function: BooleanArray,
        size: Int,
        ecc: QrErrorCorrection,
        requested: Int,
    ): Int {
        if (requested != -1) return requested
        var bestMask = 0
        var bestPenalty = Int.MAX_VALUE
        for (mask in 0..7) {
            val candidate = applyMask(base, function, size, mask)
            drawFormatBits(candidate, size, ecc, mask)
            val penalty = QrVersions.penalty(
                BooleanArray(candidate.size) { candidate[it] != 0.toByte() },
                size,
            )
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                bestMask = mask
            }
        }
        return bestMask
    }

    private fun drawFormatBits(
        modules: ByteArray,
        size: Int,
        ecc: QrErrorCorrection,
        mask: Int,
    ) {
        fun set(x: Int, y: Int, dark: Boolean) {
            if (x in 0 until size && y in 0 until size) {
                modules[y * size + x] = if (dark) 1 else 0
            }
        }

        val data = (ecc.formatBits shl 3) or mask
        val bits = QrVersions.bchFormatBits(data)
        fun bit(index: Int) = ((bits ushr index) and 1) != 0

        for (i in 0..5) set(8, i, bit(i))
        set(8, 7, bit(6))
        set(8, 8, bit(7))
        set(7, 8, bit(8))
        for (i in 9..14) set(14 - i, 8, bit(i))

        for (i in 0..7) set(size - 1 - i, 8, bit(i))
        for (i in 8..14) set(8, size - 15 + i, bit(i))

        set(8, size - 8, true)
    }
}

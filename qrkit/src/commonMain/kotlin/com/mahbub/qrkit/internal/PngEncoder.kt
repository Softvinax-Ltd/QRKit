package com.mahbub.qrkit.internal

import kotlin.math.min

/**
 * Minimal PNG encoder for RGBA images. Uses uncompressed DEFLATE blocks, so no platform
 * dependencies are required.
 */
internal object PngEncoder {
    private val CRC_TABLE = IntArray(256) { n ->
        var c = n
        repeat(8) {
            c = if (c and 1 != 0) 0xEDB88320.toInt() xor (c ushr 1) else c ushr 1
        }
        c
    }

    private class ByteSink(initialCapacity: Int = 1024) {
        private var data = ByteArray(initialCapacity)
        var size = 0
            private set

        private fun ensure(extra: Int) {
            if (size + extra <= data.size) return
            var capacity = data.size
            while (capacity < size + extra) capacity *= 2
            data = data.copyOf(capacity)
        }

        fun writeByte(value: Int) {
            ensure(1)
            data[size++] = (value and 0xFF).toByte()
        }

        fun writeBytes(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) {
            ensure(length)
            bytes.copyInto(data, size, offset, offset + length)
            size += length
        }

        fun writeInt32BE(value: Int) {
            writeByte(value ushr 24)
            writeByte(value ushr 16)
            writeByte(value ushr 8)
            writeByte(value)
        }

        fun toByteArray(): ByteArray = data.copyOf(size)
    }

    fun encode(width: Int, height: Int, pixel: (x: Int, y: Int) -> Int): ByteArray {
        require(width > 0 && height > 0) { "Image dimensions must be positive" }

        val rowBytes = 1 + width * 4
        val raw = ByteArray(rowBytes * height)
        var index = 0
        for (y in 0 until height) {
            raw[index++] = 0 // filter type: none
            for (x in 0 until width) {
                val color = pixel(x, y)
                raw[index++] = ((color ushr 16) and 0xFF).toByte()
                raw[index++] = ((color ushr 8) and 0xFF).toByte()
                raw[index++] = (color and 0xFF).toByte()
                raw[index++] = ((color ushr 24) and 0xFF).toByte()
            }
        }

        val zlib = ByteSink(raw.size / 2 + 64)
        zlib.writeByte(0x78)
        zlib.writeByte(0x01)
        var offset = 0
        while (offset < raw.size) {
            val length = min(65535, raw.size - offset)
            val isFinal = offset + length >= raw.size
            zlib.writeByte(if (isFinal) 1 else 0)
            zlib.writeByte(length and 0xFF)
            zlib.writeByte((length ushr 8) and 0xFF)
            val inverted = length xor 0xFFFF
            zlib.writeByte(inverted and 0xFF)
            zlib.writeByte((inverted ushr 8) and 0xFF)
            zlib.writeBytes(raw, offset, length)
            offset += length
        }
        zlib.writeInt32BE(adler32(raw))

        val out = ByteSink(width * height / 2 + 256)
        out.writeBytes(
            byteArrayOf(
                0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            ),
        )

        val ihdr = ByteSink(13)
        ihdr.writeInt32BE(width)
        ihdr.writeInt32BE(height)
        ihdr.writeByte(8) // bit depth
        ihdr.writeByte(6) // colour type: RGBA
        ihdr.writeByte(0) // compression
        ihdr.writeByte(0) // filter
        ihdr.writeByte(0) // interlace
        writeChunk(out, "IHDR", ihdr.toByteArray())
        writeChunk(out, "IDAT", zlib.toByteArray())
        writeChunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun adler32(data: ByteArray): Int {
        var a = 1
        var b = 0
        for (byte in data) {
            a = (a + (byte.toInt() and 0xFF)) % 65521
            b = (b + a) % 65521
        }
        return (b shl 16) or a
    }

    private fun writeChunk(out: ByteSink, type: String, data: ByteArray) {
        out.writeInt32BE(data.size)
        var crc = -1
        for (char in type) {
            val value = char.code
            out.writeByte(value)
            crc = updateCrc(crc, value)
        }
        out.writeBytes(data)
        for (byte in data) crc = updateCrc(crc, byte.toInt() and 0xFF)
        out.writeInt32BE(crc.inv())
    }

    private fun updateCrc(current: Int, value: Int): Int =
        CRC_TABLE[(current xor value) and 0xFF] xor (current ushr 8)
}

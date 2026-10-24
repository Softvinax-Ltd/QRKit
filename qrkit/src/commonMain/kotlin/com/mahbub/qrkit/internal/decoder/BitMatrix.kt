package com.mahbub.qrkit.internal.decoder

internal class BitMatrix(val width: Int, val height: Int) {
    private val data = BooleanArray(width * height)

    fun get(x: Int, y: Int): Boolean = data[y * width + x]

    fun getOrNull(x: Int, y: Int): Boolean? =
        if (x in 0 until width && y in 0 until height) data[y * width + x] else null

    fun set(x: Int, y: Int, dark: Boolean) {
        data[y * width + x] = dark
    }
}

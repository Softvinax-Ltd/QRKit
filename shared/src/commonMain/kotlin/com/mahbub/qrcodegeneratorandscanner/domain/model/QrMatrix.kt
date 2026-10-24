package com.mahbub.qrcodegeneratorandscanner.domain.model

/**
 * Immutable QR module matrix. `(0, 0)` is the top-left module and the quiet zone is not included.
 */
class QrMatrix(
    val size: Int,
    private val modules: BooleanArray,
) {
    init {
        require(size > 0) { "size must be positive" }
        require(modules.size == size * size) { "modules must contain size * size entries" }
    }

    fun isDark(x: Int, y: Int): Boolean =
        x in 0 until size && y in 0 until size && modules[y * size + x]
}

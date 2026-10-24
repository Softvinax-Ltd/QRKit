package com.mahbub.qrkit.internal.decoder

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Projective mapping from QR module coordinates to image coordinates.
 */
internal class PerspectiveTransform private constructor(private val m: DoubleArray) {
    fun mapX(x: Double, y: Double): Double {
        val denominator = m[6] * x + m[7] * y + 1.0
        return (m[0] * x + m[1] * y + m[2]) / denominator
    }

    fun mapY(x: Double, y: Double): Double {
        val denominator = m[6] * x + m[7] * y + 1.0
        return (m[3] * x + m[4] * y + m[5]) / denominator
    }

    companion object {
        /**
         * Solves the 8 coefficients of the homography that maps four source points to four
         * destination points. Returns null for degenerate quadrilaterals.
         */
        fun create(
            sourceX: DoubleArray,
            sourceY: DoubleArray,
            destinationX: DoubleArray,
            destinationY: DoubleArray,
        ): PerspectiveTransform? {
            if (sourceX.size != 4 || sourceY.size != 4 ||
                destinationX.size != 4 || destinationY.size != 4
            ) {
                return null
            }

            val matrix = Array(8) { DoubleArray(9) }
            for (i in 0 until 4) {
                val x = sourceX[i]
                val y = sourceY[i]
                val u = destinationX[i]
                val v = destinationY[i]
                matrix[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -x * u, -y * u, u)
                matrix[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -x * v, -y * v, v)
            }

            for (column in 0 until 8) {
                var pivot = column
                for (row in column + 1 until 8) {
                    if (abs(matrix[row][column]) > abs(matrix[pivot][column])) pivot = row
                }
                if (abs(matrix[pivot][column]) < 1e-10) return null
                if (pivot != column) {
                    val temp = matrix[column]
                    matrix[column] = matrix[pivot]
                    matrix[pivot] = temp
                }

                val divisor = matrix[column][column]
                for (c in column..8) matrix[column][c] /= divisor

                for (row in 0 until 8) {
                    if (row == column) continue
                    val factor = matrix[row][column]
                    if (factor == 0.0) continue
                    for (c in column..8) matrix[row][c] -= factor * matrix[column][c]
                }
            }

            return PerspectiveTransform(DoubleArray(8) { matrix[it][8] })
        }
    }
}

/**
 * Searches for the bottom-right alignment pattern inside a small window around an estimated
 * position. The 5x5 template is sampled along the local module axes, which also works with mild
 * perspective distortion.
 */
internal fun findAlignmentPattern(
    matrix: BitMatrix,
    predictedX: Double,
    predictedY: Double,
    moduleVectorX: Double,
    moduleVectorY: Double,
    columnVectorX: Double,
    columnVectorY: Double,
): Pair<Double, Double>? {
    var best: Pair<Double, Double>? = null
    var bestMismatch = Int.MAX_VALUE
    var bestDistance = Double.MAX_VALUE

    var offsetY = -5.0
    while (offsetY <= 5.0) {
        var offsetX = -5.0
        while (offsetX <= 5.0) {
            val centerX = predictedX + moduleVectorX * offsetX + columnVectorX * offsetY
            val centerY = predictedY + moduleVectorY * offsetX + columnVectorY * offsetY
            var mismatch = 0

            for (dy in -2..2) {
                for (dx in -2..2) {
                    val sampleX = centerX + moduleVectorX * dx + columnVectorX * dy
                    val sampleY = centerY + moduleVectorY * dx + columnVectorY * dy
                    val dark = sample(matrix, sampleX, sampleY)
                    val expected = max(abs(dx), abs(dy)) != 1
                    mismatch += if (dark == null) 2 else if (dark == expected) 0 else 1
                }
            }

            if (mismatch <= 7) {
                val dx = centerX - predictedX
                val dy = centerY - predictedY
                val distance = dx * dx + dy * dy
                if (mismatch < bestMismatch || (mismatch == bestMismatch && distance < bestDistance)) {
                    bestMismatch = mismatch
                    bestDistance = distance
                    best = centerX to centerY
                }
            }
            offsetX += 0.5
        }
        offsetY += 0.5
    }

    return best
}

private fun sample(matrix: BitMatrix, x: Double, y: Double): Boolean? {
    if (!x.isFinite() || !y.isFinite()) return null
    val ix = round(x).toInt()
    val iy = round(y).toInt()
    return matrix.getOrNull(ix, iy)
}

/**
 * Samples all modules of a QR symbol using [transform].
 */
internal fun sampleGrid(
    matrix: BitMatrix,
    size: Int,
    transform: PerspectiveTransform,
): BooleanArray? {
    val result = BooleanArray(size * size)
    var invalidModules = 0
    val offsets = doubleArrayOf(-0.2, 0.0, 0.2)

    for (y in 0 until size) {
        for (x in 0 until size) {
            var dark = 0
            var valid = 0
            for (offsetY in offsets) {
                for (offsetX in offsets) {
                    val sampleX = transform.mapX(x + 0.5 + offsetX, y + 0.5 + offsetY)
                    val sampleY = transform.mapY(x + 0.5 + offsetX, y + 0.5 + offsetY)
                    if (!sampleX.isFinite() || !sampleY.isFinite()) continue
                    val ix = round(sampleX).toInt()
                    val iy = round(sampleY).toInt()
                    if (ix in 0 until matrix.width && iy in 0 until matrix.height) {
                        valid++
                        if (matrix.get(ix, iy)) dark++
                    }
                }
            }
            if (valid < 5) {
                invalidModules++
                result[y * size + x] = false
            } else {
                result[y * size + x] = dark * 2 >= valid
            }
        }
    }

    if (invalidModules > size * size / 5) return null
    return result
}

internal fun unitVector(dx: Double, dy: Double): Pair<Double, Double> {
    val length = sqrt(dx * dx + dy * dy)
    return if (length == 0.0) 0.0 to 0.0 else dx / length to dy / length
}

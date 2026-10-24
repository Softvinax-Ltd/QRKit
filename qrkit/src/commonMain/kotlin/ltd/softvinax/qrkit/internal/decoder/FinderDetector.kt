package ltd.softvinax.qrkit.internal.decoder

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal class FinderPattern(
    var x: Float,
    var y: Float,
    var moduleSize: Float,
    var count: Int,
)

internal class DetectedFinders(
    val topLeft: FinderPattern,
    val topRight: FinderPattern,
    val bottomLeft: FinderPattern,
    val dimension: Int,
    val moduleSize: Float,
    val score: Float,
)

/**
 * Locates the three finder patterns of a QR code.
 *
 * Rows are scanned for the characteristic 1:1:3:1:1 dark/light pattern. Every hit is cross-checked
 * vertically and horizontally, clustered, and then triples of clusters are evaluated geometrically.
 */
internal object FinderDetector {
    private const val MAX_RESULTS = 3

    fun detect(matrix: BitMatrix): List<DetectedFinders> {
        val width = matrix.width
        val height = matrix.height
        if (width < 21 || height < 21) return emptyList()

        val candidates = ArrayList<FinderPattern>()
        val colours = BooleanArray(5)
        val lengths = IntArray(5)
        val starts = IntArray(5)

        for (y in 0 until height) {
            var runCount = 0
            var x = 0
            while (x < width) {
                val colour = matrix.get(x, y)
                var end = x + 1
                while (end < width && matrix.get(end, y) == colour) end++
                val length = end - x

                if (runCount < 5) {
                    colours[runCount] = colour
                    lengths[runCount] = length
                    starts[runCount] = x
                    runCount++
                } else {
                    for (i in 1 until 5) {
                        colours[i - 1] = colours[i]
                        lengths[i - 1] = lengths[i]
                        starts[i - 1] = starts[i]
                    }
                    colours[4] = colour
                    lengths[4] = length
                    starts[4] = x
                }

                if (runCount == 5 &&
                    colours[0] && !colours[1] && colours[2] && !colours[3] && colours[4] &&
                    foundPatternCross(lengths)
                ) {
                    val total = lengths[0] + lengths[1] + lengths[2] + lengths[3] + lengths[4]
                    val centerX = starts[2] + lengths[2] / 2f
                    val centerY = crossCheckVertical(
                        matrix,
                        y,
                        centerX.roundToInt(),
                        lengths[2],
                        total,
                    )
                    if (!centerY.isNaN()) {
                        val refinedX = crossCheckHorizontal(
                            matrix,
                            centerY.roundToInt(),
                            centerX.roundToInt(),
                            lengths[2],
                            total,
                        )
                        if (!refinedX.isNaN()) {
                            addCandidate(candidates, refinedX, centerY, total / 7f)
                        }
                    }
                }
                x = end
            }
        }

        if (candidates.size < 3) return emptyList()

        val top = candidates
            .sortedWith(compareByDescending<FinderPattern> { it.count }.thenByDescending { it.moduleSize })
            .take(12)

        val results = ArrayList<DetectedFinders>()
        for (i in 0 until top.size - 2) {
            for (j in i + 1 until top.size - 1) {
                for (k in j + 1 until top.size) {
                    val ordered = orderPatterns(top[i], top[j], top[k]) ?: continue
                    val detected = evaluate(ordered.first, ordered.second, ordered.third) ?: continue
                    results.add(detected)
                }
            }
        }

        return results
            .sortedByDescending { it.score }
            .distinctBy {
                "${it.dimension}:${it.topLeft.x.roundToInt()}:${it.topLeft.y.roundToInt()}:" +
                    "${it.topRight.x.roundToInt()}:${it.bottomLeft.y.roundToInt()}"
            }
            .take(MAX_RESULTS)
    }

    private fun foundPatternCross(stateCount: IntArray): Boolean {
        val total = stateCount[0] + stateCount[1] + stateCount[2] + stateCount[3] + stateCount[4]
        if (total < 7) return false
        val moduleSize = total / 7f
        if (moduleSize < 0.7f) return false
        val maxVariance = moduleSize / 2f
        return abs(moduleSize - stateCount[0]) < maxVariance &&
            abs(moduleSize - stateCount[1]) < maxVariance &&
            abs(3f * moduleSize - stateCount[2]) < 3f * maxVariance &&
            abs(moduleSize - stateCount[3]) < maxVariance &&
            abs(moduleSize - stateCount[4]) < maxVariance
    }

    private fun centerFromEnd(stateCount: IntArray, end: Int): Float =
        end - stateCount[4] - stateCount[3] - stateCount[2] / 2f

    private fun crossCheckVertical(
        matrix: BitMatrix,
        startY: Int,
        centerX: Int,
        maxCount: Int,
        originalTotal: Int,
    ): Float {
        val x = centerX.coerceIn(0, matrix.width - 1)
        val state = IntArray(5)
        var y = startY
        while (y >= 0 && matrix.get(x, y)) {
            state[2]++
            y--
        }
        if (y < 0) return Float.NaN
        while (y >= 0 && !matrix.get(x, y) && state[1] <= maxCount) {
            state[1]++
            y--
        }
        if (y < 0 || state[1] > maxCount) return Float.NaN
        while (y >= 0 && matrix.get(x, y) && state[0] <= maxCount) {
            state[0]++
            y--
        }
        if (state[0] > maxCount) return Float.NaN

        y = startY + 1
        while (y < matrix.height && matrix.get(x, y)) {
            state[2]++
            y++
        }
        if (y == matrix.height) return Float.NaN
        while (y < matrix.height && !matrix.get(x, y) && state[3] < maxCount) {
            state[3]++
            y++
        }
        if (y == matrix.height || state[3] >= maxCount) return Float.NaN
        while (y < matrix.height && matrix.get(x, y) && state[4] < maxCount) {
            state[4]++
            y++
        }
        if (state[4] >= maxCount) return Float.NaN

        val total = state[0] + state[1] + state[2] + state[3] + state[4]
        if (5 * abs(total - originalTotal) >= 2 * originalTotal) return Float.NaN
        return if (foundPatternCross(state)) centerFromEnd(state, y) else Float.NaN
    }

    private fun crossCheckHorizontal(
        matrix: BitMatrix,
        centerY: Int,
        centerX: Int,
        maxCount: Int,
        originalTotal: Int,
    ): Float {
        val y = centerY.coerceIn(0, matrix.height - 1)
        val state = IntArray(5)
        val startX = centerX.coerceIn(0, matrix.width - 1)
        var x = startX
        while (x >= 0 && matrix.get(x, y)) {
            state[2]++
            x--
        }
        if (x < 0) return Float.NaN
        while (x >= 0 && !matrix.get(x, y) && state[1] <= maxCount) {
            state[1]++
            x--
        }
        if (x < 0 || state[1] > maxCount) return Float.NaN
        while (x >= 0 && matrix.get(x, y) && state[0] <= maxCount) {
            state[0]++
            x--
        }
        if (state[0] > maxCount) return Float.NaN

        x = startX + 1
        while (x < matrix.width && matrix.get(x, y)) {
            state[2]++
            x++
        }
        if (x == matrix.width) return Float.NaN
        while (x < matrix.width && !matrix.get(x, y) && state[3] < maxCount) {
            state[3]++
            x++
        }
        if (x == matrix.width || state[3] >= maxCount) return Float.NaN
        while (x < matrix.width && matrix.get(x, y) && state[4] < maxCount) {
            state[4]++
            x++
        }
        if (state[4] >= maxCount) return Float.NaN

        val total = state[0] + state[1] + state[2] + state[3] + state[4]
        if (5 * abs(total - originalTotal) >= 2 * originalTotal) return Float.NaN
        return if (foundPatternCross(state)) centerFromEnd(state, x) else Float.NaN
    }

    private fun addCandidate(
        candidates: MutableList<FinderPattern>,
        x: Float,
        y: Float,
        moduleSize: Float,
    ) {
        for (candidate in candidates) {
            val sizeDifference = abs(candidate.moduleSize - moduleSize)
            val closeEnough = sizeDifference <= 1f || sizeDifference <= moduleSize
            if (closeEnough &&
                abs(candidate.x - x) <= moduleSize * 1.5f &&
                abs(candidate.y - y) <= moduleSize * 1.5f
            ) {
                val count = candidate.count
                candidate.x = (candidate.x * count + x) / (count + 1)
                candidate.y = (candidate.y * count + y) / (count + 1)
                candidate.moduleSize = (candidate.moduleSize * count + moduleSize) / (count + 1)
                candidate.count = count + 1
                return
            }
        }
        candidates.add(FinderPattern(x, y, moduleSize, 1))
    }

    private fun orderPatterns(
        a: FinderPattern,
        b: FinderPattern,
        c: FinderPattern,
    ): Triple<FinderPattern, FinderPattern, FinderPattern>? {
        val ab = distance(a, b)
        val ac = distance(a, c)
        val bc = distance(b, c)

        val first: FinderPattern
        val second: FinderPattern
        val corner: FinderPattern
        when {
            ab >= ac && ab >= bc -> {
                first = a
                second = b
                corner = c
            }
            ac >= ab && ac >= bc -> {
                first = a
                second = c
                corner = b
            }
            else -> {
                first = b
                second = c
                corner = a
            }
        }

        var topRight = first
        var bottomLeft = second
        val cross = (topRight.x - corner.x) * (bottomLeft.y - corner.y) -
            (topRight.y - corner.y) * (bottomLeft.x - corner.x)
        if (cross < 0f) {
            val temp = topRight
            topRight = bottomLeft
            bottomLeft = temp
        }
        return Triple(corner, topRight, bottomLeft)
    }

    private fun evaluate(
        topLeft: FinderPattern,
        topRight: FinderPattern,
        bottomLeft: FinderPattern,
    ): DetectedFinders? {
        if (topLeft.moduleSize < 0.7f || topRight.moduleSize < 0.7f || bottomLeft.moduleSize < 0.7f) {
            return null
        }
        val d1 = distance(topLeft, topRight)
        val d2 = distance(topLeft, bottomLeft)
        val d3 = distance(topRight, bottomLeft)
        if (d1 < 2f || d2 < 2f) return null

        val ratio = d1 / d2
        if (ratio < 0.5f || ratio > 2f) return null

        val dot = ((topRight.x - topLeft.x) * (bottomLeft.x - topLeft.x) +
            (topRight.y - topLeft.y) * (bottomLeft.y - topLeft.y)) / (d1 * d2)
        if (abs(dot) > 0.45f) return null
        if (d3 < max(d1, d2) * 0.8f) return null

        val averageModule = (topLeft.moduleSize + topRight.moduleSize + bottomLeft.moduleSize) / 3f
        val moduleDeviation =
            (abs(topLeft.moduleSize - averageModule) +
                abs(topRight.moduleSize - averageModule) +
                abs(bottomLeft.moduleSize - averageModule)) / averageModule
        if (moduleDeviation > 1f) return null

        val rawDimension = (d1 + d2) / (2f * averageModule) + 7f
        val dimension = nearestValidDimension(rawDimension.roundToInt()) ?: return null
        val refinedModule = (d1 + d2) / (2f * (dimension - 7))
        if (abs(refinedModule - averageModule) / averageModule > 0.3f) return null

        val count = topLeft.count + topRight.count + bottomLeft.count
        val geometryError = abs(d1 - d2) / max(d1, d2) + abs(dot)
        val score = count - geometryError * 20f - moduleDeviation * 10f
        return DetectedFinders(topLeft, topRight, bottomLeft, dimension, refinedModule, score)
    }

    private fun nearestValidDimension(raw: Int): Int? {
        var index = floor((raw - 21) / 4.0 + 0.5).toInt()
        index = index.coerceIn(0, (177 - 21) / 4)
        val dimension = 21 + index * 4
        return if (dimension in 21..177) dimension else null
    }

    private fun distance(a: FinderPattern, b: FinderPattern): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }
}

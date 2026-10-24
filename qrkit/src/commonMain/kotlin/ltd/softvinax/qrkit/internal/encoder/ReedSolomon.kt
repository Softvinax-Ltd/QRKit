package ltd.softvinax.qrkit.internal.encoder

/**
 * GF(256) arithmetic and Reed-Solomon coding used by QR codes.
 *
 * Field polynomial: x^8 + x^4 + x^3 + x^2 + 1 (0x11D), primitive element 2.
 */
internal object GaloisField {
    const val PRIMITIVE = 0x11D
    private val expTable = IntArray(512)
    private val logTable = IntArray(256)

    init {
        var value = 1
        for (i in 0 until 255) {
            expTable[i] = value
            logTable[value] = i
            value = value shl 1
            if (value and 0x100 != 0) value = value xor PRIMITIVE
        }
        for (i in 255 until 512) expTable[i] = expTable[i - 255]
    }

    fun exp(power: Int): Int = expTable[power]

    fun log(value: Int): Int {
        require(value != 0) { "log(0) is undefined" }
        return logTable[value]
    }

    fun multiply(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return expTable[logTable[a] + logTable[b]]
    }

    fun inverse(value: Int): Int {
        require(value != 0) { "0 has no multiplicative inverse" }
        return expTable[255 - logTable[value]]
    }
}

/**
 * Reed-Solomon encoder. The generator polynomial uses roots alpha^0 .. alpha^(degree-1), which is
 * the convention used by QR codes.
 */
internal object ReedSolomonEncoder {
    private val generatorCache = HashMap<Int, IntArray>()

    private fun generatorPolynomial(degree: Int): IntArray {
        generatorCache[degree]?.let { return it }
        var generator = intArrayOf(1)
        for (i in 0 until degree) {
            val root = GaloisField.exp(i)
            val next = IntArray(generator.size + 1)
            for (j in generator.indices) {
                next[j] = next[j] xor generator[j]
                next[j + 1] = next[j + 1] xor GaloisField.multiply(generator[j], root)
            }
            generator = next
        }
        generatorCache[degree] = generator
        return generator
    }

    /**
     * Returns the [degree] error correction codewords for [data], in big-endian codeword order.
     */
    fun computeRemainder(data: IntArray, degree: Int): IntArray {
        require(degree > 0)
        val generator = generatorPolynomial(degree)
        val remainder = IntArray(degree)
        for (value in data) {
            val factor = value xor remainder[0]
            for (i in 0 until degree - 1) remainder[i] = remainder[i + 1]
            remainder[degree - 1] = 0
            if (factor != 0) {
                for (i in 0 until degree) {
                    remainder[i] = remainder[i] xor GaloisField.multiply(generator[i + 1], factor)
                }
            }
        }
        return remainder
    }
}

/** A polynomial over GF(256) with coefficients stored from highest to lowest degree. */
internal class GfPoly private constructor(
    private val coefficients: IntArray,
) {
    val degree: Int get() = coefficients.size - 1
    val isZero: Boolean get() = coefficients[0] == 0

    fun coefficient(degree: Int): Int = coefficients[coefficients.size - 1 - degree]

    fun evaluateAt(value: Int): Int {
        if (value == 0) return coefficient(0)
        if (value == 1) {
            var result = 0
            for (c in coefficients) result = result xor c
            return result
        }
        var result = coefficients[0]
        for (i in 1 until coefficients.size) {
            result = GaloisField.multiply(value, result) xor coefficients[i]
        }
        return result
    }

    fun addOrSubtract(other: GfPoly): GfPoly {
        if (isZero) return other
        if (other.isZero) return this
        val smaller = if (coefficients.size <= other.coefficients.size) this else other
        val larger = if (coefficients.size <= other.coefficients.size) other else this
        val diff = larger.coefficients.size - smaller.coefficients.size
        val result = larger.coefficients.copyOf()
        for (i in smaller.coefficients.indices) {
            result[i + diff] = result[i + diff] xor smaller.coefficients[i]
        }
        return fromCoefficients(result)
    }

    fun multiply(other: GfPoly): GfPoly {
        if (isZero || other.isZero) return ZERO
        val result = IntArray(coefficients.size + other.coefficients.size - 1)
        for (i in coefficients.indices) {
            if (coefficients[i] == 0) continue
            for (j in other.coefficients.indices) {
                result[i + j] = result[i + j] xor
                    GaloisField.multiply(coefficients[i], other.coefficients[j])
            }
        }
        return GfPoly(result)
    }

    fun multiplyByScalar(scalar: Int): GfPoly {
        if (scalar == 0 || isZero) return ZERO
        if (scalar == 1) return this
        return GfPoly(IntArray(coefficients.size) { GaloisField.multiply(coefficients[it], scalar) })
    }

    fun multiplyByMonomial(degree: Int, coefficient: Int): GfPoly {
        require(degree >= 0)
        if (coefficient == 0 || isZero) return ZERO
        val result = IntArray(coefficients.size + degree)
        for (i in coefficients.indices) {
            result[i] = GaloisField.multiply(coefficients[i], coefficient)
        }
        return GfPoly(result)
    }

    companion object {
        val ZERO = GfPoly(intArrayOf(0))
        val ONE = GfPoly(intArrayOf(1))

        fun fromCoefficients(coefficients: IntArray): GfPoly {
            var start = 0
            while (start < coefficients.lastIndex && coefficients[start] == 0) start++
            return GfPoly(coefficients.copyOfRange(start, coefficients.size))
        }

        fun monomial(degree: Int, coefficient: Int): GfPoly {
            if (coefficient == 0) return ZERO
            val result = IntArray(degree + 1)
            result[0] = coefficient
            return GfPoly(result)
        }
    }
}

/**
 * Reed-Solomon decoder using the Euclidean algorithm. Corrects up to `eccCount / 2` corrupted
 * codewords in place and returns the data portion of the block.
 */
internal object ReedSolomonDecoder {
    fun decode(received: IntArray, eccCount: Int): IntArray? {
        if (eccCount <= 0) return null
        val poly = GfPoly.fromCoefficients(received)
        val syndromes = IntArray(eccCount)
        var hasError = false
        for (i in 0 until eccCount) {
            val evaluation = poly.evaluateAt(GaloisField.exp(i))
            syndromes[eccCount - 1 - i] = evaluation
            if (evaluation != 0) hasError = true
        }
        if (!hasError) return received.copyOfRange(0, received.size - eccCount)

        val syndromePoly = GfPoly.fromCoefficients(syndromes)
        val (errorLocator, errorEvaluator) = try {
            runEuclideanAlgorithm(GfPoly.monomial(eccCount, 1), syndromePoly, eccCount)
        } catch (_: ArithmeticException) {
            return null
        } ?: return null

        val errorLocations = findErrorLocations(errorLocator) ?: return null
        val errorMagnitudes = findErrorMagnitudes(errorEvaluator, errorLocations) ?: return null

        for (i in errorLocations.indices) {
            val position = received.size - 1 - GaloisField.log(errorLocations[i])
            if (position < 0 || position >= received.size) return null
            received[position] = received[position] xor errorMagnitudes[i]
        }
        return received.copyOfRange(0, received.size - eccCount)
    }

    private fun runEuclideanAlgorithm(
        a: GfPoly,
        b: GfPoly,
        r: Int,
    ): Pair<GfPoly, GfPoly>? {
        var rLast = a
        var rCurrent = b
        if (rLast.degree < rCurrent.degree) {
            val temp = rLast
            rLast = rCurrent
            rCurrent = temp
        }

        var tLast = GfPoly.ZERO
        var tCurrent = GfPoly.ONE

        while (rCurrent.degree >= r / 2) {
            val rLastLast = rLast
            val tLastLast = tLast
            rLast = rCurrent
            tLast = tCurrent

            if (rLast.isZero) return null
            rCurrent = rLastLast

            var q = GfPoly.ZERO
            val denominatorLeadingTerm = rLast.coefficient(rLast.degree)
            val dltInverse = GaloisField.inverse(denominatorLeadingTerm)
            while (rCurrent.degree >= rLast.degree && !rCurrent.isZero) {
                val degreeDiff = rCurrent.degree - rLast.degree
                val scale = GaloisField.multiply(rCurrent.coefficient(rCurrent.degree), dltInverse)
                q = q.addOrSubtract(GfPoly.monomial(degreeDiff, scale))
                rCurrent = rCurrent.addOrSubtract(rLast.multiplyByMonomial(degreeDiff, scale))
            }

            tCurrent = q.multiply(tLast).addOrSubtract(tLastLast)
            if (rCurrent.degree >= rLast.degree) return null
        }

        val sigmaTildeAtZero = tCurrent.coefficient(0)
        if (sigmaTildeAtZero == 0) return null
        val inverse = GaloisField.inverse(sigmaTildeAtZero)
        val sigma = tCurrent.multiplyByScalar(inverse)
        val omega = rCurrent.multiplyByScalar(inverse)
        return sigma to omega
    }

    private fun findErrorLocations(errorLocator: GfPoly): IntArray? {
        val numErrors = errorLocator.degree
        if (numErrors == 1) return intArrayOf(errorLocator.coefficient(1))
        val result = IntArray(numErrors)
        var count = 0
        for (i in 1 until 256) {
            if (errorLocator.evaluateAt(i) == 0) {
                if (count >= result.size) return null
                result[count++] = GaloisField.inverse(i)
            }
        }
        return if (count == numErrors) result else null
    }

    private fun findErrorMagnitudes(
        errorEvaluator: GfPoly,
        errorLocations: IntArray,
    ): IntArray? {
        val result = IntArray(errorLocations.size)
        for (i in errorLocations.indices) {
            val xiInverse = GaloisField.inverse(errorLocations[i])
            var denominator = 1
            for (j in errorLocations.indices) {
                if (i == j) continue
                denominator = GaloisField.multiply(
                    denominator,
                    1 xor GaloisField.multiply(errorLocations[j], xiInverse),
                )
            }
            if (denominator == 0) return null
            result[i] = GaloisField.multiply(
                errorEvaluator.evaluateAt(xiInverse),
                GaloisField.inverse(denominator),
            )
        }
        return result
    }
}

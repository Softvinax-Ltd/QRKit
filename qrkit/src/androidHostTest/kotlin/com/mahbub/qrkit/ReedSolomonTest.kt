package com.mahbub.qrkit

import com.mahbub.qrkit.internal.encoder.ReedSolomonDecoder
import com.mahbub.qrkit.internal.encoder.ReedSolomonEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ReedSolomonTest {
    @Test
    fun correctsErrorsUpToCapacity() {
        val data = intArrayOf(32, 91, 11, 120, 209, 114, 220, 77, 67, 64, 236, 17, 236, 17, 236, 17)
        val ecc = ReedSolomonEncoder.computeRemainder(data, 10)
        println("ecc=${ecc.joinToString(",")}")
        val block = data + ecc
        for (errorCount in 0..5) {
            val corrupted = block.copyOf()
            for (i in 0 until errorCount) {
                val position = (i * 5 + 1) % corrupted.size
                corrupted[position] = corrupted[position] xor (0x55 + i)
            }
            val decoded = ReedSolomonDecoder.decode(corrupted, 10)
            println("errors=$errorCount decoded=${decoded?.joinToString(",")}")
            assertNotNull(decoded, "Failed to correct $errorCount errors")
            assertEquals(data.toList(), decoded.toList())
        }
    }

    @Test
    fun correctsSingleByteError() {
        val data = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val ecc = ReedSolomonEncoder.computeRemainder(data, 8)
        val block = data + ecc
        for (position in block.indices) {
            val corrupted = block.copyOf()
            corrupted[position] = corrupted[position] xor 0x42
            val decoded = ReedSolomonDecoder.decode(corrupted, 8)
            assertEquals(data.toList(), decoded?.toList(), "Failed at position $position")
        }
    }
}

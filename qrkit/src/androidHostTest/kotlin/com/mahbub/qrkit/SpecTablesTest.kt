package com.mahbub.qrkit

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.decoder.Version
import com.mahbub.qrkit.internal.encoder.QrVersions
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Compares the shared version tables and block structure against ZXing's implementation of the
 * ISO/IEC 18004 tables.
 */
class SpecTablesTest {
    private val mapping = mapOf(
        QrErrorCorrection.LOW to ErrorCorrectionLevel.L,
        QrErrorCorrection.MEDIUM to ErrorCorrectionLevel.M,
        QrErrorCorrection.QUARTILE to ErrorCorrectionLevel.Q,
        QrErrorCorrection.HIGH to ErrorCorrectionLevel.H,
    )

    @Test
    fun versionTablesMatchSpec() {
        val problems = mutableListOf<String>()
        for (version in 1..40) {
            val zx = Version.getVersionForNumber(version)
            if (QrVersions.totalCodewords(version) != zx.totalCodewords) {
                problems += "v$version total ours=${QrVersions.totalCodewords(version)} zx=${zx.totalCodewords}"
            }
            val ourAlignment = QrVersions.alignmentPositions(version).toList()
            val zxAlignment = zx.alignmentPatternCenters.toList()
            if (ourAlignment != zxAlignment) {
                problems += "v$version alignment ours=$ourAlignment zx=$zxAlignment"
            }

            for ((ours, theirs) in mapping) {
                val ec = zx.getECBlocksForLevel(theirs)
                val ourEcc = QrVersions.eccCodewordsPerBlock(version, ours)
                val ourBlocks = QrVersions.eccBlockCount(version, ours)
                val zxEcc = ec.ecCodewordsPerBlock
                val zxBlocks = ec.ecBlocks.sumOf { it.count }
                if (ourEcc != zxEcc || ourBlocks != zxBlocks) {
                    problems += "v$version ${ours.name} ecc/blocks ours=$ourEcc/$ourBlocks zx=$zxEcc/$zxBlocks"
                }

                val structure = QrVersions.blockStructure(version, ours)
                val ourDataLengths = (0 until structure.numBlocks).map { structure.dataLength(it) }
                val zxDataLengths = ec.ecBlocks.flatMap { block ->
                    List(block.count) { block.dataCodewords }
                }
                if (ourDataLengths != zxDataLengths) {
                    problems += "v$version ${ours.name} dataLengths ours=$ourDataLengths zx=$zxDataLengths"
                }
            }
        }
        assertEquals(emptyList(), problems)
    }

    @Test
    fun formatInformationMatchesSpec() {
        // ISO/IEC 18004 Table C.1, indexed by ECC format bits (M=0, L=1, H=2, Q=3) and mask.
        val specFormatBits = arrayOf(
            intArrayOf(0x5412, 0x5125, 0x5E7C, 0x5B4B, 0x45F9, 0x40CE, 0x4F97, 0x4AA0),
            intArrayOf(0x77C4, 0x72F3, 0x7DAA, 0x789D, 0x662F, 0x6318, 0x6C41, 0x6976),
            intArrayOf(0x1689, 0x13BE, 0x1CE7, 0x19D0, 0x0762, 0x0255, 0x0D0C, 0x083B),
            intArrayOf(0x355F, 0x3068, 0x3F31, 0x3A06, 0x24B4, 0x2183, 0x2EDA, 0x2BED),
        )
        val problems = mutableListOf<String>()
        for ((ours, _) in mapping) {
            for (mask in 0..7) {
                val actual = QrVersions.bchFormatBits((ours.formatBits shl 3) or mask)
                val expected = specFormatBits[ours.formatBits][mask]
                if (actual != expected) {
                    problems += "${ours.name}/$mask ours=${actual.toString(2)} spec=${expected.toString(2)}"
                }
            }
        }
        assertEquals(emptyList(), problems)
    }
}

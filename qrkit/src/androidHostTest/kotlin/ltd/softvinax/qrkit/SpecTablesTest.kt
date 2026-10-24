package ltd.softvinax.qrkit

import ltd.softvinax.qrkit.internal.encoder.QrVersions
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * Guards the ISO/IEC 18004 tables used by both the encoder and the decoder.
 */
class SpecTablesTest {
    @Test
    fun totalCodewordsMatchSpec() {
        val expected = intArrayOf(
            26, 44, 70, 100, 134, 172, 196, 242, 292, 346,
            404, 466, 532, 581, 655, 733, 815, 901, 991, 1085,
            1156, 1258, 1364, 1474, 1588, 1706, 1828, 1921, 2051, 2185,
            2323, 2465, 2611, 2761, 2876, 3034, 3196, 3362, 3532, 3706,
        )
        for (version in 1..40) {
            assertEquals(
                expected[version - 1],
                QrVersions.totalCodewords(version),
                "version $version total codewords",
            )
        }
    }

    @Test
    fun alignmentPositionsMatchSpec() {
        val expected = listOf(
            intArrayOf(),
            intArrayOf(6, 18),
            intArrayOf(6, 22),
            intArrayOf(6, 26),
            intArrayOf(6, 30),
            intArrayOf(6, 34),
            intArrayOf(6, 22, 38),
            intArrayOf(6, 24, 42),
            intArrayOf(6, 26, 46),
            intArrayOf(6, 28, 50),
            intArrayOf(6, 30, 54),
            intArrayOf(6, 32, 58),
            intArrayOf(6, 34, 62),
            intArrayOf(6, 26, 46, 66),
            intArrayOf(6, 26, 48, 70),
            intArrayOf(6, 26, 50, 74),
            intArrayOf(6, 30, 54, 78),
            intArrayOf(6, 30, 56, 82),
            intArrayOf(6, 30, 58, 86),
            intArrayOf(6, 34, 62, 90),
            intArrayOf(6, 28, 50, 72, 94),
            intArrayOf(6, 26, 50, 74, 98),
            intArrayOf(6, 30, 54, 78, 102),
            intArrayOf(6, 28, 54, 80, 106),
            intArrayOf(6, 32, 58, 84, 110),
            intArrayOf(6, 30, 58, 86, 114),
            intArrayOf(6, 34, 62, 90, 118),
            intArrayOf(6, 26, 50, 74, 98, 122),
            intArrayOf(6, 30, 54, 78, 102, 126),
            intArrayOf(6, 26, 52, 78, 104, 130),
            intArrayOf(6, 30, 56, 82, 108, 134),
            intArrayOf(6, 34, 60, 86, 112, 138),
            intArrayOf(6, 30, 58, 86, 114, 142),
            intArrayOf(6, 34, 62, 90, 118, 146),
            intArrayOf(6, 30, 54, 78, 102, 126, 150),
            intArrayOf(6, 24, 50, 76, 102, 128, 154),
            intArrayOf(6, 28, 54, 80, 106, 132, 158),
            intArrayOf(6, 32, 58, 84, 110, 136, 162),
            intArrayOf(6, 26, 54, 82, 110, 138, 166),
            intArrayOf(6, 30, 58, 86, 114, 142, 170),
        )
        for (version in 1..40) {
            assertContentEquals(
                expected[version - 1],
                QrVersions.alignmentPositions(version),
                "version $version alignment positions",
            )
        }
    }

    @Test
    fun dataCodewordsMatchSpec() {
        val expected = mapOf(
            QrErrorCorrection.LOW to intArrayOf(
                19, 34, 55, 80, 108, 136, 156, 194, 232, 274,
                324, 370, 428, 461, 523, 589, 647, 721, 795, 861,
                932, 1006, 1094, 1174, 1276, 1370, 1468, 1531, 1631, 1735,
                1843, 1955, 2071, 2191, 2306, 2434, 2566, 2702, 2812, 2956,
            ),
            QrErrorCorrection.MEDIUM to intArrayOf(
                16, 28, 44, 64, 86, 108, 124, 154, 182, 216,
                254, 290, 334, 365, 415, 453, 507, 563, 627, 669,
                714, 782, 860, 914, 1000, 1062, 1128, 1193, 1267, 1373,
                1455, 1541, 1631, 1725, 1812, 1914, 1992, 2102, 2216, 2334,
            ),
            QrErrorCorrection.QUARTILE to intArrayOf(
                13, 22, 34, 48, 62, 76, 88, 110, 132, 154,
                180, 206, 244, 261, 295, 325, 367, 397, 445, 485,
                512, 568, 614, 664, 718, 754, 808, 871, 911, 985,
                1033, 1115, 1171, 1231, 1286, 1354, 1426, 1502, 1582, 1666,
            ),
            QrErrorCorrection.HIGH to intArrayOf(
                9, 16, 26, 36, 46, 60, 66, 86, 100, 122,
                140, 158, 180, 197, 223, 253, 283, 313, 341, 385,
                406, 442, 464, 514, 538, 596, 628, 661, 701, 745,
                793, 845, 901, 961, 986, 1054, 1096, 1142, 1222, 1276,
            ),
        )
        for ((level, values) in expected) {
            for (version in 1..40) {
                assertEquals(
                    values[version - 1],
                    QrVersions.dataCodewords(version, level),
                    "$level version $version data codewords",
                )
            }
        }
    }

    @Test
    fun blockStructureFillsDataAndTotalCodewords() {
        for (version in 1..40) {
            for (level in QrErrorCorrection.entries) {
                val structure = QrVersions.blockStructure(version, level)
                val data = (0 until structure.numBlocks).sumOf { structure.dataLength(it) }
                val total = (0 until structure.numBlocks).sumOf { structure.totalLength(it) }
                assertEquals(
                    QrVersions.dataCodewords(version, level),
                    data,
                    "$level version $version block data codewords",
                )
                assertEquals(
                    QrVersions.totalCodewords(version),
                    total,
                    "$level version $version block total codewords",
                )
            }
        }
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
        val levels = listOf(
            QrErrorCorrection.LOW,
            QrErrorCorrection.MEDIUM,
            QrErrorCorrection.QUARTILE,
            QrErrorCorrection.HIGH,
        )
        val problems = mutableListOf<String>()
        for (level in levels) {
            for (mask in 0..7) {
                val actual = QrVersions.bchFormatBits((level.formatBits shl 3) or mask)
                val expected = specFormatBits[level.formatBits][mask]
                if (actual != expected) {
                    problems += "${level.name}/$mask ours=${actual.toString(2)} spec=${expected.toString(2)}"
                }
            }
        }
        assertEquals(emptyList(), problems)
    }
}

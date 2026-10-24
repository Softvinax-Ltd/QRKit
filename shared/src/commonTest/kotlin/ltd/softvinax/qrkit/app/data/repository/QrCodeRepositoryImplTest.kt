package ltd.softvinax.qrkit.app.data.repository

import ltd.softvinax.qrkit.app.domain.model.GeneratedQr
import ltd.softvinax.qrkit.app.domain.model.QrErrorLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QrCodeRepositoryImplTest {

    private val repository = QrCodeRepositoryImpl()

    @Test
    fun encodesPayloadAndMapsMetadata() {
        val generated = repository.generate("https://example.com", QrErrorLevel.QUARTILE).getOrThrow()

        assertEquals("https://example.com", generated.data)
        assertEquals(QrErrorLevel.QUARTILE, generated.errorLevel)
        assertEquals(generated.version * 4 + 17, generated.matrix.size)
        assertTrue(darkModuleCount(generated) > 0)
    }

    @Test
    fun oversizedPayloadFails() {
        val result = repository.generate("A".repeat(8_000), QrErrorLevel.HIGH)

        assertTrue(result.isFailure)
    }

    @Test
    fun coordinatesOutsideTheSymbolAreNotDark() {
        val generated = repository.generate("hello", QrErrorLevel.LOW).getOrThrow()

        assertFalse(generated.matrix.isDark(-1, 0))
        assertFalse(generated.matrix.isDark(generated.matrix.size, 0))
    }

    private fun darkModuleCount(qr: GeneratedQr): Int {
        val size = qr.matrix.size
        return (0 until size).sumOf { y ->
            (0 until size).count { x -> qr.matrix.isDark(x, y) }
        }
    }
}

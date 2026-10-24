package ltd.softvinax.qrkit.app.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ltd.softvinax.qrkit.app.domain.model.QrMatrix
import kotlin.math.floor

@Composable
fun QrMatrixImage(
    matrix: QrMatrix,
    modifier: Modifier = Modifier,
    darkColor: Color = Color.Black,
    lightColor: Color = Color.White,
    quietZone: Int = 3,
    contentDescription: String? = null,
) {
    val semanticsModifier = if (contentDescription == null) {
        Modifier
    } else {
        Modifier.semantics { this.contentDescription = contentDescription }
    }

    Canvas(modifier = modifier.then(semanticsModifier)) {
        drawRect(color = lightColor)

        val totalModules = matrix.size + quietZone * 2
        val cellSize = (size.minDimension / totalModules).toInt().coerceAtLeast(1).toFloat()
        val contentSize = cellSize * totalModules
        val originX = floor((size.width - contentSize) / 2f) + cellSize * quietZone
        val originY = floor((size.height - contentSize) / 2f) + cellSize * quietZone

        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (matrix.isDark(x, y)) {
                    drawRect(
                        color = darkColor,
                        topLeft = Offset(originX + x * cellSize, originY + y * cellSize),
                        size = Size(cellSize, cellSize),
                    )
                }
            }
        }
    }
}

package com.mahbub.qrkit

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/**
 * Live camera preview that scans QR codes and reports decoded results.
 *
 * On Android this uses Camera 2; on iOS it uses AVFoundation with Vision. Both implementations
 * request camera access when needed, but apps must still declare the platform usage
 * description/permission in their manifests (see the library README).
 *
 * @param isActive when false, frames are still previewed but decoding is paused.
 * @param showOverlay draws a translucent scan frame over the preview.
 * @param onResult invoked on the main thread when a new code is decoded. Repeated reads of the same
 *   payload are debounced.
 * @param onError invoked with a human-readable message when the camera cannot start.
 */
@Composable
expect fun QrScannerView(
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    showOverlay: Boolean = true,
    onResult: (QrScanResult) -> Unit,
    onError: (String) -> Unit = {},
)

/**
 * Decorative scanner frame: dimmed surroundings, four corner brackets and an animated scan line.
 */
@Composable
fun QrScannerOverlay(
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFF4ADE80),
    dimColor: Color = Color.Black.copy(alpha = 0.45f),
) {
    val transition = rememberInfiniteTransition(label = "qr-scan-overlay")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scan-line",
    )

    Canvas(modifier) {
        val side = minOf(size.width, size.height) * 0.7f
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        val frame = Rect(left, top, left + side, top + side)

        val dim = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, size.width, size.height))
            addRect(frame)
        }
        drawPath(dim, dimColor)

        val stroke = 4.dp.toPx()
        val corner = side * 0.12f
        val inset = stroke / 2f
        val corners = listOf(
            Triple(
                Offset(frame.left + inset, frame.top + inset + corner),
                Offset(frame.left + inset, frame.top + inset),
                Offset(frame.left + inset + corner, frame.top + inset),
            ),
            Triple(
                Offset(frame.right - inset - corner, frame.top + inset),
                Offset(frame.right - inset, frame.top + inset),
                Offset(frame.right - inset, frame.top + inset + corner),
            ),
            Triple(
                Offset(frame.right - inset, frame.bottom - inset - corner),
                Offset(frame.right - inset, frame.bottom - inset),
                Offset(frame.right - inset - corner, frame.bottom - inset),
            ),
            Triple(
                Offset(frame.left + inset + corner, frame.bottom - inset),
                Offset(frame.left + inset, frame.bottom - inset),
                Offset(frame.left + inset, frame.bottom - inset - corner),
            ),
        )
        for ((start, cornerPoint, end) in corners) {
            drawLine(accentColor, start, cornerPoint, strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(accentColor, cornerPoint, end, strokeWidth = stroke, cap = StrokeCap.Round)
        }

        val lineY = frame.top + side * progress
        drawLine(
            color = accentColor.copy(alpha = 0.9f),
            start = Offset(frame.left + side * 0.06f, lineY),
            end = Offset(frame.right - side * 0.06f, lineY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

package com.mahbub.qrkit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Draws a QR code. The composable fills a square area, adds a quiet zone and draws each run of
 * dark modules as a single rectangle so large symbols stay crisp and cheap to render.
 */
@Composable
fun QrCodeImage(
    qrCode: QrCode,
    modifier: Modifier = Modifier,
    darkColor: Color = Color.Black,
    lightColor: Color = Color.White,
    quietZone: Int = 4,
    contentDescription: String? = null,
) {
    require(quietZone >= 0) { "quietZone must not be negative" }
    val semanticsModifier = if (contentDescription == null) {
        Modifier
    } else {
        Modifier.semantics { this.contentDescription = contentDescription }
    }

    Canvas(modifier.aspectRatio(1f).then(semanticsModifier)) {
        drawRect(color = lightColor)
        val totalModules = qrCode.size + quietZone * 2
        val moduleSize = minOf(size.width, size.height) / totalModules
        val originX = (size.width - moduleSize * totalModules) / 2f
        val originY = (size.height - moduleSize * totalModules) / 2f

        for (y in 0 until qrCode.size) {
            var x = 0
            while (x < qrCode.size) {
                if (!qrCode.isDark(x, y)) {
                    x++
                    continue
                }
                val start = x
                while (x < qrCode.size && qrCode.isDark(x, y)) x++
                drawRect(
                    color = darkColor,
                    topLeft = Offset(
                        originX + (quietZone + start) * moduleSize,
                        originY + (quietZone + y) * moduleSize,
                    ),
                    size = Size((x - start) * moduleSize, moduleSize),
                )
            }
        }
    }
}

/**
 * Generates and draws a QR code for [data]. Generation errors are not caught: if the data does not
 * fit, [QrCodeException] is thrown during composition.
 */
@Composable
fun QrCodeImage(
    data: String,
    modifier: Modifier = Modifier,
    options: QrCodeOptions = QrCodeOptions(),
    darkColor: Color = Color.Black,
    lightColor: Color = Color.White,
    quietZone: Int = 4,
    contentDescription: String? = null,
) {
    val qrCode = remember(data, options) { QrCodeGenerator.generate(data, options) }
    QrCodeImage(
        qrCode = qrCode,
        modifier = modifier,
        darkColor = darkColor,
        lightColor = lightColor,
        quietZone = quietZone,
        contentDescription = contentDescription,
    )
}

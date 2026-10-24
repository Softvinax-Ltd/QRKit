package com.mahbub.qrcodegeneratorandscanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mahbub.qrkit.QrCodeGenerator
import com.mahbub.qrkit.QrCodeImage
import com.mahbub.qrkit.QrCodeOptions
import com.mahbub.qrkit.QrErrorCorrection
import com.mahbub.qrkit.QrScanResult
import com.mahbub.qrkit.QrScannerView

private val Background = Color(0xFF0B1020)
private val SurfaceColor = Color(0xFF151B2E)
private val Accent = Color(0xFF4ADE80)
private val MutedText = Color(0xFF9AA3B8)
private val CodeInk = Color(0xFF0B1020)

@Composable
fun App() {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color(0xFF04140A),
            background = Background,
            onBackground = Color(0xFFF5F7FB),
            surface = SurfaceColor,
            onSurface = Color(0xFFE7EAF3),
            surfaceVariant = Color(0xFF1E2740),
            onSurfaceVariant = Color(0xFFB9C0D4),
        ),
    ) {
        var tab by remember { mutableStateOf(0) }

        Surface(modifier = Modifier.fillMaxSize(), color = Background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeContentPadding(),
            ) {
                Header()
                PrimaryTabRow(
                    selectedTabIndex = tab,
                    containerColor = Background,
                    contentColor = Accent,
                ) {
                    Tab(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        text = { Text("Generate") },
                    )
                    Tab(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        text = { Text("Scan") },
                    )
                }
                when (tab) {
                    0 -> GenerateScreen(Modifier.weight(1f))
                    else -> ScanScreen(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)) {
        Text(
            text = "QRKit",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Text(
            text = "Generate and scan QR codes offline, on Android and iOS.",
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenerateScreen(modifier: Modifier) {
    var content by remember { mutableStateOf("https://github.com/mahbubalam") }
    var errorCorrection by remember { mutableStateOf(QrErrorCorrection.MEDIUM) }

    val generation = remember(content, errorCorrection) {
        runCatching {
            QrCodeGenerator.generate(
                data = content,
                options = QrCodeOptions(errorCorrection = errorCorrection),
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Text, link or any payload") },
            minLines = 2,
            maxLines = 5,
            shape = RoundedCornerShape(16.dp),
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Error correction",
                style = MaterialTheme.typography.titleSmall,
                color = MutedText,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QrErrorCorrection.entries.forEach { level ->
                    FilterChip(
                        selected = errorCorrection == level,
                        onClick = { errorCorrection = level },
                        label = { Text(level.label()) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Accent.copy(alpha = 0.18f),
                            selectedLabelColor = Accent,
                        ),
                    )
                }
            }
        }

        generation.fold(
            onSuccess = { qrCode ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        QrCodeImage(
                            qrCode = qrCode,
                            modifier = Modifier.fillMaxWidth(),
                            darkColor = CodeInk,
                            lightColor = Color.White,
                            quietZone = 3,
                            contentDescription = "QR code for $content",
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Version ${qrCode.version} · ${qrCode.size}×${qrCode.size} " +
                                "modules · ${errorCorrection.label()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF667085),
                        )
                    }
                }
            },
            onFailure = { error ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "This payload cannot be encoded: ${error.message}",
                        modifier = Modifier.padding(16.dp),
                        color = Color(0xFFFCA5A5),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
        )
    }
}

@Composable
private fun ScanScreen(modifier: Modifier) {
    var result by remember { mutableStateOf<QrScanResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        QrScannerView(
            modifier = Modifier.fillMaxSize(),
            onResult = {
                result = it
                error = null
            },
            onError = { error = it },
        )

        error?.let { message ->
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(16.dp),
                    color = Color(0xFFFCA5A5),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        result?.let { scanned ->
            ScannedResultCard(
                result = scanned,
                onDismiss = { result = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun ScannedResultCard(
    result: QrScanResult,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = result.contentType,
                    modifier = Modifier
                        .background(
                            color = Accent.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    color = Accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                result.version?.let { version ->
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Version $version",
                        color = MutedText,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
            SelectionContainer {
                Text(
                    text = result.text,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun QrErrorCorrection.label(): String = when (this) {
    QrErrorCorrection.LOW -> "Low"
    QrErrorCorrection.MEDIUM -> "Medium"
    QrErrorCorrection.QUARTILE -> "Quartile"
    QrErrorCorrection.HIGH -> "High"
}

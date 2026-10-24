package com.mahbub.qrcodegeneratorandscanner.presentation.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mahbub.qrcodegeneratorandscanner.domain.model.ScannedQr
import com.mahbub.qrcodegeneratorandscanner.presentation.components.MessageCard
import com.mahbub.qrcodegeneratorandscanner.presentation.theme.Accent
import com.mahbub.qrcodegeneratorandscanner.presentation.theme.ErrorText
import com.mahbub.qrcodegeneratorandscanner.presentation.theme.MutedText
import com.mahbub.qrcodegeneratorandscanner.presentation.theme.SurfaceColor
import com.mahbub.qrkit.QrScanResult
import com.mahbub.qrkit.QrScannerView
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ScanRoute(
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ScanScreen(
        state = state,
        onResult = { viewModel.onCodeScanned(it.toScannedQr()) },
        onError = viewModel::onCameraError,
        onDismiss = viewModel::dismissResult,
        modifier = modifier,
    )
}

@Composable
fun ScanScreen(
    state: ScanUiState,
    onResult: (QrScanResult) -> Unit,
    onError: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        QrScannerView(
            modifier = Modifier.fillMaxSize(),
            isActive = state.result == null,
            onResult = onResult,
            onError = onError,
        )

        state.errorMessage?.let { message ->
            MessageCard(
                message = message,
                color = ErrorText,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }

        state.result?.let { result ->
            ScannedResultCard(
                result = result,
                onDismiss = onDismiss,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun ScannedResultCard(
    result: ScannedQr,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.fillMaxWidth(),
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

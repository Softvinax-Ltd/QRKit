package ltd.softvinax.qrkit.app.presentation.generate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ltd.softvinax.qrkit.app.domain.model.GeneratedQr
import ltd.softvinax.qrkit.app.domain.model.QrErrorLevel
import ltd.softvinax.qrkit.app.presentation.components.MessageCard
import ltd.softvinax.qrkit.app.presentation.components.QrMatrixImage
import ltd.softvinax.qrkit.app.presentation.theme.Accent
import ltd.softvinax.qrkit.app.presentation.theme.CodeInk
import ltd.softvinax.qrkit.app.presentation.theme.ErrorText
import ltd.softvinax.qrkit.app.presentation.theme.MutedText
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun GenerateRoute(
    modifier: Modifier = Modifier,
    viewModel: GenerateViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GenerateScreen(
        state = state,
        onContentChange = viewModel::onContentChanged,
        onErrorLevelChange = viewModel::onErrorLevelChanged,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GenerateScreen(
    state: GenerateUiState,
    onContentChange: (String) -> Unit,
    onErrorLevelChange: (QrErrorLevel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = state.content,
            onValueChange = onContentChange,
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
                QrErrorLevel.entries.forEach { level ->
                    FilterChip(
                        selected = state.errorLevel == level,
                        onClick = { onErrorLevelChange(level) },
                        label = { Text(level.label()) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Accent.copy(alpha = 0.18f),
                            selectedLabelColor = Accent,
                        ),
                    )
                }
            }
        }

        val generated = state.generated
        val errorMessage = state.errorMessage
        when {
            errorMessage != null -> MessageCard(message = errorMessage, color = ErrorText)
            generated != null -> GeneratedQrCard(generated)
            state.content.isBlank() -> MessageCard(
                message = "Enter text, a link or any payload to generate a QR code.",
                color = MutedText,
            )
        }
    }
}

@Composable
private fun GeneratedQrCard(generated: GeneratedQr) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            QrMatrixImage(
                matrix = generated.matrix,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                darkColor = CodeInk,
                lightColor = Color.White,
                quietZone = 3,
                contentDescription = "QR code for ${generated.data}",
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Version ${generated.version} · ${generated.matrix.size}×" +
                    "${generated.matrix.size} modules · ${generated.errorLevel.label()}",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF667085),
            )
        }
    }
}

private fun QrErrorLevel.label(): String = when (this) {
    QrErrorLevel.LOW -> "Low"
    QrErrorLevel.MEDIUM -> "Medium"
    QrErrorLevel.QUARTILE -> "Quartile"
    QrErrorLevel.HIGH -> "High"
}

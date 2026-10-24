package com.mahbub.qrcodegeneratorandscanner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mahbub.qrcodegeneratorandscanner.presentation.generate.GenerateRoute
import com.mahbub.qrcodegeneratorandscanner.presentation.scan.ScanRoute
import com.mahbub.qrcodegeneratorandscanner.presentation.theme.Accent
import com.mahbub.qrcodegeneratorandscanner.presentation.theme.Background
import com.mahbub.qrcodegeneratorandscanner.presentation.theme.MutedText
import com.mahbub.qrcodegeneratorandscanner.presentation.theme.QrKitTheme

@Composable
fun App() {
    QrKitTheme {
        var selectedTab by rememberSaveable { mutableStateOf(TAB_GENERATE) }

        Surface(modifier = Modifier.fillMaxSize(), color = Background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeContentPadding(),
            ) {
                Header()
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Background,
                    contentColor = Accent,
                ) {
                    Tab(
                        selected = selectedTab == TAB_GENERATE,
                        onClick = { selectedTab = TAB_GENERATE },
                        text = { Text("Generate") },
                    )
                    Tab(
                        selected = selectedTab == TAB_SCAN,
                        onClick = { selectedTab = TAB_SCAN },
                        text = { Text("Scan") },
                    )
                }
                when (selectedTab) {
                    TAB_GENERATE -> GenerateRoute(Modifier.weight(1f))
                    else -> ScanRoute(Modifier.weight(1f))
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

private const val TAB_GENERATE = 0
private const val TAB_SCAN = 1

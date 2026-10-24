package com.mahbub.qrkit

import android.Manifest
import android.content.pm.PackageManager
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect

@Composable
actual fun QrScannerView(
    modifier: Modifier,
    isActive: Boolean,
    showOverlay: Boolean,
    onResult: (QrScanResult) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var requestedOnce by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { result ->
        granted = result
        if (!result) requestedOnce = true
    }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier) {
        if (granted) {
            val controller = remember(context) { Camera2ScannerController(context) }
            val currentOnResult by rememberUpdatedState(onResult)
            val currentOnError by rememberUpdatedState(onError)

            SideEffect {
                controller.onResult = { currentOnResult(it) }
                controller.onError = { currentOnError(it) }
                controller.isActive = isActive
            }
            LifecycleStartEffect(controller) {
                controller.start()
                onStopOrDispose { controller.stop() }
            }
            DisposableEffect(controller) {
                onDispose { controller.release() }
            }

            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).also { view ->
                        controller.attach(view)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            )
            if (showOverlay) {
                QrScannerOverlay(Modifier.fillMaxSize())
            }
        } else {
            CameraPermissionPrompt(
                modifier = Modifier.fillMaxSize(),
                showSettingsHint = requestedOnce,
                onRequest = { launcher.launch(Manifest.permission.CAMERA) },
            )
        }
    }
}

@Composable
private fun CameraPermissionPrompt(
    modifier: Modifier,
    showSettingsHint: Boolean,
    onRequest: () -> Unit,
) {
    Box(
        modifier = modifier.background(Color(0xFF101114)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            BasicText(
                text = "Camera access needed",
                style = TextStyle(color = Color.White, fontSize = 18.sp),
            )
            BasicText(
                text = if (showSettingsHint) {
                    "Enable the camera permission in system settings to scan QR codes."
                } else {
                    "Allow camera access to scan QR codes with your device."
                },
                style = TextStyle(
                    color = Color(0xFFB9BDC7),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                ),
            )
            BasicText(
                text = "Grant permission",
                style = TextStyle(color = Color(0xFF4ADE80), fontSize = 16.sp),
                modifier = Modifier
                    .clickable(onClick = onRequest)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@file:OptIn(ExperimentalForeignApi::class)

package com.mahbub.qrkit

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.UIKitView
import com.mahbub.qrkit.internal.decoder.inferContentType
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureInput
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeLeft
import platform.AVFoundation.AVCaptureVideoOrientationLandscapeRight
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVCaptureVideoOrientationPortraitUpsideDown
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.defaultDeviceWithDeviceType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectZero
import platform.CoreMedia.CMSampleBufferRef
import platform.Foundation.NSDate
import platform.Foundation.NSURL
import platform.Foundation.timeIntervalSince1970
import platform.ImageIO.kCGImagePropertyOrientationDown
import platform.ImageIO.kCGImagePropertyOrientationLeft
import platform.ImageIO.kCGImagePropertyOrientationRight
import platform.ImageIO.kCGImagePropertyOrientationUp
import platform.QuartzCore.CATransaction
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIColor
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceOrientation
import platform.UIKit.UIView
import platform.Vision.VNBarcodeObservation
import platform.Vision.VNBarcodeSymbologyQR
import platform.Vision.VNDetectBarcodesRequest
import platform.Vision.VNImageRequestHandler
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create
import kotlin.coroutines.resume

private const val ANALYSIS_INTERVAL_SECONDS = 0.12
private const val DUPLICATE_TIMEOUT_SECONDS = 2.5

@Composable
actual fun QrScannerView(
    modifier: Modifier,
    isActive: Boolean,
    showOverlay: Boolean,
    onResult: (QrScanResult) -> Unit,
    onError: (String) -> Unit,
) {
    val controller = remember { IosQrScannerController() }
    var authorized by remember { mutableStateOf(controller.hasCameraPermission()) }
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnError by rememberUpdatedState(onError)

    SideEffect {
        controller.onResult = { currentOnResult(it) }
        controller.onError = { currentOnError(it) }
        controller.isActive = isActive
    }

    LaunchedEffect(Unit) {
        if (!authorized) {
            authorized = controller.requestCameraPermission()
            if (!authorized) {
                currentOnError("Camera access is required to scan QR codes.")
            }
        }
    }

    DisposableEffect(controller, authorized) {
        if (authorized) controller.start()
        onDispose {
            if (authorized) controller.stop()
        }
    }
    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    Box(modifier) {
        if (authorized) {
            UIKitView(
                factory = { controller.createPreviewView() },
                modifier = Modifier.fillMaxSize(),
            )
            if (showOverlay) {
                QrScannerOverlay(Modifier.fillMaxSize())
            }
        } else {
            IosCameraPermissionPrompt(
                modifier = Modifier.fillMaxSize(),
                onOpenSettings = ::openAppSettings,
            )
        }
    }
}

@Composable
private fun IosCameraPermissionPrompt(
    modifier: Modifier,
    onOpenSettings: () -> Unit,
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
                text = "Allow camera access in Settings to scan QR codes with your device.",
                style = TextStyle(
                    color = Color(0xFFB9BDC7),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                ),
            )
            BasicText(
                text = "Open Settings",
                style = TextStyle(color = Color(0xFF4ADE80), fontSize = 16.sp),
                modifier = Modifier
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

private fun openAppSettings() {
    val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
    UIApplication.sharedApplication.openURL(
        url = url,
        options = emptyMap<Any?, Any>(),
        completionHandler = null,
    )
}

private class CameraPreviewView(session: AVCaptureSession) :
    UIView(frame = CGRectZero.readValue()) {

    private val previewLayer = AVCaptureVideoPreviewLayer(session = session)

    init {
        backgroundColor = UIColor.blackColor
        previewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        previewLayer.frame = bounds
        val connection = previewLayer.connection
        if (connection != null && connection.isVideoOrientationSupported()) {
            connection.videoOrientation = currentVideoOrientation()
        }
        CATransaction.commit()
    }
}

private class FrameDelegate(
    private val controller: IosQrScannerController,
) : NSObject(), AVCaptureVideoDataOutputSampleBufferDelegateProtocol {
    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputSampleBuffer: CMSampleBufferRef?,
        fromConnection: AVCaptureConnection,
    ) {
        controller.analyze(didOutputSampleBuffer)
    }
}

internal class IosQrScannerController {
    private val session = AVCaptureSession()
    private val sessionQueue = dispatch_queue_create("com.mahbub.qrkit.camera", null)
    private val analysisQueue = dispatch_queue_create("com.mahbub.qrkit.analysis", null)
    private var frameDelegate: FrameDelegate? = null
    private var configured = false
    private var started = false
    private var released = false
    private var lastAnalysisAt = 0.0
    private var lastText: String? = null
    private var lastTextAt = 0.0

    var onResult: ((QrScanResult) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var isActive: Boolean = true

    fun hasCameraPermission(): Boolean =
        AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) ==
            AVAuthorizationStatusAuthorized

    suspend fun requestCameraPermission(): Boolean = suspendCancellableCoroutine { continuation ->
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> continuation.resume(true)
            AVAuthorizationStatusNotDetermined ->
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    if (continuation.isActive) continuation.resume(granted)
                }
            else -> continuation.resume(false)
        }
    }

    fun createPreviewView(): UIView = CameraPreviewView(session)

    fun start() {
        if (started) return
        started = true
        dispatch_async(sessionQueue) {
            if (!released && configureLocked()) {
                session.startRunning()
            }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        dispatch_async(sessionQueue) {
            if (session.running) session.stopRunning()
        }
    }

    fun release() {
        if (released) return
        released = true
        started = false
        dispatch_async(sessionQueue) {
            if (session.running) session.stopRunning()
            session.beginConfiguration()
            for (output in session.outputs) {
                (output as? AVCaptureOutput)?.let { session.removeOutput(it) }
            }
            for (input in session.inputs) {
                (input as? AVCaptureInput)?.let { session.removeInput(it) }
            }
            session.commitConfiguration()
            frameDelegate = null
            configured = false
        }
    }

    private fun configureLocked(): Boolean {
        if (configured) return true

        val device = AVCaptureDevice.defaultDeviceWithDeviceType(
            AVCaptureDeviceTypeBuiltInWideAngleCamera,
            AVMediaTypeVideo,
            AVCaptureDevicePositionBack,
        ) ?: AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        if (device == null) {
            reportError("No camera is available on this device.")
            return false
        }

        val input = try {
            AVCaptureDeviceInput(device, null)
        } catch (_: Throwable) {
            null
        }
        if (input == null) {
            reportError("Unable to access the camera.")
            return false
        }

        session.beginConfiguration()
        session.sessionPreset = AVCaptureSessionPresetHigh

        if (!session.canAddInput(input)) {
            session.commitConfiguration()
            reportError("The camera input could not be configured.")
            return false
        }
        session.addInput(input)

        val output = AVCaptureVideoDataOutput()
        output.alwaysDiscardsLateVideoFrames = true
        val delegate = FrameDelegate(this)
        output.setSampleBufferDelegate(delegate, analysisQueue)

        if (!session.canAddOutput(output)) {
            session.commitConfiguration()
            reportError("The camera output could not be configured.")
            return false
        }
        session.addOutput(output)
        frameDelegate = delegate
        session.commitConfiguration()

        configured = true
        return true
    }

    fun analyze(sampleBuffer: CMSampleBufferRef?) {
        if (!isActive || sampleBuffer == null) return

        val now = NSDate().timeIntervalSince1970
        if (now - lastAnalysisAt < ANALYSIS_INTERVAL_SECONDS) return
        lastAnalysisAt = now

        val request = VNDetectBarcodesRequest()
        request.symbologies = listOfNotNull(VNBarcodeSymbologyQR)
        val handler = VNImageRequestHandler(
            sampleBuffer,
            currentImageOrientation(),
            emptyMap<Any?, Any>(),
        )
        if (!handler.performRequests(listOf(request), null)) return

        for (result in request.results.orEmpty()) {
            val barcode = result as? VNBarcodeObservation ?: continue
            val text = barcode.payloadStringValue ?: continue
            deliver(text)
            break
        }
    }

    private fun deliver(text: String) {
        val now = NSDate().timeIntervalSince1970
        if (text == lastText && now - lastTextAt < DUPLICATE_TIMEOUT_SECONDS) return
        lastText = text
        lastTextAt = now

        val result = QrScanResult(
            text = text,
            contentType = inferContentType(text),
        )
        dispatch_async(dispatch_get_main_queue()) {
            onResult?.invoke(result)
        }
    }

    private fun reportError(message: String) {
        dispatch_async(dispatch_get_main_queue()) {
            onError?.invoke(message)
        }
    }
}

private fun currentVideoOrientation(): Long = when (UIDevice.currentDevice.orientation) {
    UIDeviceOrientation.UIDeviceOrientationPortrait -> AVCaptureVideoOrientationPortrait
    UIDeviceOrientation.UIDeviceOrientationPortraitUpsideDown ->
        AVCaptureVideoOrientationPortraitUpsideDown
    UIDeviceOrientation.UIDeviceOrientationLandscapeLeft -> AVCaptureVideoOrientationLandscapeRight
    UIDeviceOrientation.UIDeviceOrientationLandscapeRight -> AVCaptureVideoOrientationLandscapeLeft
    else -> AVCaptureVideoOrientationPortrait
}

private fun currentImageOrientation(): UInt = when (UIDevice.currentDevice.orientation) {
    UIDeviceOrientation.UIDeviceOrientationPortrait -> kCGImagePropertyOrientationRight
    UIDeviceOrientation.UIDeviceOrientationPortraitUpsideDown -> kCGImagePropertyOrientationLeft
    UIDeviceOrientation.UIDeviceOrientationLandscapeLeft -> kCGImagePropertyOrientationUp
    UIDeviceOrientation.UIDeviceOrientationLandscapeRight -> kCGImagePropertyOrientationDown
    else -> kCGImagePropertyOrientationRight
}

package ltd.softvinax.qrkit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Camera 2 preview and frame source. All camera work runs on a dedicated [HandlerThread]; results
 * and errors are delivered on the main thread.
 */
internal class Camera2ScannerController(
    private val context: Context,
) : TextureView.SurfaceTextureListener {

    var onResult: (QrScanResult) -> Unit = {}
    var onError: (String) -> Unit = {}
    var isActive: Boolean = true

    private val handlerThread = HandlerThread("qrkit-camera").apply { start() }
    private val handler = Handler(handlerThread.looper)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var textureView: TextureView? = null
    private var started = false
    private var opening = false
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var previewSurface: Surface? = null
    private var luminance = ByteArray(0)

    private var lastAnalysisTime = 0L
    private var lastResultText: String? = null
    private var lastResultTime = 0L

    fun attach(view: TextureView) {
        textureView = view
        view.surfaceTextureListener = this
        if (view.isAvailable) {
            handler.post { if (started) openCameraLocked() }
        }
    }

    fun start() {
        started = true
        handler.post { openCameraLocked() }
    }

    fun stop() {
        started = false
        handler.post { closeCameraLocked() }
    }

    fun release() {
        stop()
        handler.post { handlerThread.quitSafely() }
    }

    // region TextureView callbacks (main thread)

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        handler.post { if (started) openCameraLocked() }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        handler.post { closeCameraLocked() }
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit

    // endregion

    private fun openCameraLocked() {
        if (!started || opening || cameraDevice != null || textureView?.isAvailable != true) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            reportError("Camera permission is not granted")
            return
        }

        val cameraId = chooseCameraId() ?: run {
            reportError("No camera is available on this device")
            return
        }

        opening = true
        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    opening = false
                    if (!started) {
                        camera.close()
                        return
                    }
                    cameraDevice = camera
                    createCaptureSessionLocked(camera)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    opening = false
                    camera.close()
                    if (cameraDevice === camera) cameraDevice = null
                    closeSessionLocked()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    opening = false
                    camera.close()
                    if (cameraDevice === camera) cameraDevice = null
                    closeSessionLocked()
                    reportError("Camera error (code $error)")
                }
            }, handler)
        } catch (exception: SecurityException) {
            opening = false
            reportError("Camera permission is not granted")
        } catch (exception: Exception) {
            opening = false
            reportError("Unable to open the camera: ${exception.message}")
        }
    }

    private fun chooseCameraId(): String? {
        val ids = cameraManager.cameraIdList
        return ids.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: ids.firstOrNull()
    }

    private fun createCaptureSessionLocked(camera: CameraDevice) {
        val view = textureView
        if (view == null || !view.isAvailable) {
            closeCameraLocked()
            return
        }
        val characteristics = try {
            cameraManager.getCameraCharacteristics(camera.id)
        } catch (exception: Exception) {
            reportError("Unable to read camera characteristics")
            return
        }
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?: run {
                reportError("Camera does not expose a stream configuration map")
                return
            }

        val previewSize = choosePreviewSize(map, view.width, view.height)
        val analysisSize = chooseAnalysisSize(map)

        val texture = view.surfaceTexture ?: return
        texture.setDefaultBufferSize(previewSize.width, previewSize.height)
        val surface = Surface(texture)
        previewSurface = surface

        val reader = ImageReader.newInstance(
            analysisSize.width,
            analysisSize.height,
            ImageFormat.YUV_420_888,
            2,
        )
        imageReader = reader
        reader.setOnImageAvailableListener({ source ->
            val image = try {
                source.acquireLatestImage()
            } catch (_: IllegalStateException) {
                null
            } ?: return@setOnImageAvailableListener
            try {
                processFrameLocked(image)
            } finally {
                image.close()
            }
        }, handler)

        val displayRotation = view.display?.rotation ?: Surface.ROTATION_0
        mainHandler.post {
            configureTransform(view, previewSize, displayRotation)
        }

        try {
            camera.createCaptureSession(
                listOf(surface, reader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (!started) {
                            session.close()
                            return
                        }
                        captureSession = session
                        try {
                            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                            request.addTarget(surface)
                            request.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                            )
                            session.setRepeatingRequest(request.build(), null, handler)
                        } catch (exception: Exception) {
                            reportError("Unable to start the camera preview: ${exception.message}")
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        reportError("Unable to configure the camera preview")
                    }
                },
                handler,
            )
        } catch (exception: Exception) {
            reportError("Unable to start the camera: ${exception.message}")
        }
    }

    private fun processFrameLocked(image: Image) {
        if (!started || !isActive) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastAnalysisTime < ANALYSIS_INTERVAL_MS) return
        lastAnalysisTime = now

        val data = extractLuminance(image) ?: return
        val result = try {
            QrCodeDecoder.decodeLuminance(data, image.width, image.height)
        } catch (_: Exception) {
            null
        } ?: return

        if (result.text == lastResultText && now - lastResultTime < DUPLICATE_TIMEOUT_MS) return
        lastResultText = result.text
        lastResultTime = now
        mainHandler.post { onResult(result) }
    }

    private fun extractLuminance(image: Image): ByteArray? {
        val width = image.width
        val height = image.height
        if (width <= 0 || height <= 0) return null
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        if (!buffer.hasRemaining()) return null

        val required = width * height
        if (luminance.size < required) luminance = ByteArray(required)
        val output = luminance
        buffer.rewind()

        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        if (pixelStride == 1 && rowStride == width) {
            buffer.get(output, 0, min(required, buffer.remaining()))
            return output
        }

        val row = ByteArray(max(rowStride, width))
        var out = 0
        for (y in 0 until height) {
            val rowStart = y * rowStride
            if (rowStart >= buffer.limit()) break
            buffer.position(rowStart)
            val count = min(buffer.remaining(), row.size)
            buffer.get(row, 0, count)
            if (pixelStride == 1) {
                System.arraycopy(row, 0, output, out, min(width, count))
            } else {
                var x = 0
                while (x < width && x * pixelStride < count) {
                    output[out + x] = row[x * pixelStride]
                    x++
                }
            }
            out += width
        }
        return output
    }

    private fun closeCameraLocked() {
        closeSessionLocked()
        cameraDevice?.close()
        cameraDevice = null
        previewSurface?.release()
        previewSurface = null
    }

    private fun closeSessionLocked() {
        captureSession?.close()
        captureSession = null
        imageReader?.close()
        imageReader = null
    }

    private fun configureTransform(view: TextureView, previewSize: Size, rotation: Int) {
        val viewWidth = view.width
        val viewHeight = view.height
        if (viewWidth == 0 || viewHeight == 0) return

        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(
            0f,
            0f,
            previewSize.height.toFloat(),
            previewSize.width.toFloat(),
        )
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = max(
                viewHeight.toFloat() / previewSize.height,
                viewWidth.toFloat() / previewSize.width,
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate(90f * (rotation - 2), centerX, centerY)
        } else if (rotation == Surface.ROTATION_180) {
            matrix.postRotate(180f, centerX, centerY)
        }
        view.setTransform(matrix)
    }

    private fun choosePreviewSize(map: StreamConfigurationMap, viewWidth: Int, viewHeight: Int): Size {
        val sizes = map.getOutputSizes(SurfaceTexture::class.java).orEmpty()
        if (sizes.isEmpty()) return Size(1280, 720)

        val targetArea = 1280 * 720
        val viewAspect = if (viewHeight > 0) viewWidth.toFloat() / viewHeight else 9f / 16f
        return sizes.minByOrNull { size ->
            val aspect = size.width.toFloat() / size.height
            val aspectPenalty = abs(aspect - viewAspect) / max(aspect, viewAspect) * targetArea
            abs(size.width * size.height - targetArea) + aspectPenalty
        } ?: sizes[0]
    }

    private fun chooseAnalysisSize(map: StreamConfigurationMap): Size {
        val sizes = map.getOutputSizes(ImageFormat.YUV_420_888).orEmpty()
        if (sizes.isEmpty()) return Size(640, 480)
        val bounded = sizes.filter { max(it.width, it.height) <= 1280 }
        val candidates = if (bounded.isEmpty()) sizes.toList() else bounded
        return candidates.maxByOrNull { it.width * it.height } ?: sizes[0]
    }

    private fun reportError(message: String) {
        mainHandler.post { onError(message) }
    }

    private companion object {
        const val ANALYSIS_INTERVAL_MS = 140L
        const val DUPLICATE_TIMEOUT_MS = 2500L
    }
}

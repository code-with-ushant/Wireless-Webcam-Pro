package com.wirelesswebcam.camera

import android.content.Context
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionFilter
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.wirelesswebcam.encoder.H264HardwareEncoder
import java.util.concurrent.Executors

/**
 * CameraX Pipeline Manager.
 * 
 * Configures camera hardware capture at target resolutions (1080p/720p) and frame rates (30/60 FPS),
 * splitting camera frames between the Jetpack Compose screen preview and the zero-copy MediaCodec input Surface.
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    companion object {
        private const val TAG = "CameraManager"
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var encoder: H264HardwareEncoder? = null
    private val executor = Executors.newSingleThreadExecutor()

    var isStreaming: Boolean = false
        private set

    /**
     * Initializes CameraX and binds capture session.
     */
    fun startCamera(
        previewView: PreviewView,
        width: Int = 1920,
        height: Int = 1080,
        fps: Int = 30,
        bitrateBps: Int = 5_000_000,
        isFrontCamera: Boolean = false,
        onEncodedFrame: (ByteArray) -> Unit
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(
                    previewView = previewView,
                    width = width,
                    height = height,
                    fps = fps,
                    bitrateBps = bitrateBps,
                    isFrontCamera = isFrontCamera,
                    onEncodedFrame = onEncodedFrame
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize ProcessCameraProvider", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(
        previewView: PreviewView,
        width: Int,
        height: Int,
        fps: Int,
        bitrateBps: Int,
        isFrontCamera: Boolean,
        onEncodedFrame: (ByteArray) -> Unit
    ) {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        // Select camera lens
        val cameraSelector = if (isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        // Configure Resolution Selector for exact target resolution (1080p or 720p)
        val targetSize = Size(width, height)
        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(targetSize, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
            )
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .build()

        // 1. Local Screen Preview Use Case
        val preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        // 2. Instantiate zero-copy H.264 MediaCodec Hardware Encoder
        encoder?.stop()
        encoder = H264HardwareEncoder(
            width = width,
            height = height,
            fps = fps,
            initialBitrateBps = bitrateBps,
            onFrameEncoded = onEncodedFrame
        )
        val encoderSurface: Surface = encoder!!.start()

        // 3. Encoder Capture Use Case: Render camera surface directly into MediaCodec input surface
        val encoderUseCase = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()
            .also {
                it.setSurfaceProvider(executor) { request ->
                    request.provideSurface(encoderSurface, executor) { result ->
                        Log.d(TAG, "Encoder surface request result code: ${result.resultCode}")
                    }
                }
            }

        try {
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                encoderUseCase
            )
            isStreaming = true
            Log.i(TAG, "CameraX successfully bound to MediaCodec encoder ($width x $height @ $fps FPS)")
        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed", e)
            isStreaming = false
        }
    }

    /**
     * Updates target bitrate on the fly without breaking camera capture session.
     */
    fun updateBitrate(newBitrateBps: Int) {
        encoder?.setBitrate(newBitrateBps)
    }

    /**
     * Triggers immediate keyframe refresh.
     */
    fun requestKeyframe() {
        encoder?.requestSyncFrame()
    }

    /**
     * Stops camera capture and releases MediaCodec encoder.
     */
    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            encoder?.stop()
            encoder = null
            isStreaming = false
            Log.i(TAG, "Camera capture stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera pipeline", e)
        }
    }
}

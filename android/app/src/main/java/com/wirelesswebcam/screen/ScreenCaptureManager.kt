package com.wirelesswebcam.screen

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Controller for managing the ScreenCaptureService lifecycle.
 */
class ScreenCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "ScreenCaptureManager"
    }

    val isCapturing: Boolean
        get() = ScreenCaptureService.isRunning

    /**
     * Starts screen capture by sending an ACTION_START Intent to ScreenCaptureService.
     */
    fun startCapture(
        resultCode: Int,
        data: Intent,
        width: Int = 1920,
        height: Int = 1080,
        fps: Int = 30,
        bitrateBps: Int = 5_000_000,
        onEncodedFrame: (ByteArray) -> Unit,
        onStateChanged: ((Boolean) -> Unit)? = null
    ) {
        Log.i(TAG, "Dispatching start capture intent to ScreenCaptureService")

        ScreenCaptureService.framePacketListener = onEncodedFrame
        ScreenCaptureService.captureStateListener = onStateChanged

        val intent = Intent(context, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_START
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
            putExtra(ScreenCaptureService.EXTRA_WIDTH, width)
            putExtra(ScreenCaptureService.EXTRA_HEIGHT, height)
            putExtra(ScreenCaptureService.EXTRA_FPS, fps)
            putExtra(ScreenCaptureService.EXTRA_BITRATE, bitrateBps)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ScreenCaptureService", e)
            onStateChanged?.invoke(false)
        }
    }

    /**
     * Dynamically updates encoder bitrate.
     */
    fun updateBitrate(newBitrateBps: Int) {
        val intent = Intent(context, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_UPDATE_BITRATE
            putExtra(ScreenCaptureService.EXTRA_BITRATE, newBitrateBps)
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send update bitrate intent: ${e.message}")
        }
    }

    /**
     * Requests an IDR sync keyframe.
     */
    fun requestKeyframe() {
        val intent = Intent(context, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_REQUEST_KEYFRAME
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send request keyframe intent: ${e.message}")
        }
    }

    /**
     * Stops screen capture service.
     */
    fun stopCapture() {
        Log.i(TAG, "Stopping ScreenCaptureService")
        ScreenCaptureService.framePacketListener = null
        ScreenCaptureService.captureStateListener = null

        val intent = Intent(context, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send stop capture intent: ${e.message}")
        }
    }
}

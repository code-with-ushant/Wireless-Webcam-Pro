package com.wirelesswebcam.screen

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import com.wirelesswebcam.encoder.H264HardwareEncoder

/**
 * Foreground Service for Android MediaProjection Screen Capture.
 *
 * Fully compliant with Android 10, 11, 12, 13, and 14+ (API 29..34+) requirements.
 * Promotes to foreground with FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION immediately
 * before acquiring the MediaProjection instance to prevent SecurityExceptions.
 */
class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "wireless_webcam_screen_capture"
        private const val NOTIFICATION_ID = 1001
        private const val VIRTUAL_DISPLAY_NAME = "WirelessWebcamScreenMirror"

        const val ACTION_START = "com.wirelesswebcam.screen.ACTION_START"
        const val ACTION_STOP = "com.wirelesswebcam.screen.ACTION_STOP"
        const val ACTION_UPDATE_BITRATE = "com.wirelesswebcam.screen.ACTION_UPDATE_BITRATE"
        const val ACTION_REQUEST_KEYFRAME = "com.wirelesswebcam.screen.ACTION_REQUEST_KEYFRAME"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_WIDTH = "extra_width"
        const val EXTRA_HEIGHT = "extra_height"
        const val EXTRA_FPS = "extra_fps"
        const val EXTRA_BITRATE = "extra_bitrate"

        // Callback listener for encoded H.264 packets
        var framePacketListener: ((ByteArray) -> Unit)? = null
        var captureStateListener: ((Boolean) -> Unit)? = null

        var isRunning: Boolean = false
            private set
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var encoder: H264HardwareEncoder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                handleStartCapture(intent)
            }
            ACTION_STOP -> {
                handleStopCapture()
                stopSelf()
            }
            ACTION_UPDATE_BITRATE -> {
                val newBitrate = intent.getIntExtra(EXTRA_BITRATE, 5_000_000)
                encoder?.setBitrate(newBitrate)
            }
            ACTION_REQUEST_KEYFRAME -> {
                encoder?.requestSyncFrame()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleStartCapture(intent: Intent) {
        // 1. MUST promote to foreground with FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION first!
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            captureStateListener?.invoke(false)
            stopSelf()
            return
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode != Activity.RESULT_OK || resultData == null) {
            Log.e(TAG, "Invalid result code or intent data for screen capture")
            captureStateListener?.invoke(false)
            stopSelf()
            return
        }

        val width = intent.getIntExtra(EXTRA_WIDTH, 1920)
        val height = intent.getIntExtra(EXTRA_HEIGHT, 1080)
        val fps = intent.getIntExtra(EXTRA_FPS, 30)
        val bitrateBps = intent.getIntExtra(EXTRA_BITRATE, 5_000_000)

        Log.i(TAG, "Initializing screen capture ($width x $height @ $fps FPS, $bitrateBps bps)")

        try {
            // 2. Initialize H.264 hardware encoder with Surface input
            encoder?.stop()
            encoder = H264HardwareEncoder(
                width = width,
                height = height,
                fps = fps,
                initialBitrateBps = bitrateBps,
                onFrameEncoded = { packet ->
                    framePacketListener?.invoke(packet)
                }
            )
            val encoderSurface: Surface = encoder!!.start()

            // 3. Create MediaProjection from token
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

            if (mediaProjection == null) {
                Log.e(TAG, "MediaProjection is null after getMediaProjection")
                captureStateListener?.invoke(false)
                stopSelf()
                return
            }

            // Register stop callback
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.i(TAG, "MediaProjection stopped by system")
                    handleStopCapture()
                    stopSelf()
                }
            }, null)

            // 4. Get screen density for VirtualDisplay
            val metrics = DisplayMetrics()
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            val densityDpi = metrics.densityDpi

            // 5. Create VirtualDisplay that mirrors screen directly onto encoder Surface
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                encoderSurface,
                null,
                null
            )

            isRunning = true
            captureStateListener?.invoke(true)
            Log.i(TAG, "Screen capture VirtualDisplay running successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error starting screen capture pipeline", e)
            handleStopCapture()
            captureStateListener?.invoke(false)
            stopSelf()
        }
    }

    private fun handleStopCapture() {
        isRunning = false
        captureStateListener?.invoke(false)
        Log.i(TAG, "Stopping ScreenCaptureService")

        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing VirtualDisplay: ${e.message}")
        } finally {
            virtualDisplay = null
        }

        try {
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping MediaProjection: ${e.message}")
        } finally {
            mediaProjection = null
        }

        try {
            encoder?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping encoder: ${e.message}")
        } finally {
            encoder = null
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        super.onDestroy()
        handleStopCapture()
        Log.i(TAG, "ScreenCaptureService destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Streaming",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Wireless Webcam is sharing your screen to PC"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("Wireless Webcam Pro")
            .setContentText("Streaming screen to PC...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }
}

package com.wirelesswebcam.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wirelesswebcam.camera.CameraManager
import com.wirelesswebcam.network.WebSocketClientStreamer
import com.wirelesswebcam.screen.ScreenCaptureManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Stream mode enum — determines whether camera or screen is being streamed.
 */
enum class StreamMode {
    CAMERA,
    SCREEN
}

data class UiState(
    val serverIp: String = "192.168.1.100",
    val serverPort: Int = 8000,
    val isConnected: Boolean = false,
    val isStreaming: Boolean = false,
    val streamMode: StreamMode = StreamMode.CAMERA,
    val resolution: String = "1080p", // "720p" or "1080p"
    val fps: Int = 30, // 30 or 60
    val bitrateMbps: Float = 5.0f, // 1.0 to 20.0 Mbps default
    val isFrontCamera: Boolean = false,
    val statusMessage: String = "Disconnected",
    val isScreenSharePending: Boolean = false // waiting for user to grant permission
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var cameraManager: CameraManager? = null
    private var screenCaptureManager: ScreenCaptureManager? = null
    private var networkStreamer: WebSocketClientStreamer? = null

    init {
        networkStreamer = WebSocketClientStreamer(
            onControlCommandReceived = { textJson ->
                handleIncomingControlCommand(textJson)
            },
            onConnectionStateChanged = { connected ->
                viewModelScope.launch {
                    _uiState.value = _uiState.value.copy(
                        isConnected = connected,
                        statusMessage = if (connected) {
                            val mode = if (_uiState.value.streamMode == StreamMode.SCREEN) "Screen Share" else "Camera"
                            "$mode Streaming Active"
                        } else "Disconnected"
                    )
                }
            }
        )

        screenCaptureManager = ScreenCaptureManager(application)
    }

    fun bindCameraManager(manager: CameraManager) {
        this.cameraManager = manager
    }

    fun updateServerIp(ip: String) {
        _uiState.value = _uiState.value.copy(serverIp = ip)
    }

    // ---------------------------------------------------------------
    // Camera Streaming
    // ---------------------------------------------------------------

    fun toggleConnection(previewView: androidx.camera.view.PreviewView) {
        val currentState = _uiState.value
        if (currentState.isConnected || currentState.isStreaming) {
            stopStreaming()
        } else {
            startStreaming(previewView)
        }
    }

    fun startStreaming(previewView: androidx.camera.view.PreviewView) {
        val state = _uiState.value
        val (width, height) = if (state.resolution == "720p") Pair(1280, 720) else Pair(1920, 1080)
        val bitrateBps = (state.bitrateMbps * 1_000_000).toInt()

        Log.i(TAG, "Starting Camera Stream ($width x $height @ ${state.fps} FPS, $bitrateBps bps)")

        // 1. Connect WebSocket streamer to PC
        networkStreamer?.connect(state.serverIp, state.serverPort)

        // 2. Start CameraX pipeline with MediaCodec callback
        cameraManager?.startCamera(
            previewView = previewView,
            width = width,
            height = height,
            fps = state.fps,
            bitrateBps = bitrateBps,
            isFrontCamera = state.isFrontCamera,
            onEncodedFrame = { packet ->
                networkStreamer?.sendFramePacket(packet)
            }
        )

        _uiState.value = _uiState.value.copy(
            isStreaming = true,
            streamMode = StreamMode.CAMERA,
            statusMessage = "Connecting to ${state.serverIp}..."
        )
    }

    fun stopStreaming() {
        Log.i(TAG, "Stopping stream")
        networkStreamer?.disconnect()
        cameraManager?.stopCamera()
        screenCaptureManager?.stopCapture()
        _uiState.value = _uiState.value.copy(
            isConnected = false,
            isStreaming = false,
            isScreenSharePending = false,
            statusMessage = "Disconnected"
        )
    }

    // ---------------------------------------------------------------
    // Screen Share
    // ---------------------------------------------------------------

    /**
     * Called by UI to request screen share. Sets pending flag so MainActivity
     * knows to launch the MediaProjection permission dialog.
     */
    fun requestScreenShare() {
        if (_uiState.value.isStreaming && _uiState.value.streamMode == StreamMode.SCREEN) {
            // Already screen sharing — stop it
            stopStreaming()
            return
        }
        // Stop any active camera stream first
        if (_uiState.value.isStreaming) {
            cameraManager?.stopCamera()
        }
        _uiState.value = _uiState.value.copy(isScreenSharePending = true)
    }

    /**
     * Called by MainActivity after user grants MediaProjection permission.
     */
    fun startScreenShare(resultCode: Int, data: Intent) {
        val state = _uiState.value
        val (width, height) = if (state.resolution == "720p") Pair(1280, 720) else Pair(1920, 1080)
        val bitrateBps = (state.bitrateMbps * 1_000_000).toInt()

        Log.i(TAG, "Starting Screen Share ($width x $height @ ${state.fps} FPS, $bitrateBps bps)")

        // 1. Connect WebSocket streamer to PC
        networkStreamer?.connect(state.serverIp, state.serverPort)

        // 2. Start screen capture with MediaProjection
        screenCaptureManager?.startCapture(
            resultCode = resultCode,
            data = data,
            width = width,
            height = height,
            fps = state.fps,
            bitrateBps = bitrateBps,
            onEncodedFrame = { packet ->
                networkStreamer?.sendFramePacket(packet)
            },
            onStateChanged = { capturing ->
                viewModelScope.launch {
                    if (!capturing && _uiState.value.streamMode == StreamMode.SCREEN) {
                        _uiState.value = _uiState.value.copy(
                            isStreaming = false,
                            statusMessage = "Screen Share Ended"
                        )
                    }
                }
            }
        )

        _uiState.value = _uiState.value.copy(
            isStreaming = true,
            isScreenSharePending = false,
            streamMode = StreamMode.SCREEN,
            statusMessage = "Screen Share → ${state.serverIp}"
        )
    }

    /**
     * Called when user denies MediaProjection permission.
     */
    fun onScreenShareDenied() {
        _uiState.value = _uiState.value.copy(isScreenSharePending = false)
    }

    // ---------------------------------------------------------------
    // Settings
    // ---------------------------------------------------------------

    fun updateBitrate(newBitrateMbps: Float) {
        _uiState.value = _uiState.value.copy(bitrateMbps = newBitrateMbps)
        val bitrateBps = (newBitrateMbps * 1_000_000).toInt()
        cameraManager?.updateBitrate(bitrateBps)
        screenCaptureManager?.updateBitrate(bitrateBps)
    }

    fun updateResolution(newResolution: String, previewView: androidx.camera.view.PreviewView) {
        if (_uiState.value.resolution == newResolution) return
        _uiState.value = _uiState.value.copy(resolution = newResolution)
        if (_uiState.value.isStreaming && _uiState.value.streamMode == StreamMode.CAMERA) {
            startStreaming(previewView)
        }
    }

    fun updateFps(newFps: Int, previewView: androidx.camera.view.PreviewView) {
        if (_uiState.value.fps == newFps) return
        _uiState.value = _uiState.value.copy(fps = newFps)
        if (_uiState.value.isStreaming && _uiState.value.streamMode == StreamMode.CAMERA) {
            startStreaming(previewView)
        }
    }

    fun toggleCameraLens(previewView: androidx.camera.view.PreviewView) {
        val newLens = !_uiState.value.isFrontCamera
        _uiState.value = _uiState.value.copy(isFrontCamera = newLens)
        if (_uiState.value.isStreaming && _uiState.value.streamMode == StreamMode.CAMERA) {
            startStreaming(previewView)
        }
    }

    private fun handleIncomingControlCommand(textJson: String) {
        try {
            val json = JSONObject(textJson)
            val cmd = json.optString("command")
            Log.i(TAG, "Processing PC Control Command: $cmd")

            viewModelScope.launch {
                when (cmd) {
                    "set_bitrate" -> {
                        val bps = json.optInt("bitrate_bps", 5_000_000)
                        val mbps = (bps / 1_000_000.0f)
                        _uiState.value = _uiState.value.copy(bitrateMbps = mbps)
                        cameraManager?.updateBitrate(bps)
                        screenCaptureManager?.updateBitrate(bps)
                    }
                    "request_keyframe" -> {
                        cameraManager?.requestKeyframe()
                        screenCaptureManager?.requestKeyframe()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse control command JSON", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopStreaming()
    }
}

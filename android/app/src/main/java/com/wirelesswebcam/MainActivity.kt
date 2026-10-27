package com.wirelesswebcam

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.wirelesswebcam.camera.CameraManager
import com.wirelesswebcam.ui.screens.CameraScreen
import com.wirelesswebcam.ui.theme.WirelessWebcamTheme
import com.wirelesswebcam.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var cameraManager: CameraManager

    // Camera permission launcher
    private val requestCameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Camera permission required to stream video", Toast.LENGTH_LONG).show()
            }
        }

    // MediaProjection screen capture permission launcher
    private val screenCapturePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                viewModel.startScreenShare(result.resultCode, result.data!!)
            } else {
                viewModel.onScreenShareDenied()
                Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraManager = CameraManager(this, this)
        viewModel.bindCameraManager(cameraManager)

        checkCameraPermission()

        setContent {
            WirelessWebcamTheme {
                val uiState by viewModel.uiState.collectAsState()

                // Watch for screen share permission request
                LaunchedEffect(uiState.isScreenSharePending) {
                    if (uiState.isScreenSharePending) {
                        launchScreenCapturePermission()
                    }
                }

                CameraScreen(
                    viewModel = viewModel,
                    uiState = uiState,
                    onPreviewViewCreated = { previewView ->
                        // CameraView initialized
                    }
                )
            }
        }
    }

    /**
     * Launches the system MediaProjection permission dialog.
     */
    private fun launchScreenCapturePermission() {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = projectionManager.createScreenCaptureIntent()
        screenCapturePermissionLauncher.launch(captureIntent)
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.stopStreaming()
    }
}

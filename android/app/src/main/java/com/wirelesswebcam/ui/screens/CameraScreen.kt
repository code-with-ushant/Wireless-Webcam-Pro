package com.wirelesswebcam.ui.screens

import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.wirelesswebcam.ui.theme.*
import com.wirelesswebcam.ui.viewmodel.MainViewModel
import com.wirelesswebcam.ui.viewmodel.StreamMode
import com.wirelesswebcam.ui.viewmodel.UiState

/**
 * Ultra-Clean Pro Camera HUD Interface.
 * 
 * Provides an unobstructed fullscreen camera preview with floating HUD elements,
 * right-side Pro Camera shutter dock, and collapsible settings panel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    viewModel: MainViewModel,
    uiState: UiState,
    onPreviewViewCreated: (PreviewView) -> Unit
) {
    var showIpDialog by remember { mutableStateOf(false) }
    var showSettingsPanel by remember { mutableStateOf(false) }
    var tempIpInput by remember { mutableStateOf(uiState.serverIp) }
    var localPreviewView by remember { mutableStateOf<PreviewView?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // 1. FULLSCREEN UNOBSTRUCTED CAMERA PREVIEW
        AndroidView(
            factory = { context ->
                PreviewView(context).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    onPreviewViewCreated(this)
                    localPreviewView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. TOP FLOATING HUD BAR (Minimal Overlay)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Live Status Badge Pill
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = DarkSurface.copy(alpha = 0.75f),
                tonalElevation = 4.dp,
                modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (uiState.isConnected) AccentGreen else AccentRed)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isStreaming && uiState.streamMode == StreamMode.SCREEN)
                            "SCREEN • ${uiState.resolution} @ ${uiState.fps}FPS"
                        else if (uiState.isStreaming)
                            "${uiState.resolution} @ ${uiState.fps}FPS • ${String.format("%.1f", uiState.bitrateMbps)}Mbps"
                        else uiState.statusMessage,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                }
            }

            // Top Action Quick Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // IP Server Config Button
                Surface(
                    shape = CircleShape,
                    color = DarkSurface.copy(alpha = 0.75f),
                    modifier = Modifier
                        .size(42.dp)
                        .clickable { showIpDialog = true }
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                    tonalElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Wifi,
                            contentDescription = "Server IP Settings",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Camera Lens Flip Button
                Surface(
                    shape = CircleShape,
                    color = DarkSurface.copy(alpha = 0.75f),
                    modifier = Modifier
                        .size(42.dp)
                        .clickable { localPreviewView?.let { viewModel.toggleCameraLens(it) } }
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                    tonalElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Cameraswitch,
                            contentDescription = "Switch Camera Lens",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Settings Panel Toggle Button
                Surface(
                    shape = CircleShape,
                    color = if (showSettingsPanel) PrimaryBlue else DarkSurface.copy(alpha = 0.75f),
                    modifier = Modifier
                        .size(42.dp)
                        .clickable { showSettingsPanel = !showSettingsPanel }
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                    tonalElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (showSettingsPanel) Icons.Filled.Close else Icons.Filled.Tune,
                            contentDescription = "Toggle Settings Panel",
                            tint = if (showSettingsPanel) DarkBackground else TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 3. RIGHT PRO DOCK BAR (Camera + Screen Share Buttons)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Camera Stream Shutter Button
            Surface(
                shape = CircleShape,
                color = DarkSurface.copy(alpha = 0.80f),
                modifier = Modifier
                    .size(72.dp)
                    .clickable { localPreviewView?.let { viewModel.toggleConnection(it) } }
                    .border(
                        2.dp,
                        if (uiState.isStreaming && uiState.streamMode == StreamMode.CAMERA) AccentRed
                        else if (uiState.streamMode == StreamMode.SCREEN && uiState.isStreaming) Color.White.copy(alpha = 0.3f)
                        else PrimaryBlue,
                        CircleShape
                    ),
                tonalElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(
                                if (uiState.isStreaming && uiState.streamMode == StreamMode.CAMERA) 28.dp
                                else 48.dp
                            )
                            .clip(
                                if (uiState.isStreaming && uiState.streamMode == StreamMode.CAMERA)
                                    RoundedCornerShape(6.dp) else CircleShape
                            )
                            .background(
                                if (uiState.isStreaming && uiState.streamMode == StreamMode.CAMERA) AccentRed
                                else PrimaryBlue
                            )
                    )
                }
            }

            // Camera label
            Text(
                text = "Camera",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )

            // Screen Share Button
            Surface(
                shape = CircleShape,
                color = if (uiState.isStreaming && uiState.streamMode == StreamMode.SCREEN)
                    AccentGreen.copy(alpha = 0.2f) else DarkSurface.copy(alpha = 0.80f),
                modifier = Modifier
                    .size(60.dp)
                    .clickable { viewModel.requestScreenShare() }
                    .border(
                        2.dp,
                        if (uiState.isStreaming && uiState.streamMode == StreamMode.SCREEN) AccentGreen
                        else Color.White.copy(alpha = 0.3f),
                        CircleShape
                    ),
                tonalElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (uiState.isStreaming && uiState.streamMode == StreamMode.SCREEN)
                            Icons.Filled.CastConnected else Icons.Filled.Cast,
                        contentDescription = "Screen Share",
                        tint = if (uiState.isStreaming && uiState.streamMode == StreamMode.SCREEN)
                            AccentGreen else TextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Screen Share label
            Text(
                text = if (uiState.isStreaming && uiState.streamMode == StreamMode.SCREEN) "Stop Share" else "Screen",
                style = MaterialTheme.typography.labelSmall,
                color = if (uiState.isStreaming && uiState.streamMode == StreamMode.SCREEN) AccentGreen else TextSecondary
            )
        }

        // 4. COLLAPSIBLE FLOATING SETTINGS PANEL (Does not block camera preview when hidden)
        AnimatedVisibility(
            visible = showSettingsPanel,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 380.dp)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(20.dp),
                color = DarkSurface.copy(alpha = 0.92f),
                tonalElevation = 10.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Stream Settings",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        IconButton(
                            onClick = { showSettingsPanel = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = TextSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Resolution Filter Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Resolution",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = uiState.resolution == "720p",
                                onClick = { localPreviewView?.let { viewModel.updateResolution("720p", it) } },
                                label = { Text("720p") }
                            )
                            FilterChip(
                                selected = uiState.resolution == "1080p",
                                onClick = { localPreviewView?.let { viewModel.updateResolution("1080p", it) } },
                                label = { Text("1080p") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // FPS Filter Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Frame Rate",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = uiState.fps == 30,
                                onClick = { localPreviewView?.let { viewModel.updateFps(30, it) } },
                                label = { Text("30 FPS") }
                            )
                            FilterChip(
                                selected = uiState.fps == 60,
                                onClick = { localPreviewView?.let { viewModel.updateFps(60, it) } },
                                label = { Text("60 FPS") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Bitrate Slider
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Bitrate",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = "${String.format("%.1f", uiState.bitrateMbps)} Mbps",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PrimaryBlue
                            )
                        }
                        Slider(
                            value = uiState.bitrateMbps,
                            onValueChange = { viewModel.updateBitrate(it) },
                            valueRange = 2.0f..20.0f,
                            steps = 18
                        )
                    }
                }
            }
        }

        // 5. SERVER IP DIALOG
        if (showIpDialog) {
            AlertDialog(
                onDismissRequest = { showIpDialog = false },
                title = { Text("Target Windows PC IP Address") },
                text = {
                    Column {
                        Text(
                            text = "Enter the local Wi-Fi IPv4 address of your Windows PC running the Wireless Webcam receiver:",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = tempIpInput,
                            onValueChange = { tempIpInput = it },
                            label = { Text("Server IP Address") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.updateServerIp(tempIpInput)
                        showIpDialog = false
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showIpDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

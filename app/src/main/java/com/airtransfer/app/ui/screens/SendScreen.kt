package com.airtransfer.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airtransfer.app.files.SelectedFile
import com.airtransfer.app.session.SessionState
import com.airtransfer.app.session.TransferOrchestrator
import com.airtransfer.app.ui.components.CameraPreviewBox
import com.airtransfer.app.ui.components.StitchBottomNavBar
import com.airtransfer.app.ui.theme.*
import com.airtransfer.app.vision.*
import java.util.Locale

@Composable
fun SendScreen(
    orchestrator: TransferOrchestrator,
    onBack: () -> Unit,
    onTransferComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFiles by remember { mutableStateOf<List<SelectedFile>>(emptyList()) }
    var landmarks by remember { mutableStateOf<List<NormalizedLandmark>?>(null) }

    val stateMachine = remember {
        GestureStateMachine(GestureRole.SENDER).apply {
            setOnActionListener { action ->
                if (action == "GRAB") {
                    orchestrator.onGrabGestureTriggered()
                }
            }
        }
    }

    var gestureState by remember { mutableStateOf(GestureState()) }
    val sessionState by orchestrator.sessionState.collectAsState()

    // File picker launcher
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        val files = uris.mapNotNull { orchestrator.fileRepository.getFileInfo(it) }
        if (files.isNotEmpty()) {
            selectedFiles = files
            orchestrator.onFilesSelected(files)
        }
    }

    Scaffold(
        bottomBar = { StitchBottomNavBar(selectedTab = "radar") },
        containerColor = StitchSurface
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Back button + Title + Clear all
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(StitchSurfaceContainerHigh)
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = StitchOnSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Send Files",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StitchOnSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(StitchTertiary)
                            )
                            Text(
                                text = "Spatial Hand Engine active",
                                fontSize = 11.sp,
                                color = StitchOnSurfaceVariant
                            )
                        }
                    }
                }

                if (selectedFiles.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            selectedFiles = emptyList()
                            orchestrator.reset()
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.textButtonColors(contentColor = StitchOnSurfaceVariant)
                    ) {
                        Text("Clear all", fontSize = 12.sp)
                    }
                }
            }

            // Selected Files Payload Group
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val totalMB = selectedFiles.sumOf { it.size } / (1024.0 * 1024.0)
                    Text(
                        text = if (selectedFiles.isEmpty()) "Selected Files" else "Selected (${selectedFiles.size} files · ${String.format(Locale.US, "%.1f MB", totalMB)})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = StitchOnSurface
                    )

                    Button(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StitchSurfaceContainerHigh,
                            contentColor = StitchPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (selectedFiles.isEmpty()) "Select Files" else "Add Files", fontSize = 12.sp)
                    }
                }

                if (selectedFiles.isNotEmpty()) {
                    selectedFiles.take(3).forEach { file ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = StitchSurfaceContainerLow),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(StitchSurfaceContainerHigh),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Description,
                                            contentDescription = null,
                                            tint = StitchPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.name,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = StitchOnSurface,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = "${formatBytes(file.size)} • ${file.mimeType.substringAfterLast('/')}",
                                            fontSize = 11.sp,
                                            color = StitchOnSurfaceVariant
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(StitchSurfaceContainer)
                                        .clickable {
                                            selectedFiles = selectedFiles.filter { it.id != file.id }
                                            orchestrator.onFilesSelected(selectedFiles)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = StitchOnSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = StitchSurfaceContainerLow),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { filePicker.launch(arrayOf("*/*")) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, tint = StitchPrimary, modifier = Modifier.size(32.dp))
                            Text("Tap here to pick files to transfer", fontSize = 13.sp, color = StitchOnSurface)
                            Text("Supports photos, videos, documents & folders", fontSize = 11.sp, color = StitchOnSurfaceVariant)
                        }
                    }
                }
            }

            // Gesture Camera Viewport: Spatial Grab Mode
            if (selectedFiles.isNotEmpty() && sessionState !is SessionState.Transferring && sessionState !is SessionState.Completed) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.PanTool, contentDescription = null, tint = StitchPrimary, modifier = Modifier.size(16.dp))
                            Text("Spatial Pinch & Grab", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = StitchOnSurface)
                        }

                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(StitchSurfaceContainerHigh)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(StitchTertiary))
                            Text("Tracking 60fps", fontSize = 10.sp, color = StitchOnSurfaceVariant)
                        }
                    }

                    // Camera Sandbox with Grab Floating Pill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(StitchSurfaceContainerLowest)
                    ) {
                        CameraPreviewBox(
                            landmarks = landmarks,
                            detectedGesture = gestureState.detectedGesture,
                            holdProgress = gestureState.holdProgress,
                            onLandmarksDetected = { lms ->
                                landmarks = lms
                                val classification = GestureClassifier.classify(lms)
                                val prevStep = gestureState.currentStep
                                val newState = stateMachine.process(classification)
                                if (newState.currentStep != prevStep && newState.currentStep == GestureStep.STEP_1_CONFIRMED) {
                                    orchestrator.hapticManager.stepComplete()
                                }
                                gestureState = newState
                            }
                        )

                        // Top Overlays
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(StitchSurfaceContainer.copy(alpha = 0.85f))
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Default.Sensors, contentDescription = null, tint = StitchPrimary, modifier = Modifier.size(12.dp))
                                Text("Leap Target Locked", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = StitchOnSurface)
                            }

                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(StitchSurfaceContainerHigh.copy(alpha = 0.7f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Front Viewfinder", fontSize = 10.sp, color = StitchOnSurfaceVariant)
                            }
                        }

                        // Center: Floating Grabbed Pill if Fist is closed or holding
                        if (gestureState.detectedGesture == HandGesture.FIST || gestureState.currentStep == GestureStep.STEP_2_HOLDING) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = StitchSurfaceContainerHigh.copy(alpha = 0.95f)),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(StitchPrimaryContainer)
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text("Grabbed ✊", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StitchOnPrimaryContainer)
                                        }
                                        Text("Holding file payload", fontSize = 10.sp, color = StitchOnSurfaceVariant)
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(StitchSurfaceContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Description, contentDescription = null, tint = StitchPrimary, modifier = Modifier.size(16.dp))
                                        }
                                        Column {
                                            Text(selectedFiles.firstOrNull()?.name ?: "Files", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = StitchOnSurface, maxLines = 1)
                                            Text("Hold 0.5s to confirm transfer", fontSize = 10.sp, color = StitchTertiary)
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom Overlay: Haptic confirmation badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp)
                                .clip(CircleShape)
                                .background(StitchSurfaceContainerHighest.copy(alpha = 0.85f))
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text("Haptic feedback armed • Ready", fontSize = 10.sp, color = StitchOnSurface, fontWeight = FontWeight.Medium)
                        }
                    }

                    // Stepper / Gesture Guidance State Indicator (3-stage pill flow)
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = StitchSurfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Gesture Pipeline", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = StitchOnSurfaceVariant)
                                Text(
                                    text = when (gestureState.currentStep) {
                                        GestureStep.STEP_1_HOLDING, GestureStep.STEP_1_CONFIRMED -> "Step 1 of 2"
                                        GestureStep.STEP_2_HOLDING -> "Step 2 of 2"
                                        GestureStep.ACTION_TRIGGERED -> "Complete ✓"
                                        else -> "Ready"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = StitchPrimary
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Stage 1
                                val isStep1 = gestureState.currentStep == GestureStep.STEP_1_HOLDING || gestureState.currentStep == GestureStep.IDLE
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isStep1) StitchPrimaryContainer else StitchSurfaceContainer)
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "✋ Show palm",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isStep1) StitchOnPrimaryContainer else StitchOnSurfaceVariant
                                    )
                                }

                                // Stage 2
                                val isStep2 = gestureState.currentStep == GestureStep.STEP_1_CONFIRMED || gestureState.currentStep == GestureStep.STEP_2_HOLDING
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isStep2) StitchPrimaryContainer else StitchSurfaceContainer)
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "✊ Close fist",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isStep2) StitchOnPrimaryContainer else StitchOnSurfaceVariant
                                    )
                                }

                                // Stage 3
                                val isStep3 = gestureState.currentStep == GestureStep.ACTION_TRIGGERED
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isStep3) StitchSuccess.copy(alpha = 0.2f) else StitchSurfaceContainer)
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "🚀 Fling",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isStep3) StitchSuccess else StitchOnSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Transfer / Discovery Status Cards
            when (val state = sessionState) {
                is SessionState.Armed, is SessionState.Discovering -> {
                    val infiniteTransition = rememberInfiniteTransition(label = "spin")
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(animation = tween(2400, easing = LinearEasing)),
                        label = "rot"
                    )

                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = StitchSurfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(StitchSurfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Sync,
                                    contentDescription = null,
                                    tint = StitchPrimary,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .rotate(rotation)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text("Looking for nearby devices…", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = StitchOnSurface)
                                Text("Hold fist closed & aim at receiver", fontSize = 11.sp, color = StitchOnSurfaceVariant)
                            }

                            Icon(Icons.Default.WifiTethering, contentDescription = null, tint = StitchOnSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                is SessionState.DeviceFound, is SessionState.Connecting -> {
                    val deviceName = if (state is SessionState.DeviceFound) state.deviceName else (state as SessionState.Connecting).deviceName
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = StitchSurfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = StitchPrimary)
                            Column {
                                Text("Connecting to $deviceName...", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = StitchOnSurface)
                                Text("Negotiating high-speed Wi-Fi Direct link", fontSize = 11.sp, color = StitchOnSurfaceVariant)
                            }
                        }
                    }
                }

                is SessionState.Transferring -> {
                    // Full Stitch Screen 5: "Transfer - In Progress"
                    InFlightTransferCard(
                        progressList = state.progressList,
                        onCancel = { orchestrator.reset() }
                    )
                }

                is SessionState.Completed -> {
                    LaunchedEffect(Unit) { onTransferComplete() }
                }

                is SessionState.Error -> {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = StitchErrorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Error: ${state.message}",
                            color = StitchError,
                            modifier = Modifier.padding(16.dp),
                            fontSize = 13.sp
                        )
                    }
                }

                else -> {}
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * Stitch Screen 5: "Transfer - In Progress" implementation
 */
@Composable
private fun InFlightTransferCard(
    progressList: List<com.airtransfer.app.transfer.FileTransferProgress>,
    onCancel: () -> Unit
) {
    val totalBytes = progressList.sumOf { it.fileSize }
    val transferredBytes = progressList.sumOf { it.bytesTransferred }
    val avgSpeed = progressList.maxOfOrNull { it.speedBytesPerSec } ?: 0L
    val overallPercent = if (totalBytes > 0) ((transferredBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Focus header
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${progressList.size} files · ${formatBytes(totalBytes)}",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = StitchOnSurface
            )
            Text(
                text = "Sending over high-speed P2P Wi-Fi",
                fontSize = 13.sp,
                color = StitchPrimary
            )
        }

        // Elevated Floating File Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = StitchSurfaceContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header in transit badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(StitchSecondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.FlightTakeoff, contentDescription = null, tint = StitchOnPrimaryContainer, modifier = Modifier.size(14.dp))
                        }
                        Text("In transit", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = StitchOnSurface)
                    }

                    Text("$overallPercent%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = StitchPrimary)
                }

                // Progress track
                LinearProgressIndicator(
                    progress = { overallPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = StitchPrimaryContainer,
                    trackColor = StitchSurfaceContainerHighest
                )

                // Transferred bytes & ETA breakdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Payload Transferred", fontSize = 11.sp, color = StitchOnSurfaceVariant)
                        Text(formatBytes(transferredBytes), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = StitchOnSurface)
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text("Speed", fontSize = 11.sp, color = StitchOnSurfaceVariant)
                        Text(
                            if (avgSpeed > 0) "${formatBytes(avgSpeed)}/s" else "Streaming",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StitchPrimary
                        )
                    }
                }
            }
        }

        // Cancel action
        Button(
            onClick = onCancel,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = StitchSurfaceContainerHigh,
                contentColor = StitchOnSurface
            ),
            modifier = Modifier.width(180.dp)
        ) {
            Text("Cancel", fontSize = 13.sp)
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

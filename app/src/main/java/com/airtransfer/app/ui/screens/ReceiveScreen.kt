package com.airtransfer.app.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airtransfer.app.session.SessionState
import com.airtransfer.app.session.TransferOrchestrator
import com.airtransfer.app.ui.components.CameraPreviewBox
import com.airtransfer.app.ui.components.StitchBottomNavBar
import com.airtransfer.app.ui.components.TransferProgressCard
import com.airtransfer.app.ui.theme.*
import com.airtransfer.app.vision.*

@Composable
fun ReceiveScreen(
    orchestrator: TransferOrchestrator,
    onBack: () -> Unit,
    onTransferComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var landmarks by remember { mutableStateOf<List<NormalizedLandmark>?>(null) }

    val stateMachine = remember {
        GestureStateMachine(GestureRole.RECEIVER).apply {
            setOnActionListener { action ->
                if (action == "RELEASE") {
                    orchestrator.onReleaseGestureTriggered()
                }
            }
        }
    }

    var gestureState by remember { mutableStateOf(GestureState()) }
    val sessionState by orchestrator.sessionState.collectAsState()

    LaunchedEffect(Unit) {
        orchestrator.startReceiverMode()
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
            // Top Bar Modal Context
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Receive",
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
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(StitchTertiary)
                        )
                        Text(
                            text = "Spatial UWB Active",
                            fontSize = 11.sp,
                            color = StitchOnSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(StitchSurfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "Settings",
                        tint = StitchOnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Hero Viewfinder Container (Aspect ~ 4/5, rounded-3xl)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(StitchSurfaceContainerLowest)
            ) {
                // Live camera preview layer
                if (sessionState !is SessionState.Transferring && sessionState !is SessionState.Completed) {
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
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Vignette gradient overlays
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    StitchSurfaceContainerLowest.copy(alpha = 0.65f),
                                    Color.Transparent,
                                    StitchSurfaceContainerLowest.copy(alpha = 0.85f)
                                )
                            )
                        )
                )

                // Viewfinder Content Overlay
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Viewfinder Status Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(StitchSurfaceContainer.copy(alpha = 0.85f))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.ViewInAr,
                                contentDescription = null,
                                tint = StitchPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Visual Handshake",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = StitchOnSurface
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(StitchSurfaceContainer.copy(alpha = 0.75f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.FlipCameraAndroid,
                                contentDescription = "Flip Camera",
                                tint = StitchOnSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Center Viewfinder Reticle / Spatial Catch Spot
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Corner brackets
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .size(16.dp)
                                    .border(
                                        width = 2.5.dp,
                                        color = StitchTertiary.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(topStart = 6.dp)
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(16.dp)
                                    .border(
                                        width = 2.5.dp,
                                        color = StitchTertiary.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(topEnd = 6.dp)
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .size(16.dp)
                                    .border(
                                        width = 2.5.dp,
                                        color = StitchTertiary.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(bottomStart = 6.dp)
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(16.dp)
                                    .border(
                                        width = 2.5.dp,
                                        color = StitchTertiary.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(bottomEnd = 6.dp)
                                    )
                            )
                        }

                        // Pulsing center hand silhouette container
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.08f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1400, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "scale"
                        )

                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .scale(if (gestureState.detectedGesture != HandGesture.UNKNOWN) 1.15f else scale)
                                .clip(CircleShape)
                                .background(
                                    if (gestureState.detectedGesture == HandGesture.OPEN_PALM)
                                        StitchPrimary
                                    else
                                        StitchSurfaceContainer.copy(alpha = 0.9f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (gestureState.detectedGesture) {
                                    HandGesture.FIST -> "✊"
                                    HandGesture.OPEN_PALM -> "🖐️"
                                    else -> "✋"
                                },
                                fontSize = 28.sp
                            )
                        }
                    }

                    // Bottom Floating Incoming Card / Status Card inside Viewfinder
                    when (val state = sessionState) {
                        is SessionState.IncomingRequest -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(StitchSurfaceContainer.copy(alpha = 0.95f))
                                    .padding(14.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(StitchSurfaceContainerHigh),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.PhoneAndroid,
                                                contentDescription = null,
                                                tint = StitchPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = state.deviceName,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = StitchOnSurface
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(StitchPrimary.copy(alpha = 0.15f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "Nearby",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = StitchPrimary
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "wants to send files",
                                                fontSize = 12.sp,
                                                color = StitchOnSurfaceVariant
                                            )
                                        }
                                    }

                                    Text(
                                        text = "Open your palm to accept and catch payload",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = StitchPrimary,
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { orchestrator.reset() },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(20.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = StitchSurfaceContainerHigh,
                                                contentColor = StitchOnSurface
                                            ),
                                            contentPadding = PaddingValues(vertical = 10.dp)
                                        ) {
                                            Text("Decline", fontSize = 13.sp)
                                        }

                                        Button(
                                            onClick = { orchestrator.onReleaseGestureTriggered() },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(20.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = StitchPrimary,
                                                contentColor = StitchOnPrimary
                                            ),
                                            contentPadding = PaddingValues(vertical = 10.dp)
                                        ) {
                                            Text("Accept 🖐️", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }

                        is SessionState.Connecting -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(StitchSurfaceContainer.copy(alpha = 0.9f))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = StitchPrimary
                                    )
                                    Text(
                                        text = "Connecting to sender...",
                                        fontSize = 12.sp,
                                        color = StitchOnSurface
                                    )
                                }
                            }
                        }

                        is SessionState.GestureArmed -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(StitchSurfaceContainer.copy(alpha = 0.9f))
                                    .padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("🖐️", fontSize = 18.sp)
                                    Text(
                                        text = "Gesture confirmed! Ready to receive...",
                                        fontSize = 12.sp,
                                        color = StitchPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        else -> {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(StitchSurfaceContainerLowest.copy(alpha = 0.85f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ScreenLockPortrait,
                                        contentDescription = null,
                                        tint = StitchPrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Hold phone upright toward sender",
                                        fontSize = 11.sp,
                                        color = StitchOnSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Transfer In Progress Card
            if (sessionState is SessionState.Transferring) {
                val state = sessionState as SessionState.Transferring
                TransferProgressCard(progressList = state.progressList)
            }

            // Completed State Action Card
            if (sessionState is SessionState.Completed) {
                val state = sessionState as SessionState.Completed
                TransferProgressCard(progressList = state.files)
                Button(
                    onClick = onTransferComplete,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StitchPrimary,
                        contentColor = StitchOnPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("View Received Files", fontWeight = FontWeight.SemiBold)
                }
            }

            // Instruction Subtext
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Ready to receive",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = StitchOnSurface
                )
                Text(
                    text = "Natural peer-to-peer spatial transfer. Hold steady in line of sight.",
                    fontSize = 12.sp,
                    color = StitchOnSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Gesture Step Flow Guide Card (Stitch Screen 6)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(StitchSurfaceContainer)
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GESTURE GUIDE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StitchOnSurfaceVariant,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Automatic Detection",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = StitchPrimary
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(StitchSurfaceContainerLow)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Step 1
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(StitchSurfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✊", fontSize = 16.sp)
                            }
                            Column {
                                Text(
                                    text = "Close fist",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = StitchOnSurface
                                )
                                Text(
                                    text = "Lock sender link",
                                    fontSize = 10.sp,
                                    color = StitchOnSurfaceVariant
                                )
                            }
                        }

                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = StitchOutlineVariant,
                            modifier = Modifier.size(16.dp)
                        )

                        // Step 2
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(StitchPrimary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🖐️", fontSize = 16.sp)
                            }
                            Column {
                                Text(
                                    text = "Open palm",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = StitchPrimary
                                )
                                Text(
                                    text = "Catch payload",
                                    fontSize = 10.sp,
                                    color = StitchOnSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Error feedback
            if (sessionState is SessionState.Error) {
                val err = sessionState as SessionState.Error
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Error: ${err.message}",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

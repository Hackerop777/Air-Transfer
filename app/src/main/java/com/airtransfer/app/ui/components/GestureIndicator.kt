package com.airtransfer.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airtransfer.app.vision.*

@Composable
fun GestureIndicator(
    role: GestureRole,
    gestureState: GestureState,
    modifier: Modifier = Modifier
) {
    val isSender = role == GestureRole.SENDER
    val step1Target = if (isSender) HandGesture.OPEN_PALM else HandGesture.FIST
    val step2Target = if (isSender) HandGesture.FIST else HandGesture.OPEN_PALM

    val isStep1Done = gestureState.currentStep == GestureStep.STEP_1_CONFIRMED ||
            gestureState.currentStep == GestureStep.STEP_2_HOLDING ||
            gestureState.currentStep == GestureStep.ACTION_TRIGGERED

    val isActionDone = gestureState.currentStep == GestureStep.ACTION_TRIGGERED

    val animatedHoldProgress by animateFloatAsState(
        targetValue = gestureState.holdProgress,
        label = "holdProgress"
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Steps indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Step 1
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (isStep1Done) Color(0xFF10B981)
                                else if (gestureState.expectedGesture == step1Target) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isStep1Done) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        } else {
                            Text("1", color = Color.White, fontSize = 12.sp)
                        }
                    }
                    Column {
                        Text(
                            text = if (step1Target == HandGesture.OPEN_PALM) "Open Palm ✋" else "Closed Fist ✊",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text("Step 1", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Text("→", color = MaterialTheme.colorScheme.outlineVariant)

                // Step 2
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActionDone) Color(0xFF10B981)
                                else if (gestureState.expectedGesture == step2Target && isStep1Done) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isActionDone) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        } else {
                            Text("2", color = Color.White, fontSize = 12.sp)
                        }
                    }
                    Column {
                        Text(
                            text = if (step2Target == HandGesture.FIST) "Close Fist ✊" else "Open Palm 🖐",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(if (isSender) "Grab Files" else "Accept Files", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Hold Progress Bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = when (gestureState.detectedGesture) {
                            HandGesture.OPEN_PALM -> "✋ Palm Detected (${(gestureState.confidence * 100).toInt()}%)"
                            HandGesture.FIST -> "✊ Fist Detected (${(gestureState.confidence * 100).toInt()}%)"
                            else -> "Scanning for hand..."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (gestureState.holdProgress > 0f) {
                        Text(
                            text = "${(gestureState.holdProgress * 100).toInt()}% held",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = { animatedHoldProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Status message
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isActionDone) Color(0xFFECFDF5) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = gestureState.statusMessage.ifEmpty { "Keep hand visible in camera frame" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isActionDone) Color(0xFF047857) else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                )
            }
        }
    }
}

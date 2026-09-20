package com.airtransfer.app.ui.screens

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airtransfer.app.ui.components.AirTransferGlyph

@Composable
fun AirTransferV2Screen(
    isServiceRunning: Boolean,
    connectedPeersCount: Int = 0,
    primaryPeerName: String? = null,
    onToggleGestures: (Boolean) -> Unit,
    hasCameraPermission: Boolean,
    hasOverlayPermission: Boolean,
    hasNotificationPermission: Boolean,
    onRequestCamera: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestNotification: () -> Unit,
    onOpenLegacyV1: () -> Unit
) {
    val scrollState = rememberScrollState()

    val bgGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF090A0F),
            Color(0xFF0F111A),
            Color(0xFF090A0F)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(28.dp))

        // Brand Glyph
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E293B).copy(alpha = 0.5f))
                .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AirTransferGlyph(modifier = Modifier.size(44.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Title
        Text(
            text = "AIR TRANSFER",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF38BDF8)
            )
        )

        Text(
            text = "Touchless Spatial Screen Sharing",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color.White.copy(alpha = 0.6f)
            ),
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 1. MASTER TOGGLE CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = if (isServiceRunning) Color(0xFF38BDF8).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(24.dp)
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131622))
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Air Gestures",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = if (isServiceRunning) "System-wide tracking active" else "Touchless gesture detection",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isServiceRunning) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.5f)
                            )
                        )
                    }

                    Switch(
                        checked = isServiceRunning,
                        onCheckedChange = onToggleGestures,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF0284C7),
                            uncheckedThumbColor = Color.LightGray,
                            uncheckedTrackColor = Color(0xFF334155)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Live status pill
                val pillColor by animateColorAsState(
                    targetValue = if (isServiceRunning) Color(0xFF10B981) else Color.Gray,
                    label = "status_color"
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(pillColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isServiceRunning)
                            "Air Gestures are active across your phone"
                        else
                            "Air Gestures are disabled",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                if (isServiceRunning) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (connectedPeersCount > 0) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFF0284C7).copy(alpha = 0.12f))
                            .border(1.dp, if (connectedPeersCount > 0) Color(0xFF10B981).copy(alpha = 0.3f) else Color(0xFF0284C7).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = if (connectedPeersCount > 0) "🟢" else "🔍",
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (connectedPeersCount > 0)
                                "Ready with ${primaryPeerName ?: "nearby device"} ($connectedPeersCount connected)"
                            else
                                "Discovering nearby Air Transfer devices...",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (connectedPeersCount > 0) Color(0xFF34D399) else Color(0xFF38BDF8),
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2. PERMISSION ONBOARDING CARDS (if any required)
        AnimatedVisibility(visible = !hasCameraPermission || !hasOverlayPermission || !hasNotificationPermission) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "SETUP REQUIRED",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp,
                        color = Color(0xFFF59E0B)
                    )
                )

                if (!hasCameraPermission) {
                    PermissionCard(
                        title = "Camera Access",
                        subtitle = "Required to detect hand gestures",
                        icon = Icons.Default.CameraAlt,
                        onAction = onRequestCamera
                    )
                }

                if (!hasOverlayPermission) {
                    PermissionCard(
                        title = "Display Over Other Apps",
                        subtitle = "Required to show hand indicator and floating card",
                        icon = Icons.Default.Layers,
                        onAction = onRequestOverlay
                    )
                }

                if (!hasNotificationPermission) {
                    PermissionCard(
                        title = "Notification Permission",
                        subtitle = "Allows disabling air gestures anytime",
                        icon = Icons.Default.CheckCircle,
                        onAction = onRequestNotification
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        // 3. SECONDARY STATUS MODULES
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusChip(
                label = "Camera Detection",
                isActive = hasCameraPermission,
                icon = Icons.Default.CameraAlt,
                modifier = Modifier.weight(1f)
            )
            StatusChip(
                label = "Screen Capture",
                isActive = isServiceRunning,
                icon = Icons.Default.ScreenShare,
                modifier = Modifier.weight(1f)
            )
            StatusChip(
                label = "Nearby Presence",
                isActive = isServiceRunning,
                icon = Icons.Default.Share,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 4. HOW IT WORKS
        Text(
            text = "HOW IT WORKS",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp,
                color = Color(0xFF38BDF8)
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131622).copy(alpha = 0.8f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HowItWorksStep(step = "1", icon = "✋", title = "Open Palm", desc = "Show your open palm to arm gesture detection.")
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                HowItWorksStep(step = "2", icon = "✊", title = "Close Fist", desc = "Grab the screen. It lifts into a floating card.")
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                HowItWorksStep(step = "3", icon = "⇄", title = "Move", desc = "Move your closed fist toward the nearby device.")
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                HowItWorksStep(step = "4", icon = "🖐", title = "Release", desc = "Open your palm at the receiver to finish transfer.")
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 5. LEGACY V1 ACCESS
        TextButton(
            onClick = onOpenLegacyV1,
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            Text(
                text = "Switch to Legacy In-App Mode (V1)",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.4f),
                    fontFamily = FontFamily.Monospace
                )
            )
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    isActive: Boolean,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.border(
            1.dp,
            if (isActive) Color(0xFF38BDF8).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
            RoundedCornerShape(16.dp)
        ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131622))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) Color(0xFF38BDF8) else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = if (isActive) Color.White.copy(alpha = 0.85f) else Color.Gray,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1813))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    )
                }
            }

            Button(
                onClick = onAction,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Grant",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
private fun HowItWorksStep(
    step: String,
    icon: String,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon, fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = "$step. $title",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.6f)
                )
            )
        }
    }
}
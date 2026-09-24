package com.airtransfer.app.ui.screens

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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

    // Eye-comforting porcelain canvas
    val surfaceBg = Color(0xFFF8FAFC)
    val cardBg = Color(0xFFFFFFFF)
    val borderColor = Color(0xFFE2E8F0)
    val textPrimary = Color(0xFF0F172A)
    val textSecondary = Color(0xFF64748B)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(surfaceBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Brand Icon
        Box(
            modifier = Modifier
                .size(68.dp)
                .shadow(elevation = 3.dp, shape = CircleShape, spotColor = Color(0x1A0F172A))
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, borderColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AirTransferGlyph(modifier = Modifier.size(40.dp))
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Clean Modern Title
        Text(
            text = "Air Transfer",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                color = textPrimary
            )
        )

        Text(
            text = "Touchless screen grab & share",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = textSecondary
            ),
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // 1. MASTER TOGGLE CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 2.dp, shape = RoundedCornerShape(22.dp), spotColor = Color(0x120F172A))
                .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            Column(
                modifier = Modifier.padding(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Air Gestures",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = textPrimary
                            )
                        )
                        Text(
                            text = if (isServiceRunning) "Touchless detection is active" else "Grab screen with fist gesture",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isServiceRunning) Color(0xFF059669) else textSecondary
                            )
                        )
                    }

                    Switch(
                        checked = isServiceRunning,
                        onCheckedChange = onToggleGestures,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF2563EB),
                            uncheckedThumbColor = Color(0xFFCBD5E1),
                            uncheckedTrackColor = Color(0xFFE2E8F0),
                            uncheckedBorderColor = Color.Transparent
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Service Status Pill
                val pillDotColor by animateColorAsState(
                    targetValue = if (isServiceRunning) Color(0xFF10B981) else Color(0xFF94A3B8),
                    label = "status_dot_color"
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isServiceRunning) Color(0xFFECFDF5) else Color(0xFFF1F5F9))
                        .border(1.dp, if (isServiceRunning) Color(0xFFA7F3D0) else Color(0xFFE2E8F0), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(pillDotColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isServiceRunning)
                            "Ready across any app on your phone"
                        else
                            "Turn on to grab and share screens with air gestures",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isServiceRunning) Color(0xFF065F46) else Color(0xFF475569),
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                // Peer presence status pill
                if (isServiceRunning) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val isConnected = connectedPeersCount > 0
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isConnected) Color(0xFFECFDF5) else Color(0xFFF0F9FF))
                            .border(1.dp, if (isConnected) Color(0xFFA7F3D0) else Color(0xFFBAE6FD), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) Color(0xFF10B981) else Color(0xFF0284C7))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isConnected)
                                "Connected with ${primaryPeerName ?: "nearby phone"} ($connectedPeersCount nearby)"
                            else
                                "Searching for nearby Air Transfer devices...",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isConnected) Color(0xFF047857) else Color(0xFF0369A1),
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 2. PERMISSION ONBOARDING CARDS (if any missing)
        AnimatedVisibility(visible = !hasCameraPermission || !hasOverlayPermission || !hasNotificationPermission) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "SETUP REQUIRED",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD97706)
                    )
                )

                if (!hasCameraPermission) {
                    PermissionCard(
                        title = "Camera Access",
                        subtitle = "Required for touchless palm & fist recognition",
                        icon = Icons.Default.CameraAlt,
                        onAction = onRequestCamera
                    )
                }

                if (!hasOverlayPermission) {
                    PermissionCard(
                        title = "Display Over Other Apps",
                        subtitle = "Required to show hand gesture indicators",
                        icon = Icons.Default.Layers,
                        onAction = onRequestOverlay
                    )
                }

                if (!hasNotificationPermission) {
                    PermissionCard(
                        title = "Notification Permission",
                        subtitle = "Allows background service control",
                        icon = Icons.Default.CheckCircle,
                        onAction = onRequestNotification
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // 3. SECONDARY STATUS MODULES
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatusChip(
                label = "Camera",
                sublabel = if (hasCameraPermission) "Active" else "Off",
                isActive = hasCameraPermission,
                icon = Icons.Default.CameraAlt,
                modifier = Modifier.weight(1f)
            )
            StatusChip(
                label = "Capture",
                sublabel = if (isServiceRunning) "Ready" else "Idle",
                isActive = isServiceRunning,
                icon = Icons.Default.ScreenShare,
                modifier = Modifier.weight(1f)
            )
            StatusChip(
                label = "Nearby",
                sublabel = if (connectedPeersCount > 0) "Connected" else "Idle",
                isActive = isServiceRunning,
                icon = Icons.Default.Share,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // 4. HOW IT WORKS
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "HOW IT WORKS",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold,
                    color = textSecondary
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 1.5.dp, shape = RoundedCornerShape(20.dp), spotColor = Color(0x100F172A))
                    .border(1.dp, borderColor, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    HowItWorksStep(
                        step = "1",
                        title = "Show Open Palm",
                        desc = "Face the front camera with an open palm to activate gesture grab."
                    )
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    HowItWorksStep(
                        step = "2",
                        title = "Close Your Fist",
                        desc = "Grab the screen. It seamlessly shrinks into a floating card."
                    )
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    HowItWorksStep(
                        step = "3",
                        title = "Move Towards Device",
                        desc = "Keep your fist closed and move your hand towards the receiving phone."
                    )
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    HowItWorksStep(
                        step = "4",
                        title = "Open Palm to Catch",
                        desc = "Open your palm in front of the receiver. The screenshot appears with luminous ripples and opens in Gallery."
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 5. LEGACY V1 ACCESS
        TextButton(
            onClick = onOpenLegacyV1,
            modifier = Modifier.padding(bottom = 20.dp)
        ) {
            Text(
                text = "Switch to Classic Mode (V1)",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = textSecondary,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    sublabel: String,
    isActive: Boolean,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .shadow(elevation = 1.dp, shape = RoundedCornerShape(16.dp), spotColor = Color(0x0C0F172A))
            .border(
                1.dp,
                if (isActive) Color(0xFFBAE6FD) else Color(0xFFE2E8F0),
                RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Color(0xFFF0F9FF) else Color(0xFFFFFFFF)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) Color(0xFF0284C7) else Color(0xFF94A3B8),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0F172A),
                    textAlign = TextAlign.Center
                )
            )
            Text(
                text = sublabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = if (isActive) Color(0xFF0369A1) else Color(0xFF94A3B8),
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
            .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB))
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
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF92400E)
                        )
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFFB45309)
                        )
                    )
                }
            }

            Button(
                onClick = onAction,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Grant",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White,
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
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xFFF1F5F9))
                .border(1.dp, Color(0xFFE2E8F0), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF334155)
                )
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0F172A)
                )
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF64748B),
                    lineHeight = 18.sp
                )
            )
        }
    }
}
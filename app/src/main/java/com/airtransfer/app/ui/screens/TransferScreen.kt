package com.airtransfer.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.airtransfer.app.session.SessionState
import com.airtransfer.app.session.TransferOrchestrator
import com.airtransfer.app.ui.components.StitchBottomNavBar
import com.airtransfer.app.ui.theme.*
import java.io.File
import java.util.Locale

@Composable
fun TransferScreen(
    orchestrator: TransferOrchestrator,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sessionState by orchestrator.sessionState.collectAsState()
    var gestureSharingEnabled by remember { mutableStateOf(true) }

    val completedFiles = remember(sessionState) {
        when (val state = sessionState) {
            is SessionState.Completed -> state.files
            else -> emptyList()
        }
    }

    val totalBytes: Long = remember(completedFiles) {
        completedFiles.fold(0L) { acc, item -> acc + item.fileSize }
    }

    Scaffold(
        bottomBar = { StitchBottomNavBar(selectedTab = "radar") },
        containerColor = StitchSurface
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Calm Arrival Icon with Ambient Glow (Stitch Screen 7)
            Box(
                modifier = Modifier.size(110.dp),
                contentAlignment = Alignment.Center
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "glow")
                val glowScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.12f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1600, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "glowScale"
                )

                // Ambient glow ring
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(glowScale)
                        .clip(CircleShape)
                        .background(StitchPrimary.copy(alpha = 0.2f))
                )

                // Elevated outer circle
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(StitchSurfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    // Center primary badge
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(StitchPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Success",
                            tint = StitchOnPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            // Typography & Source Narrative
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Transfer complete",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = StitchOnSurface
                )

                val fileCount = completedFiles.size
                val sizeString = formatBytes(totalBytes)
                Text(
                    text = "$fileCount files · $sizeString transferred",
                    fontSize = 14.sp,
                    color = StitchOnSurfaceVariant
                )
            }

            // Settled Files Container (Stitch Screen 7)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(StitchSurfaceContainer)
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Storage Destination Breadcrumb
                    val dir = orchestrator.fileRepository.getReceivedFilesDir()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(StitchSurfaceContainerHighest),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = StitchPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Text(
                                text = "Saved to Downloads / Air Transfer",
                                fontSize = 12.sp,
                                color = StitchOnSurfaceVariant,
                                maxLines = 1
                            )
                        }

                        Text(
                            text = "View",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StitchPrimary,
                            modifier = Modifier.clickable {
                                try {
                                    val intent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Saved to Downloads/AirTransfer", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    HorizontalDivider(color = StitchSurfaceContainerHighest.copy(alpha = 0.6f))

                    // Settled Items List
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        completedFiles.forEach { file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(StitchSurfaceContainerLow)
                                    .padding(10.dp),
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
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(StitchSurfaceContainerHighest),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val icon = when {
                                            file.fileName.endsWith(".jpg", true) ||
                                            file.fileName.endsWith(".png", true) ||
                                            file.fileName.endsWith(".webp", true) -> Icons.Default.Image
                                            file.fileName.endsWith(".pdf", true) -> Icons.Default.Description
                                            else -> Icons.Default.InsertDriveFile
                                        }
                                        Icon(
                                            icon,
                                            contentDescription = null,
                                            tint = StitchPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.fileName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = StitchOnSurface,
                                            maxLines = 1
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = formatBytes(file.fileSize),
                                                fontSize = 11.sp,
                                                color = StitchOnSurfaceVariant
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(3.dp)
                                                    .clip(CircleShape)
                                                    .background(StitchOnSurfaceVariant.copy(alpha = 0.5f))
                                            )
                                            Text(
                                                text = "Done ✓",
                                                fontSize = 11.sp,
                                                color = StitchTertiary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            try {
                                                val localFile = File(dir, file.fileName)
                                                if (localFile.exists()) {
                                                    val uri = FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.fileprovider",
                                                        localFile
                                                    )
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    }
                                                    context.startActivity(intent)
                                                }
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Opening ${file.fileName}", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.OpenInNew,
                                        contentDescription = "Open file",
                                        tint = StitchOnSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Primary Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val dir = orchestrator.fileRepository.getReceivedFilesDir()
                            Toast.makeText(context, "Saved to: ${dir.absolutePath}", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StitchPrimary,
                        contentColor = StitchOnPrimary
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("View Files", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Button(
                    onClick = {
                        orchestrator.reset()
                        onDone()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StitchSurfaceContainerHigh,
                        contentColor = StitchOnSurface
                    )
                ) {
                    Text("Done", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }

            // Modern Peer Gesture Footnote with Switch (Stitch Screen 7)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(StitchSurfaceContainerLow)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(StitchSurfaceContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Gesture,
                                contentDescription = null,
                                tint = StitchPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Share with gesture",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = StitchOnSurface
                            )
                            Text(
                                text = "Bump or hold devices close",
                                fontSize = 11.sp,
                                color = StitchOnSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = gestureSharingEnabled,
                        onCheckedChange = {
                            gestureSharingEnabled = it
                            Toast.makeText(
                                context,
                                if (it) "Gesture sharing active" else "Gesture sharing disabled",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = StitchOnPrimary,
                            checkedTrackColor = StitchPrimary,
                            uncheckedThumbColor = StitchOnSurfaceVariant,
                            uncheckedTrackColor = StitchSurfaceContainerHighest
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

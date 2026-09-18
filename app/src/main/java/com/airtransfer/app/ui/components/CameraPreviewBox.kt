package com.airtransfer.app.ui.components

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.airtransfer.app.camera.CameraManager
import com.airtransfer.app.vision.HandGesture
import com.airtransfer.app.vision.HandLandmarkerHelper
import com.airtransfer.app.vision.NormalizedLandmark

private val HAND_CONNECTIONS = listOf(
    Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4),
    Pair(0, 5), Pair(5, 6), Pair(6, 7), Pair(7, 8),
    Pair(9, 10), Pair(10, 11), Pair(11, 12),
    Pair(13, 14), Pair(14, 15), Pair(15, 16),
    Pair(0, 17), Pair(17, 18), Pair(18, 19), Pair(19, 20),
    Pair(5, 9), Pair(9, 13), Pair(13, 17)
)

@Composable
fun CameraPreviewBox(
    landmarks: List<NormalizedLandmark>?,
    detectedGesture: HandGesture,
    holdProgress: Float,
    onLandmarksDetected: (List<NormalizedLandmark>?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var cameraManager by remember { mutableStateOf<CameraManager?>(null) }

    // Initialize HandLandmarkerHelper synchronously in remember to guarantee non-null reference
    val handHelper = remember {
        HandLandmarkerHelper(
            context = context,
            onResult = { lms, _ -> onLandmarksDetected(lms) },
            onError = { err -> errorMessage = err }
        )
    }

    DisposableEffect(handHelper) {
        onDispose {
            cameraManager?.stopCamera()
            handHelper.close()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(24.dp))
    ) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    val mgr = CameraManager(
                        context = ctx,
                        lifecycleOwner = lifecycleOwner,
                        previewView = this,
                        handLandmarkerHelper = handHelper
                    )
                    cameraManager = mgr
                    mgr.startCamera()
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Guide box overlay
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(180.dp)
                .border(
                    width = 2.dp,
                    color = Color.White.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(20.dp)
                )
        )

        // Error message banner if any
        errorMessage?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
            )
        }

        // Hand landmark skeleton Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            landmarks?.let { lms ->
                if (lms.size >= 21) {
                    val w = size.width
                    val h = size.height

                    val boneColor = when (detectedGesture) {
                        HandGesture.OPEN_PALM -> Color(0xFF10B981)
                        HandGesture.FIST -> Color(0xFF8B5CF6)
                        else -> Color(0xFF3B82F6)
                    }

                    // Draw bones
                    for ((start, end) in HAND_CONNECTIONS) {
                        val p1 = lms[start]
                        val p2 = lms[end]
                        drawLine(
                            color = boneColor.copy(alpha = 0.8f),
                            start = Offset(p1.x * w, p1.y * h),
                            end = Offset(p2.x * w, p2.y * h),
                            strokeWidth = 6f
                        )
                    }

                    // Draw joints
                    lms.forEachIndexed { idx, p ->
                        val isTip = idx in listOf(4, 8, 12, 16, 20)
                        val r = if (idx == 0) 10f else if (isTip) 8f else 6f
                        drawCircle(
                            color = if (isTip) Color.White else boneColor,
                            radius = r,
                            center = Offset(p.x * w, p.y * h)
                        )
                    }

                    // Draw progress ring around palm center (landmark 9)
                    if (holdProgress > 0f) {
                        val palm = lms[9]
                        drawArc(
                            color = boneColor,
                            startAngle = -90f,
                            sweepAngle = holdProgress * 360f,
                            useCenter = false,
                            topLeft = Offset(palm.x * w - 35f, palm.y * h - 35f),
                            size = androidx.compose.ui.geometry.Size(70f, 70f),
                            style = Stroke(width = 8f)
                        )
                    }
                }
            }
        }

        // Camera switch button
        IconButton(
            onClick = { cameraManager?.flipCamera() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Icon(
                Icons.Default.Cameraswitch,
                contentDescription = "Flip Camera",
                tint = Color.White
            )
        }
    }
}

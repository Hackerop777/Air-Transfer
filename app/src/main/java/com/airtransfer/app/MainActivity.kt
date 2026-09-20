package com.airtransfer.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.airtransfer.app.service.AirGestureService
import com.airtransfer.app.session.TransferOrchestrator
import com.airtransfer.app.ui.screens.*
import com.airtransfer.app.ui.theme.AirTransferTheme

class MainActivity : ComponentActivity() {

    private lateinit var orchestrator: TransferOrchestrator

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(this, AirGestureService::class.java).apply {
                action = AirGestureService.ACTION_START_GESTURES
                putExtra(AirGestureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(AirGestureService.EXTRA_RESULT_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "Air Gestures enabled! Move to any app to grab screens.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Screen capture permission is required for air grab", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        orchestrator = TransferOrchestrator(this)

        setContent {
            AirTransferTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        orchestrator = orchestrator,
                        onRequestStartGestures = { startAirGesturesFlow() },
                        onRequestStopGestures = { stopAirGesturesFlow() }
                    )
                }
            }
        }
    }

    fun startAirGesturesFlow() {
        // 1. Check Camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Please grant Camera permission", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. Check Overlay
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Toast.makeText(this, "Please allow 'Display over other apps'", Toast.LENGTH_LONG).show()
            return
        }

        // 3. Request MediaProjection Consent
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
    }

    fun stopAirGesturesFlow() {
        val intent = Intent(this, AirGestureService::class.java).apply {
            action = AirGestureService.ACTION_STOP_GESTURES
        }
        startService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        orchestrator.reset()
    }
}

@Composable
fun AppNavigation(
    orchestrator: TransferOrchestrator,
    onRequestStartGestures: () -> Unit,
    onRequestStopGestures: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val navController = rememberNavController()

    val isServiceRunning by AirGestureService.isServiceRunningFlow.collectAsState()
    val connectedPeersCount by AirGestureService.connectedPeersCountFlow.collectAsState()
    val primaryPeerName by AirGestureService.primaryPeerNameFlow.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    // Nearby and storage permissions launcher
    val nearbyPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
                add(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                @Suppress("DEPRECATION")
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        nearbyPermissionsLauncher.launch(permissions.toTypedArray())
    }

    NavHost(navController = navController, startDestination = "v2_home") {
        // V2 Primary Spatial Experience
        composable("v2_home") {
            AirTransferV2Screen(
                isServiceRunning = isServiceRunning,
                connectedPeersCount = connectedPeersCount,
                primaryPeerName = primaryPeerName,
                onToggleGestures = { enable ->
                    if (enable) onRequestStartGestures() else onRequestStopGestures()
                },
                hasCameraPermission = hasCameraPermission,
                hasOverlayPermission = hasOverlayPermission,
                hasNotificationPermission = hasNotificationPermission,
                onRequestCamera = {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onRequestOverlay = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                },
                onRequestNotification = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onOpenLegacyV1 = {
                    navController.navigate("v1_home")
                }
            )
        }

        // V1 Legacy In-App Experience (Preserved in isolation)
        composable("v1_home") {
            HomeScreen(
                onNavigateToSend = { navController.navigate("v1_send") },
                onNavigateToReceive = { navController.navigate("v1_receive") }
            )
        }
        composable("v1_send") {
            SendScreen(
                orchestrator = orchestrator,
                onBack = { navController.popBackStack() },
                onTransferComplete = { navController.navigate("v1_transfer") }
            )
        }
        composable("v1_receive") {
            ReceiveScreen(
                orchestrator = orchestrator,
                onBack = { navController.popBackStack() },
                onTransferComplete = { navController.navigate("v1_transfer") }
            )
        }
        composable("v1_transfer") {
            TransferScreen(
                orchestrator = orchestrator,
                onDone = { navController.navigate("v2_home") { popUpTo("v2_home") { inclusive = true } } }
            )
        }
    }
}
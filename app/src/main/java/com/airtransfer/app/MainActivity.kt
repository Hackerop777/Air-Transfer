package com.airtransfer.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import com.airtransfer.app.session.TransferOrchestrator
import com.airtransfer.app.ui.screens.HomeScreen
import com.airtransfer.app.ui.screens.ReceiveScreen
import com.airtransfer.app.ui.screens.SendScreen
import com.airtransfer.app.ui.screens.TransferScreen
import com.airtransfer.app.ui.theme.AirTransferTheme

class MainActivity : ComponentActivity() {

    private lateinit var orchestrator: TransferOrchestrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        orchestrator = TransferOrchestrator(this)

        setContent {
            AirTransferTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RequestPermissions {
                        AppNavigation(orchestrator)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        orchestrator.reset()
    }
}

@Composable
fun RequestPermissions(content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val requiredPermissions = remember {
        mutableListOf(Manifest.permission.CAMERA).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.READ_MEDIA_AUDIO)
            } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    var allGranted by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        allGranted = results.values.all { it }
    }

    LaunchedEffect(Unit) {
        if (!allGranted) {
            launcher.launch(requiredPermissions.toTypedArray())
        }
    }

    content()
}

@Composable
fun AppNavigation(orchestrator: TransferOrchestrator) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNavigateToSend = { navController.navigate("send") },
                onNavigateToReceive = { navController.navigate("receive") }
            )
        }
        composable("send") {
            SendScreen(
                orchestrator = orchestrator,
                onBack = { navController.popBackStack() },
                onTransferComplete = { navController.navigate("transfer") }
            )
        }
        composable("receive") {
            ReceiveScreen(
                orchestrator = orchestrator,
                onBack = { navController.popBackStack() },
                onTransferComplete = { navController.navigate("transfer") }
            )
        }
        composable("transfer") {
            TransferScreen(
                orchestrator = orchestrator,
                onDone = { navController.navigate("home") { popUpTo("home") { inclusive = true } } }
            )
        }
    }
}

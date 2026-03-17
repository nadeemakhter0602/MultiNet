package com.multinet

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.multinet.ui.screens.AddDownloadScreen
import com.multinet.ui.screens.DownloadListScreen
import com.multinet.ui.theme.MultinetTheme
import com.multinet.viewmodel.DownloadViewModel

class MainActivity : ComponentActivity() {

    // viewModels() creates the ViewModel scoped to this Activity's lifecycle
    private val viewModel: DownloadViewModel by viewModels()

    // Permission launcher for POST_NOTIFICATIONS (Android 13+)
    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* user accepted or denied — downloads still work either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

        setContent {
            MultinetTheme {
                AppNavigation(viewModel)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

// ── Navigation ────────────────────────────────────────────────────────────────
// Compose Navigation uses string routes, similar to URLs in a web app.
// navController.navigate("add") pushes a new screen onto the back stack.

private object Routes {
    const val LIST = "list"
    const val ADD  = "add"
}

@Composable
private fun AppNavigation(viewModel: DownloadViewModel) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.LIST) {

        composable(Routes.LIST) {
            DownloadListScreen(
                viewModel  = viewModel,
                onAddClick = { navController.navigate(Routes.ADD) }
            )
        }

        composable(Routes.ADD) {
            AddDownloadScreen(
                viewModel = viewModel,
                onBack    = { navController.popBackStack() }
            )
        }
    }
}

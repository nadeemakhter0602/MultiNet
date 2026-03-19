package com.multinet.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.multinet.R
import com.multinet.update.UpdateInfo
import com.multinet.update.UpdateService
import kotlinx.coroutines.launch

private const val CURRENT_VERSION = "1.0.7"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    var checking     by remember { mutableStateOf(false) }
    var updateInfo   by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloading  by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            Image(
                painter            = painterResource(R.mipmap.ic_launcher),
                contentDescription = "MultiNet icon",
                modifier           = Modifier.size(96.dp)
            )

            Text("MultiNet", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "v$CURRENT_VERSION",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text      = "An Android download manager that uses multiple network interfaces simultaneously to maximize download speed.",
                style     = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            // Update available banner
            if (updateInfo != null) {
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("v${updateInfo!!.latestVersion} available",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // GitHub button
                            OutlinedButton(onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW,
                                    Uri.parse(updateInfo!!.releaseUrl)))
                            }) { Text("GitHub") }

                            // Download button
                            if (updateInfo!!.downloadUrl != null) {
                                Button(
                                    onClick = {
                                        downloading = true
                                        downloadProgress = 0
                                        scope.launch {
                                            try {
                                                UpdateService(context).downloadAndInstall(
                                                    updateInfo!!.downloadUrl!!,
                                                    updateInfo!!.latestVersion
                                                ) { progress -> downloadProgress = progress }
                                            } catch (e: Exception) {
                                                snackbarHost.showSnackbar("Download failed: ${e.message}")
                                            } finally {
                                                downloading = false
                                            }
                                        }
                                    },
                                    enabled = !downloading
                                ) {
                                    if (downloading) {
                                        Text("$downloadProgress%")
                                    } else {
                                        Text("Update")
                                    }
                                }
                            }
                        }
                        if (downloading) {
                            LinearProgressIndicator(
                                progress = { downloadProgress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Check for updates button
            Button(
                onClick = {
                    checking = true
                    updateInfo = null
                    scope.launch {
                        try {
                            val result = UpdateService(context).checkForUpdate(CURRENT_VERSION)
                            if (result != null) {
                                updateInfo = result
                            } else {
                                snackbarHost.showSnackbar("Already up to date")
                            }
                        } catch (e: Exception) {
                            snackbarHost.showSnackbar("Could not check for updates")
                        } finally {
                            checking = false
                        }
                    }
                },
                enabled  = !checking && !downloading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Checking…")
                } else {
                    Text("Check for Updates")
                }
            }

            // GitHub link
            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/nadeemakhter0602/MultiNet")))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null,
                    modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("nadeemakhter0602/MultiNet", maxLines = 1)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

package com.multinet.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.multinet.network.NetworkInfo
import com.multinet.viewmodel.DownloadViewModel

private enum class NetworkMode { DEFAULT, MULTIPLE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDownloadScreen(
    viewModel: DownloadViewModel,
    onBack: () -> Unit
) {
    var url              by remember { mutableStateOf("") }
    var fileName         by remember { mutableStateOf("") }
    var urlError         by remember { mutableStateOf(false) }
    var networkMode      by remember { mutableStateOf(NetworkMode.DEFAULT) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var selectedIds      by remember { mutableStateOf(setOf<String>()) }

    val availableNetworks by viewModel.availableNetworks.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Download") },
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value           = url,
                onValueChange   = { url = it; urlError = false },
                label           = { Text("URL") },
                placeholder     = { Text("https://example.com/file.zip") },
                isError         = urlError,
                supportingText  = if (urlError) {{ Text("Please enter a valid URL") }} else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                singleLine      = true,
                modifier        = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value           = fileName,
                onValueChange   = { fileName = it },
                label           = { Text("File name") },
                placeholder     = { Text("e.g. video.mp4") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                singleLine      = true,
                modifier        = Modifier.fillMaxWidth()
            )

            LaunchedEffect(url) {
                if (fileName.isEmpty() && url.isNotEmpty()) {
                    fileName = url.trimEnd('/').substringAfterLast('/').substringBefore('?').ifEmpty { "download" }
                }
            }

            // Network mode dropdown
            ExposedDropdownMenuBox(
                expanded         = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value           = if (networkMode == NetworkMode.DEFAULT) "Default" else "Multiple",
                    onValueChange   = {},
                    readOnly        = true,
                    label           = { Text("Network") },
                    trailingIcon    = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    modifier        = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded         = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    DropdownMenuItem(
                        text    = { Text("Default") },
                        onClick = {
                            networkMode = NetworkMode.DEFAULT
                            selectedIds = emptySet()
                            dropdownExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text    = { Text("Multiple") },
                        onClick = {
                            networkMode = NetworkMode.MULTIPLE
                            dropdownExpanded = false
                            viewModel.refreshNetworks()  // scan on first open
                        }
                    )
                }
            }

            // Network list — shown only when Multiple is selected
            if (networkMode == NetworkMode.MULTIPLE) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Available Networks", style = MaterialTheme.typography.labelMedium)
                    IconButton(onClick = { viewModel.refreshNetworks() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }

                if (availableNetworks.isEmpty()) {
                    Text(
                        "No networks found. Tap refresh to scan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                } else {
                    Card {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            availableNetworks.forEach { network ->
                                NetworkRow(
                                    network   = network,
                                    checked   = network.stableId in selectedIds,
                                    onChecked = { checked ->
                                        selectedIds = if (checked)
                                            selectedIds + network.stableId
                                        else
                                            selectedIds - network.stableId
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    if (url.isBlank() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                        urlError = true
                        return@Button
                    }
                    val name = fileName.ifBlank {
                        url.trimEnd('/').substringAfterLast('/').ifEmpty { "download" }
                    }
                    viewModel.addDownload(url, name)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Download")
            }
        }
    }
}

@Composable
private fun NetworkRow(
    network:   NetworkInfo,
    checked:   Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(network.displayName, style = MaterialTheme.typography.bodyMedium)
            if (network.isMetered) {
                Text(
                    "Metered",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

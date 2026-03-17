package com.multinet.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.multinet.viewmodel.DownloadViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDownloadScreen(
    viewModel: DownloadViewModel,
    onBack: () -> Unit
) {
    var url      by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var urlError by remember { mutableStateOf(false) }

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
            // URL field
            OutlinedTextField(
                value         = url,
                onValueChange = { url = it; urlError = false },
                label         = { Text("URL") },
                placeholder   = { Text("https://example.com/file.zip") },
                isError       = urlError,
                supportingText = if (urlError) {{ Text("Please enter a valid URL") }} else null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction    = ImeAction.Next
                ),
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )

            // File name field — auto-filled from URL, user can override
            OutlinedTextField(
                value         = fileName,
                onValueChange = { fileName = it },
                label         = { Text("File name") },
                placeholder   = { Text("e.g. video.mp4") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )

            // Auto-fill file name when URL loses focus
            LaunchedEffect(url) {
                if (fileName.isEmpty() && url.isNotEmpty()) {
                    // Grab the last path segment of the URL as default file name
                    fileName = url.trimEnd('/').substringAfterLast('/').substringBefore('?')
                        .ifEmpty { "download" }
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

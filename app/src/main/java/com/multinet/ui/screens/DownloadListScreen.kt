package com.multinet.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.multinet.viewmodel.DownloadViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadListScreen(
    viewModel: DownloadViewModel,
    onAddClick: () -> Unit
) {
    val downloads by viewModel.downloads.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("MultiNet") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Default.Add, contentDescription = "Add download")
            }
        }
    ) { padding ->
        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No downloads yet.\nTap + to add one.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(downloads, key = { it.id }) { download ->
                    if (download.isMultiNetwork) {
                        MultiNetworkCard(
                            item     = download,
                            onPause  = { viewModel.pause(download.id) },
                            onResume = { viewModel.resume(download.id) },
                            onCancel = { viewModel.cancel(download.id) },
                            onDelete = { viewModel.delete(download.id) }
                        )
                    } else {
                        DefaultDownloadCard(
                            item     = download,
                            onPause  = { viewModel.pause(download.id) },
                            onResume = { viewModel.resume(download.id) },
                            onCancel = { viewModel.cancel(download.id) },
                            onDelete = { viewModel.delete(download.id) }
                        )
                    }
                }
            }
        }
    }
}

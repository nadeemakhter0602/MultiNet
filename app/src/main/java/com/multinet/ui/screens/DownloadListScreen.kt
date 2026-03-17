package com.multinet.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.multinet.database.DownloadStatus
import com.multinet.engine.toDisplaySize
import com.multinet.engine.toDisplaySpeed
import com.multinet.viewmodel.DownloadUiState
import com.multinet.viewmodel.DownloadViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadListScreen(
    viewModel: DownloadViewModel,
    onAddClick: () -> Unit
) {
    // collectAsState() subscribes to the StateFlow and recomposes when it emits
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
            // Empty state
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
                    DownloadCard(
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

@Composable
private fun DownloadCard(
    item: DownloadUiState,
    onPause:  () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {

            // File name + status chip on same row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text     = item.fileName,
                    style    = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                StatusChip(item.status)
            }

            Spacer(Modifier.height(8.dp))

            // Progress bar
            when {
                // Multi-connection: show segmented bar with per-chunk progress
                item.chunks.isNotEmpty() && item.totalBytes > 0 -> {
                    ChunkedProgressBar(
                        chunks     = item.chunks,
                        totalBytes = item.totalBytes,
                        height     = 12.dp
                    )
                }
                // Single connection with known size: plain progress bar
                item.progress != null -> {
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                // Downloading but size unknown: indeterminate
                item.status == DownloadStatus.DOWNLOADING -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(Modifier.height(4.dp))

            // Size text
            Text(
                text  = buildSizeText(item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            // Speed — only shown while actively downloading
            if (item.status == DownloadStatus.DOWNLOADING && item.speedBps > 0) {
                Text(
                    text  = item.speedBps.toDisplaySpeed(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Error message if failed
            if (item.status == DownloadStatus.FAILED && item.errorMessage != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = item.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(8.dp))

            // Action buttons row
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                when (item.status) {
                    DownloadStatus.DOWNLOADING -> {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause")
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    }
                    DownloadStatus.PAUSED, DownloadStatus.FAILED, DownloadStatus.QUEUED -> {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    }
                    DownloadStatus.COMPLETED -> {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: DownloadStatus) {
    val (label, color) = when (status) {
        DownloadStatus.QUEUED      -> "Queued"      to MaterialTheme.colorScheme.outline
        DownloadStatus.DOWNLOADING -> "Downloading" to MaterialTheme.colorScheme.primary
        DownloadStatus.PAUSED      -> "Paused"      to MaterialTheme.colorScheme.tertiary
        DownloadStatus.COMPLETED   -> "Done"        to MaterialTheme.colorScheme.secondary
        DownloadStatus.FAILED      -> "Failed"      to MaterialTheme.colorScheme.error
    }
    SuggestionChip(
        onClick = {},
        label   = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors  = SuggestionChipDefaults.suggestionChipColors(labelColor = color)
    )
}

private fun buildSizeText(item: DownloadUiState): String {
    val downloaded = item.downloadedBytes.toDisplaySize()
    return if (item.totalBytes > 0) {
        "$downloaded / ${item.totalBytes.toDisplaySize()}  (${item.progressPercent}%)"
    } else {
        downloaded
    }
}

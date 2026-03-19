package com.multinet.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.multinet.database.DownloadStatus
import com.multinet.engine.toDisplaySize
import com.multinet.engine.toDisplaySpeed
import com.multinet.viewmodel.DownloadUiState

@Composable
fun DefaultDownloadCard(
    item:     DownloadUiState,
    onPause:  () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val borderColor = when (item.status) {
        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
        DownloadStatus.COMPLETED   -> MaterialTheme.colorScheme.secondary
        DownloadStatus.FAILED      -> MaterialTheme.colorScheme.error
        DownloadStatus.PAUSED      -> MaterialTheme.colorScheme.tertiary
        DownloadStatus.QUEUED      -> MaterialTheme.colorScheme.outline
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        border   = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // File name + status chip
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
                DownloadStatusChip(item.status)
            }

            Spacer(Modifier.height(8.dp))

            // Progress bar
            when {
                item.progress != null -> LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                item.status == DownloadStatus.DOWNLOADING -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                else -> {}
            }

            // Worker / chunk info — shown when chunks are assigned
            if (item.chunks.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = "${item.workerCount} workers · ${item.minChunkSizeBytes / 1024} KB chunk size · ${item.workerProgress.sumOf { it.chunksComplete }}/${item.chunks.size} chunks done",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                )
            }

            Spacer(Modifier.height(4.dp))

            // Size + progress %
            Text(
                text  = buildDefaultSizeText(item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            // Speed — only while downloading
            if (item.status == DownloadStatus.DOWNLOADING && item.speedBps > 0) {
                Text(
                    text  = item.speedBps.toDisplaySpeed(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Elapsed time
            if (item.activeMs > 0) {
                Text(
                    text  = "Elapsed: ${item.elapsedFormatted}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            // Error message
            if (item.status == DownloadStatus.FAILED && item.errorMessage != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = item.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(8.dp))

            // Action buttons
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

// Shared status chip — will also be used by MultiNetworkCard
@Composable
fun DownloadStatusChip(status: DownloadStatus) {
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

fun buildDefaultSizeText(item: DownloadUiState): String {
    val downloaded = item.downloadedBytes.toDisplaySize()
    return if (item.totalBytes > 0) {
        "$downloaded / ${item.totalBytes.toDisplaySize()}  (${item.progressPercent}%)"
    } else {
        downloaded
    }
}

package com.multinet.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.multinet.database.DownloadStatus
import com.multinet.engine.toDisplaySize
import com.multinet.engine.toDisplaySpeed
import com.multinet.network.toShortStableId
import com.multinet.viewmodel.ChunkUiState
import com.multinet.viewmodel.DownloadUiState
import com.multinet.viewmodel.NetworkProgressState

@Composable
fun MultiNetworkCard(
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

            Spacer(Modifier.height(10.dp))

            if (item.chunks.isNotEmpty() && item.totalBytes > 0) {
                ChunkLabelsRow(item.chunks, item.totalBytes)
                Spacer(Modifier.height(2.dp))
                ChunkedProgressBar(
                    chunks     = item.chunks,
                    totalBytes = item.totalBytes,
                    height     = 12.dp,
                    modifier   = Modifier.fillMaxWidth()
                )
            } else if (item.progress != null) {
                LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
            } else if (item.status == DownloadStatus.DOWNLOADING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(8.dp))

            if (item.networkProgress.size > 1) {
                // 2+ networks: per-network rows + total row
                item.networkProgress.forEach { net ->
                    NetworkSummaryRow(net, item.status)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        buildSizeText(item.downloadedBytes, item.totalBytes, item.progressPercent),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    if (item.status == DownloadStatus.DOWNLOADING && item.speedBps > 0) {
                        Text(item.speedBps.toDisplaySpeed(), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                // 1 network: just size + speed under the bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        buildSizeText(item.downloadedBytes, item.totalBytes, item.progressPercent),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    if (item.status == DownloadStatus.DOWNLOADING && item.speedBps > 0) {
                        Text(item.speedBps.toDisplaySpeed(), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Error message
            if (item.status == DownloadStatus.FAILED && item.errorMessage != null) {
                Spacer(Modifier.height(4.dp))
                Text(item.errorMessage, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(8.dp))

            // Action buttons
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                when (item.status) {
                    DownloadStatus.DOWNLOADING -> {
                        IconButton(onClick = onPause) { Icon(Icons.Default.Pause, "Pause") }
                        IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Cancel") }
                    }
                    DownloadStatus.PAUSED, DownloadStatus.FAILED, DownloadStatus.QUEUED -> {
                        IconButton(onClick = onResume) { Icon(Icons.Default.PlayArrow, "Resume") }
                        IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Cancel") }
                    }
                    DownloadStatus.COMPLETED -> {
                        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Remove") }
                    }
                }
            }
        }
    }
}

// One label per chunk above the total bar, each sized proportionally
@Composable
private fun ChunkLabelsRow(chunks: List<ChunkUiState>, totalBytes: Long) {
    Row(modifier = Modifier.fillMaxWidth()) {
        chunks.sortedBy { it.startByte }.forEach { chunk ->
            val weight = (chunk.endByte - chunk.startByte + 1).toFloat() / totalBytes
            Text(
                text      = chunk.networkDisplayName.toShortStableId(),
                style     = MaterialTheme.typography.labelSmall,
                color     = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis,
                modifier  = Modifier.weight(weight)
            )
        }
    }
}

// One summary row per network: WIFI  42%  •  12.4 / 25.6 MB  •  1.8 MB/s
@Composable
private fun NetworkSummaryRow(net: NetworkProgressState, status: DownloadStatus) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text     = net.displayName.toShortStableId(),
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(56.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text  = buildSizeText(net.downloadedBytes, net.totalBytes, net.progressPercent),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
        )
        if (status == DownloadStatus.DOWNLOADING && net.speedBps > 0) {
            Text(
                text  = net.speedBps.toDisplaySpeed(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun buildSizeText(downloaded: Long, total: Long, percent: Int): String =
    if (total > 0) "${downloaded.toDisplaySize()} / ${total.toDisplaySize()}  ($percent%)"
    else downloaded.toDisplaySize()

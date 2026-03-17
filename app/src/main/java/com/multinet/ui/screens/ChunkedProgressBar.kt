package com.multinet.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.multinet.viewmodel.ChunkUiState

// Draws a segmented progress bar where each chunk occupies a proportional slice.
// Example for 4 equal chunks:
//   [████████░░|███░░░░░░░|██████████|░░░░░░░░░░]
//    chunk 0    chunk 1    chunk 2    chunk 3
@Composable
fun ChunkedProgressBar(
    chunks: List<ChunkUiState>,
    totalBytes: Long,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    dividerWidth: Dp = 2.dp
) {
    val progressColor = MaterialTheme.colorScheme.primary
    val trackColor    = progressColor.copy(alpha = 0.2f)  // dimmed primary for unfilled
    val dividerColor  = MaterialTheme.colorScheme.background

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val barWidth  = size.width
        val barHeight = size.height
        val radius    = CornerRadius(barHeight / 2)
        val divW      = dividerWidth.toPx()

        // Draw the background track
        drawRoundRect(
            color        = trackColor,
            size         = Size(barWidth, barHeight),
            cornerRadius = radius
        )

        // Draw each chunk's filled portion
        chunks.forEach { chunk ->
            if (totalBytes <= 0) return@forEach

            // Where this chunk starts and ends as a fraction of total width
            val segStart = chunk.startByte.toFloat() / totalBytes * barWidth
            val segWidth = (chunk.endByte - chunk.startByte + 1).toFloat() / totalBytes * barWidth

            // How much of this segment is filled
            val fillWidth = chunk.progress * segWidth

            if (fillWidth > 0f) {
                drawRect(
                    color   = progressColor,
                    topLeft = Offset(segStart, 0f),
                    size    = Size(fillWidth, barHeight)
                )
            }
        }

        // Draw dividers between chunks (thin vertical lines)
        // Skip divider before first chunk and after last chunk
        chunks.drop(1).forEach { chunk ->
            if (totalBytes <= 0) return@forEach
            val x = chunk.startByte.toFloat() / totalBytes * barWidth
            drawRect(
                color   = dividerColor,
                topLeft = Offset(x - divW / 2, 0f),
                size    = Size(divW, barHeight)
            )
        }

        // Re-draw rounded corners to clip the filled areas properly
        // (done by drawing the track as a border on top)
        drawRoundRect(
            color        = Color.Transparent,
            size         = Size(barWidth, barHeight),
            cornerRadius = radius
        )
    }
}

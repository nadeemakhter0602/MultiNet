package com.multinet.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Material 3 dark color scheme
private val DarkColors = darkColorScheme(
    primary         = Color(0xFF4FC3F7),  // light blue
    onPrimary       = Color(0xFF003549),
    primaryContainer= Color(0xFF004D6E),
    surface         = Color(0xFF1A1C1E),
    onSurface       = Color(0xFFE2E2E6),
    background      = Color(0xFF111314),
    onBackground    = Color(0xFFE2E2E6),
    error           = Color(0xFFFFB4AB),
)

@Composable
fun MultinetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography  = Typography(),
        content     = content
    )
}

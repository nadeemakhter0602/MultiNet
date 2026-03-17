package com.multinet.database

// The lifecycle states a download can be in.
// Room will store these as strings (via Converters.kt).
enum class DownloadStatus {
    QUEUED,       // waiting to start
    DOWNLOADING,  // actively downloading
    PAUSED,       // user paused it
    COMPLETED,    // finished successfully
    FAILED        // something went wrong
}

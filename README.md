# MultiNet

A modern Android download manager that uses multiple network interfaces simultaneously to maximize download speed.

---

## Features

- **Multi-network downloads** — bind connections to different network interfaces (WiFi, cellular) simultaneously to combine throughput
- **Queue-based worker pool** — configurable workers pull from a shared chunk queue; faster networks naturally process more chunks without manual balancing
- **Automatic network rebalancing** — if a network drops, connections retry on the surviving network; when it returns, work is redistributed automatically
- **Pause / Resume** — per-chunk progress saved continuously; resumes from exact byte offset even after app kill or reboot
- **Default mode** — queue-based chunked download on the system's default network
- **Configurable per download** — set worker count, target chunk count, and minimum chunk size for each download
- **Live progress** — progress bar with per-network speed and chunk stats in real time, elapsed time tracking
- **Foreground service** — downloads continue when the app is in the background
- **Check for updates** — fetches the latest release from GitHub and installs it directly

---

## How It Works

### Default Mode
The file is split into chunks using `max(minChunkSize, totalBytes / targetChunkCount)`. A pool of workers pulls chunks from a shared queue, each writing directly to its byte range in the pre-allocated file. Defaults: 10 workers, 500 target chunks, 256 KB minimum chunk size.

### Multiple Network Mode
You select 2+ network interfaces (e.g. WiFi + Mobile Data). Each network gets its own pool of workers with OkHttp clients bound to that network's socket factory via `CHANGE_NETWORK_STATE`. All workers pull from the same shared queue — faster networks drain more chunks automatically.

If a network drops, its chunks fail and retry on surviving networks. When a network returns, the download automatically restarts with work redistributed across all available networks.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Networking | OkHttp |
| Persistence | Room (SQLite) |
| Concurrency | Coroutines |
| Background | ForegroundService |
| Min SDK | 26 (Android 8.0) |

---

## Permissions

| Permission | Reason |
|-----------|--------|
| `INTERNET` | Download files |
| `ACCESS_NETWORK_STATE` | Detect available networks |
| `CHANGE_NETWORK_STATE` | Bind sockets to specific networks for multi-network mode |
| `FOREGROUND_SERVICE` | Keep downloads running in background |
| `WAKE_LOCK` | Prevent CPU sleep during download |
| `POST_NOTIFICATIONS` | Show download progress notification |
| `REQUEST_INSTALL_PACKAGES` | Install updates downloaded from GitHub |

---

## Building

```bash
git clone https://github.com/nadeemakhter0602/MultiNet
cd MultiNet
./gradlew assembleDebug
```

Install on a connected device:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Download

Get the latest APK from the [Releases](https://github.com/nadeemakhter0602/MultiNet/releases) page, or use the **Check for Updates** button inside the app.

---

## License

MIT

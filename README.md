# MultiNet

A modern Android download manager that uses multiple network interfaces simultaneously to maximize download speed.

---

## Features

- **Multi-network downloads** — bind separate connections to different network interfaces (WiFi, cellular) and download in parallel
- **Automatic network rebalancing** — if a network drops mid-download, connections retry on the surviving network; when the network returns, they are redistributed automatically
- **Parallel connections** — multiple simultaneous HTTP connections per network
- **Pause / Resume** — progress is saved continuously; resume from exact byte offset even after app kill or reboot
- **Default mode** — multi-connection chunked download on the system's default network
- **Live progress** — segmented progress bar with per-network summary rows, speed and size in real time
- **Foreground service** — downloads continue when the app is in the background
- **Check for updates** — fetches the latest release from GitHub and installs it directly

---

## How It Works

### Default Mode
The file is split into equal chunks downloaded in parallel over the system's default network (usually WiFi), each writing directly to its byte range in the pre-allocated file.

### Multiple Network Mode
You select 2+ network interfaces (e.g. WiFi + Mobile Data). Chunks are assigned round-robin across networks with multiple OkHttp connections bound to each network's socket factory via `CHANGE_NETWORK_STATE`.

If a network drops, chunks retry on the remaining networks. When a network returns, the download automatically restarts with all networks redistributed.

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

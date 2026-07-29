# Wireless Webcam Pro

<p align="center">
  <img src="https://img.shields.io/badge/Latency-%3C50ms-brightgreen?style=for-the-badge&logo=android" alt="Sub-50ms Latency">
  <img src="https://img.shields.io/badge/Resolution-1080p%20%7C%2060%20FPS-blue?style=for-the-badge&logo=python" alt="1080p 60FPS">
  <img src="https://img.shields.io/badge/Codec-H.264%20Hardware-orange?style=for-the-badge" alt="H.264 Hardware">
  <img src="https://img.shields.io/badge/OBS-Compatible-purple?style=for-the-badge&logo=obsstudio" alt="OBS Studio">
  <img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" alt="License">
</p>

An open-source, ultra-low latency wireless webcam application that turns your Android phone into a high-quality 1080p camera for **OBS Studio**, **Zoom**, and **Microsoft Teams** over your local Wi-Fi network.

Engineered with zero-copy Android hardware encoding (`MediaCodec` Surface) and PyAV zero-latency decoding context on Windows to achieve **sub-50ms latency** without cloud servers or USB cables.

---

## ✨ Features

- ⚡ **Sub-50ms Ultra-Low Latency**: Designed from the ground up for realtime streaming without queue lag buildup.
- 📱 **Hardware-Accelerated Encoding**: Direct CameraX Surface binding to Android `MediaCodec` H.264 hardware encoder (0 CPU memory copies).
- 🚫 **Zero B-Frames & Continuous Intra-Refresh**: `KEY_MAX_B_FRAMES = 0` eliminates lookahead buffering; continuous macroblock intra-refresh guarantees instant frame recovery if Wi-Fi packets drop.
- 📺 **1080p & 720p at 30 / 60 FPS**: Configurable resolutions and target frame rates.
- 🎚️ **Dynamic Bitrate Adjustment**: On-the-fly bitrate slider (2 to 20 Mbps) via Android `MediaCodec.setParameters()` without tearing down the camera preview.
- 🎥 **Dual OBS Integration Modes**:
  - **Virtual Camera Output**: Native DirectShow virtual camera device (`pyvirtualcam`).
  - **Direct HTTP MJPEG Stream**: Zero-driver HTTP multipart stream at `http://localhost:8000/api/mjpeg`.
- 🎛️ **Web Control Dashboard**: Real-time telemetry monitoring (latency ms, FPS, bitrate Mbps, dropped frame counter) served via FastAPI.
- 📱 **Pro Camera HUD Android UI**: Full-screen unobstructed viewfinder built with Jetpack Compose & Material 3 with a right-edge shutter dock and collapsible settings panel.
- 🔒 **100% LAN Only**: Zero cloud dependencies, zero account logins, no telemetry tracking, and completely offline.

---

## 🏗️ Architecture

```text
+----------------------------------------+
|             Android Phone              |
|  +----------------------------------+  |
|  | CameraX Viewfinder               |  |
|  +----------------------------------+  |
|                   |                    |
|       Zero-Copy GPU Surface            |
|                   v                    |
|  +----------------------------------+  |
|  | MediaCodec (HW H.264 Encoder)    |  |
|  | • Zero B-Frames                  |  |
|  | • Intra-Refresh Macroblocks      |  |
|  +----------------------------------+  |
|                   |                    |
|          Binary Protocol               |
|         (16-byte Header)               |
+-------------------|--------------------+
                    | LAN Wi-Fi (WebSocket)
+-------------------|--------------------+
|             Windows PC                 |
|  +----------------------------------+  |
|  | FastAPI / Uvicorn Server         |  |
|  +----------------------------------+  |
|                   |                    |
|                   v                    |
|  +----------------------------------+  |
|  | PyAV Zero-Latency H.264 Decoder  |  |
|  +----------------------------------+  |
|                   |                    |
|        Drop-Oldest Ring Buffer         |
|                   |                    |
|         +---------+---------+          |
|         |                   |          |
|         v                   v          |
|  +--------------+   +---------------+  |
|  | Virtual Cam  |   | HTTP MJPEG    |  |
|  | (pyvirtualcam|   | Stream        |  |
|  +--------------+   +---------------+  |
+---------|-------------------|----------+
          v                   v
+----------------------------------------+
|               OBS Studio               |
+----------------------------------------+
```

---

## 🚀 Quick Start Guide

### Step 1: Start the Desktop Receiver (Windows)

1. Open PowerShell / Terminal in the `desktop/` directory.
2. Install Python dependencies (Python 3.13+ recommended):
   ```bash
   pip install -r requirements.txt
   ```
   *or using [uv](https://github.com/astral-sh/uv):*
   ```bash
   uv venv
   uv pip install -r requirements.txt
   ```
3. Run the desktop receiver launcher:
   ```bash
   python run_desktop.py
   ```
4. The launcher will output your PC's local Wi-Fi IP address (e.g. `192.168.1.105`) and automatically launch the Web Dashboard at `http://localhost:8000`.

---
### Click here to Download android app.
[Wireless-Webcam](app-debug.apk)

### Step 2: Connect the Android Phone

1. Install and open the **Wireless Webcam** app on your Android phone.
2. Tap the **Wi-Fi Icon** at the top-right of the camera view.
3. Enter your Windows PC's IP address (e.g., `192.168.1.105`).
4. Tap **Save**, then tap the circular **Start Stream** button on the right edge.
5. Your live camera stream will immediately transmit to your PC.

---

### Step 3: Add to OBS Studio

#### Option A: Direct Video Capture Device (Virtual Camera)
1. Open **OBS Studio**.
2. Under **Sources**, click `+` and select **Video Capture Device**.
3. Select **OBS Virtual Camera** (or **Unity Capture**) from the Device dropdown.
4. Set Resolution/FPS Type to **Custom**:
   - Resolution: `1920x1080` (or `1280x720`)
   - FPS: `Match Output FPS` or `60` / `30`
5. Click **OK**.

#### Option B: Browser Source (Direct Local Stream)
1. Open **OBS Studio**.
2. Under **Sources**, click `+` and select **Browser**.
3. Set URL to: `http://localhost:8000/api/mjpeg`
4. Set Width to `1920` and Height to `1080`.
5. Click **OK**.

---

## 📁 Repository Structure

```text
.
├── android/                        # Android Kotlin Application
│   ├── app/src/main/java/com/wirelesswebcam/
│   │   ├── camera/                 # CameraX Pipeline Manager
│   │   ├── encoder/                # MediaCodec H.264 Hardware Encoder
│   │   ├── network/                # Low-Latency WebSocket Streamer
│   │   ├── protocol/               # Binary Wire Framing Protocol
│   │   └── ui/                     # Jetpack Compose & Material 3 HUD Layout
│   └── build.gradle.kts
│
├── desktop/                        # Windows Python Receiver Engine
│   ├── src/
│   │   ├── decoder/                # PyAV Zero-Latency H.264 Decoder
│   │   ├── network/                # FastAPI WebSockets & Control Models
│   │   ├── pipeline/               # Non-Blocking Drop-Oldest Ring Buffer
│   │   ├── service/                # Stream Orchestration & Telemetry Engine
│   │   ├── vcam/                   # pyvirtualcam DirectShow Driver Output
│   │   └── static/                 # Web Dashboard Frontend (HTML5/JS)
│   ├── tests/                      # Automated Verification Test Suites
│   ├── run_desktop.py              # One-Click Desktop Launcher Script
│   └── requirements.txt
│
└── README.md
```

---

## ⚙️ Performance Tuning & Troubleshooting

- **Wi-Fi Band**: Always connect both your PC and Phone to the **5 GHz Wi-Fi band** (or Wi-Fi 6) on your local router.
- **Bitrate Setting**: Default bitrate is set to **5.0 Mbps**. For crowded Wi-Fi networks, use the Web Dashboard slider to set bitrate between **4.0 – 6.0 Mbps** for lag-free streaming.
- **Video Stuck / Frozen**: Tap the **Force Keyframe** button on the Web Dashboard to request an immediate IDR keyframe refresh from the hardware encoder.

---

## 🛠️ Building from Source

### Android App
Requirements: Android Studio / Gradle 8.9+, JDK 17+, Android SDK Platform 35.
```bash
cd android
./gradlew assembleDebug
```
The compiled APK will be generated at:
`android/app/build/outputs/apk/debug/app-debug.apk`

---

## 📜 Acknowledgments

Developed with assistance from Google Antigravity AI pair programmer for architecture prototyping and low-latency pipeline optimization.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).

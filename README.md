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

### Option 1: Standalone Release Pack (Zero Python / Zero Setup) — Recommended

If you just want to use the app without touching code or installing Python:
1. Download **[WirelessWebcam-v1.0-Windows.zip](https://github.com/code-with-ushant/Wireless-Webcam-Pro/releases/download/v1.0.0/WirelessWebcam-v1.0-Windows.zip)** (or view the [v1.0.0 Release](https://github.com/code-with-ushant/Wireless-Webcam-Pro/releases/tag/v1.0.0)).
2. Extract the ZIP anywhere on your PC.
3. Double-click **`WirelessWebcam.exe`**.
   - If Windows Firewall asks for permission, click **Allow**. (Or run `Allow-Firewall.bat` as Administrator).
4. The Web Control Dashboard will automatically pop up in your browser (`http://localhost:8000`).

---

### Option 2: Running from Source / GitHub Clone

If you cloned this repository:
1. Double-click **`Start-Wireless-Webcam.bat`**.
   - If you don't have Python or `uv` installed, the script will automatically bootstrap a lightweight runner in seconds using Windows built-in PowerShell.
2. (Or run `Setup.bat` once to manually create the environment).

---

### Step 2: Install App on Your Android Phone

Choose whichever is easiest for you:
* **In Mobile Browser (Easiest)**: While the PC receiver is running, open Chrome/browser on your phone and visit:  
  `http://<YOUR_PC_IP>:8000/apk`  
  *(Your PC's IP address is displayed directly on the desktop dashboard and terminal window)*. Tap **Install**.
* **Direct Transfer**: Copy `WirelessWebcam.apk` from the release folder to your phone via USB cable, Google Drive, or WhatsApp Web and tap to install.

---

### Step 3: Connect & Stream

1. Connect your Android phone to the **same Wi-Fi network** as your PC.
2. Open the **Wireless Webcam** app on your phone.
3. Tap the **Wi-Fi Icon** in the top-right, enter your PC's IP address, and tap **Save**.
4. Tap the circular **Camera** button (for webcam) or **Cast** button (for screen sharing).

---

### Step 4: Add to OBS Studio

#### Method A: Direct Video Capture Device (Virtual Camera)
1. Open **OBS Studio**.
2. Under **Sources**, click `+` and select **Video Capture Device**.
3. Select **OBS Virtual Camera** (or **Unity Capture**) from the Device dropdown.
4. Set Resolution to `1920x1080` and FPS to `30` or `60`.

#### Method B: Zero-Driver Browser Source
1. Under **Sources** in OBS, click `+` and select **Browser**.
2. Set URL to: `http://localhost:8000/api/mjpeg`
3. Set Width to `1920` and Height to `1080`.

---

## 📁 Repository Structure

```text
.
├── Allow-Firewall.bat              # Optional 1-Click Windows Firewall setup
├── Setup.bat                       # One-time dependency installer (double-click)
├── Start-Wireless-Webcam.bat       # 1-Click desktop server launcher (double-click)
├── WirelessWebcam.apk              # Ready-to-install Android APK
│
├── release/
│   └── WirelessWebcam-v1.0-Windows.zip  # Standalone portable distribution pack
│
├── android/                        # Android Kotlin Application
│   ├── app/src/main/java/com/wirelesswebcam/
│   │   ├── camera/                 # CameraX Pipeline Manager
│   │   ├── encoder/                # MediaCodec H.264 Hardware Encoder
│   │   ├── network/                # Low-Latency WebSocket Streamer
│   │   ├── protocol/               # Binary Wire Framing Protocol
│   │   ├── screen/                 # MediaProjection Screen Capture Service
│   │   └── ui/                     # Jetpack Compose & Material 3 HUD Layout
│   └── build.gradle.kts
│
├── desktop/                        # Windows Python Receiver Engine
│   ├── src/
│   │   ├── decoder/                # PyAV Zero-Latency H.264 Decoder
│   │   ├── network/                # FastAPI WebSockets & Control Models
│   │   ├── pipeline/               # Non-Blocking Drop-Oldest Ring Buffer
│   │   ├── protocol/               # Low-latency protocol framing (Native C & Python)
│   │   ├── service/                # Stream Orchestration & Telemetry Engine
│   │   ├── vcam/                   # pyvirtualcam DirectShow Driver Output
│   │   └── static/                 # Web Dashboard Frontend (HTML5/JS)
│   ├── tests/                      # Automated Verification Test Suites
│   ├── build_release_pack.py       # Automated Standalone Release Packager
│   ├── WirelessWebcam.spec         # PyInstaller Packaging Specification
│   ├── run_desktop.py              # Desktop launcher script
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

### Desktop Receiver & Standalone Pack (Windows)

#### 📦 Download Pre-Built Setup Pack
Download the latest 1-click standalone package:
👉 **[Download WirelessWebcam-v1.0-Windows.zip](https://github.com/code-with-ushant/Wireless-Webcam-Pro/releases/download/v1.0.0/WirelessWebcam-v1.0-Windows.zip)** *(Direct 1-Click Download — Zero Python or drivers required)*  
*(Full Release details on [GitHub Releases](https://github.com/code-with-ushant/Wireless-Webcam-Pro/releases/tag/v1.0.0))*

#### 🔨 Building from Source
Requirements: Python 3.12+ (or [uv](https://astral.sh/uv)).

1. **Install Dependencies**:
   ```bash
   cd desktop
   pip install -r requirements.txt
   pip install pyinstaller
   ```
2. **Build Standalone Executable & Distribution Pack**:
   ```bash
   python build_release_pack.py
   ```
   This automatically compiles `WirelessWebcam.exe`, bundles `WirelessWebcam.apk`, creates `Allow-Firewall.bat`, and outputs the final release package to `release/WirelessWebcam-v1.0-Windows.zip`.

3. **Or Run Directly from Source**:
   ```bash
   python run_desktop.py
   ```
   *(Or simply double-click `Start-Wireless-Webcam.bat` at the repository root)*.

---

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

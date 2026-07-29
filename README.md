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

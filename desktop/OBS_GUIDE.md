# OBS Studio Integration Guide for Wireless Webcam

This guide explains how to connect your low-latency wireless phone camera feed into **OBS Studio** on Windows.

---

## Option 1: DirectShow Virtual Camera (Recommended for Zoom, Teams & OBS)

When the Wireless Webcam server runs on Windows, it automatically registers with your system's **Virtual Camera** driver (via `pyvirtualcam`).

### Setup Steps in OBS Studio:
1. Open **OBS Studio**.
2. Under **Sources**, click the `+` button and select **Video Capture Device**.
3. Name the source (e.g., `Wireless Webcam`).
4. In the **Device** dropdown menu, select **OBS Virtual Camera** (or **Unity Capture**).
5. Set Resolution/FPS Type to **Custom**:
   - Resolution: `1920x1080` (or `1280x720`)
   - FPS: `Match Output FPS` or `60` / `30`
6. Click **OK**. Video will render with sub-50ms latency!

---

## Option 2: Direct Local MJPEG Stream Source (No Driver Required)

If you don't have virtual camera drivers installed, you can stream directly into OBS using our built-in high-speed HTTP MJPEG endpoint.

### Setup Steps in OBS Studio:
1. Open **OBS Studio**.
2. Under **Sources**, click `+` and select **Browser** (or **VLC Video Source**).
3. Name the source (e.g., `Wireless Phone Stream`).
4. Configure properties:
   - **URL**: `http://localhost:8000/api/mjpeg`
   - **Width**: `1920`
   - **Height**: `1080`
5. Uncheck *Shutdown source when not visible* (to prevent stream disconnects).
6. Click **OK**.

---

## Optimization Tips for Zero Latency in OBS:
- **Wi-Fi Network**: Ensure both your Windows PC and Android phone are on the **5 GHz Wi-Fi band** (or Wi-Fi 6) connected to the same local router.
- **Bitrate Tuning**: Use **8–12 Mbps** for 1080p 30/60 FPS over Wi-Fi. If your Wi-Fi experiences interference, lower the bitrate slider to **5–6 Mbps**.
- **Frame Rate**: Match the FPS setting on both the phone and OBS Studio canvas (e.g., 60 FPS for gaming / 30 FPS for desktop streaming).

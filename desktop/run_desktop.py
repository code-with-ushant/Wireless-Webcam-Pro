"""
One-Click Desktop Receiver Launcher.

Displays local IP address, initializes FastAPI server, launches virtual camera output,
and opens the Web Control Dashboard in your browser.
"""

import multiprocessing
import os
import socket
import sys
import webbrowser
from pathlib import Path

# Add src to python path (supports both normal run and PyInstaller bundle)
if getattr(sys, "frozen", False) and hasattr(sys, "_MEIPASS"):
    src_path = Path(sys._MEIPASS) / "src"
    if src_path.exists():
        sys.path.insert(0, str(src_path))
    sys.path.insert(0, sys._MEIPASS)
else:
    src_path = Path(__file__).resolve().parent / "src"
    sys.path.insert(0, str(src_path))

import uvicorn
from main import app


def get_local_ip() -> str:
    """Finds the primary local IPv4 address of this machine on the LAN Wi-Fi network."""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        # Does not send packets, just determines local route IP
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


def main():
    multiprocessing.freeze_support()

    local_ip = get_local_ip()
    port = 8000
    dashboard_url = f"http://localhost:{port}"

    print("=" * 70)
    print("      WIRELESS WEBCAM RECEIVER FOR OBS STUDIO (SUB-50MS LATENCY)     ")
    print("=" * 70)
    print(f" [1] Server Host IP (Enter this in your Android Phone App):")
    print(f"     -> http://{local_ip}:{port}")
    print(f" [2] Desktop Dashboard UI:")
    print(f"     -> {dashboard_url}")
    print(f" [3] OBS Studio Direct MJPEG Stream Source URL:")
    print(f"     -> http://localhost:{port}/api/mjpeg")
    print(f" [4] Direct APK Download on Phone (Open in Phone Browser):")
    print(f"     -> http://{local_ip}:{port}/apk")
    print("=" * 70)
    print("Starting uvicorn server... Press Ctrl+C to stop.\n")

    # Automatically open Web Dashboard in default browser
    try:
        webbrowser.open(dashboard_url)
    except Exception:
        pass

    # Launch Uvicorn server with the direct app instance
    uvicorn.run(app, host="0.0.0.0", port=port, log_level="info")


if __name__ == "__main__":
    main()

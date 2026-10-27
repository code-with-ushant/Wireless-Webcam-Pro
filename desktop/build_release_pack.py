"""
Automated Build & Packaging Tool for Wireless Webcam Pro (Windows).

Builds the standalone executable, bundles the Android APK, adds firewall helpers,
and creates the final ready-to-distribute release ZIP archive.
"""

import os
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path


def main():
    print("=" * 70)
    print("   WIRELESS WEBCAM PRO - AUTOMATED RELEASE PACKAGER")
    print("=" * 70)

    desktop_dir = Path(__file__).resolve().parent
    project_root = desktop_dir.parent
    spec_file = desktop_dir / "WirelessWebcam.spec"
    dist_dir = desktop_dir / "dist"
    app_dist_dir = dist_dir / "WirelessWebcam"
    release_dir = project_root / "release"
    release_dir.mkdir(parents=True, exist_ok=True)

    # 1. Clean previous builds
    print("\n[1/5] Cleaning previous build artifacts...")
    for folder in [desktop_dir / "build", desktop_dir / "dist"]:
        if folder.exists():
            shutil.rmtree(folder, ignore_errors=True)

    # 2. Run PyInstaller
    print("\n[2/5] Compiling standalone executable with PyInstaller...")
    pyinstaller_cmd = [
        sys.executable,
        "-m",
        "PyInstaller",
        "--noconfirm",
        "--clean",
        str(spec_file),
    ]
    print(f"Running: {' '.join(pyinstaller_cmd)}")
    result = subprocess.run(pyinstaller_cmd, cwd=str(desktop_dir))
    if result.returncode != 0:
        print("\n[ERROR] PyInstaller compilation failed!")
        sys.exit(result.returncode)

    exe_path = app_dist_dir / "WirelessWebcam.exe"
    if not exe_path.exists():
        print(f"\n[ERROR] Expected executable not found at: {exe_path}")
        sys.exit(1)
    print(f"[OK] Standalone executable compiled: {exe_path}")

    # 3. Bundle Android APK
    print("\n[3/5] Bundling Android APK into release folder...")
    apk_candidates = [
        project_root / "WirelessWebcam.apk",
        project_root / "android" / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk",
    ]
    apk_bundled = False
    for candidate in apk_candidates:
        if candidate.exists():
            dest_apk = app_dist_dir / "WirelessWebcam.apk"
            shutil.copy2(candidate, dest_apk)
            print(f"[OK] Copied {candidate.name} -> {dest_apk}")
            apk_bundled = True
            break

    if not apk_bundled:
        print("[WARNING] WirelessWebcam.apk not found to bundle! You can add it later.")

    # 4. Generate Allow-Firewall.bat and QuickStart Guide
    print("\n[4/5] Generating helper tools and documentation...")
    firewall_bat = app_dist_dir / "Allow-Firewall.bat"
    firewall_bat_content = """@echo off
title Wireless Webcam - Windows Firewall Setup
echo ============================================================
echo   WIRELESS WEBCAM PRO - FIREWALL CONFIGURATION
echo ============================================================
echo.
echo This utility configures Windows Firewall to allow your
echo Android phone to connect to this PC over your Wi-Fi network.
echo.
echo Requesting Administrator privileges...

net session >nul 2>&1
if %errorlevel% neq 0 (
    powershell -Command "Start-Process cmd -ArgumentList '/c netsh advfirewall firewall add rule name=\"Wireless Webcam Receiver (TCP-8000)\" dir=in action=allow protocol=TCP localport=8000 & netsh advfirewall firewall add rule name=\"Wireless Webcam Receiver (UDP-8000)\" dir=in action=allow protocol=UDP localport=8000 & echo. & echo [OK] Firewall configured successfully! & echo You can close this window. & pause' -Verb RunAs"
    exit /b
)

netsh advfirewall firewall add rule name="Wireless Webcam Receiver (TCP-8000)" dir=in action=allow protocol=TCP localport=8000 >nul 2>&1
netsh advfirewall firewall add rule name="Wireless Webcam Receiver (UDP-8000)" dir=in action=allow protocol=UDP localport=8000 >nul 2>&1
echo [OK] Windows Firewall rules configured successfully!
echo.
pause
"""
    firewall_bat.write_text(firewall_bat_content, encoding="utf-8")

    # Quick Start Guide
    quickstart_file = app_dist_dir / "QuickStart-Guide.txt"
    quickstart_content = """======================================================================
         WIRELESS WEBCAM PRO - 3-STEP QUICK START GUIDE
======================================================================

No Python, no drivers, and no terminal commands required!

----------------------------------------------------------------------
STEP 1: LAUNCH THE RECEIVER ON YOUR PC
----------------------------------------------------------------------
1. Double-click: WirelessWebcam.exe
2. If Windows Defender Firewall asks to allow access, click "Allow access".
   (If your phone ever has trouble connecting, run Allow-Firewall.bat as Admin)
3. Your web browser will automatically open with the Control Dashboard:
   http://localhost:8000

----------------------------------------------------------------------
STEP 2: INSTALL THE APP ON YOUR ANDROID PHONE
----------------------------------------------------------------------
Choose ANY of these easy methods:
* Method A (Easiest - via Browser):
  Open your phone's browser and type:
  http://<PC_IP>:8000/apk
  (Your PC's IP is shown on the command window and dashboard)
  Then tap Install.

* Method B (Direct Transfer):
  Copy "WirelessWebcam.apk" from this folder to your phone via USB,
  Google Drive, or WhatsApp, and tap to install.

----------------------------------------------------------------------
STEP 3: CONNECT & STREAM TO OBS STUDIO
----------------------------------------------------------------------
1. Make sure your phone and PC are connected to the SAME Wi-Fi network.
2. In the Android App, enter your PC's IP address (shown on screen) and tap Connect.
3. IN OBS STUDIO:
   - Add a new "Browser" source.
   - Set URL to: http://localhost:8000/api/mjpeg
   - Set Width: 1920, Height: 1080, FPS: 30
   OR
   - Add a "Video Capture Device" and select "OBS Virtual Camera".

That's it! Ultra-low latency camera & screen sharing is live!
======================================================================
"""
    quickstart_file.write_text(quickstart_content, encoding="utf-8")

    # Launcher batch file in the app directory for convenience
    launch_bat = app_dist_dir / "Launch.bat"
    launch_bat.write_text(
        '@echo off\ncd /d "%~dp0"\nstart "" "WirelessWebcam.exe"\n',
        encoding="utf-8"
    )

    # 5. Create ZIP distribution archive
    print("\n[5/5] Creating release distribution ZIP archive...")
    zip_path = release_dir / "WirelessWebcam-v1.0-Windows.zip"
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for file in app_dist_dir.rglob("*"):
            if file.is_file():
                rel_path = file.relative_to(app_dist_dir)
                zf.write(file, arcname=f"WirelessWebcam/{rel_path}")

    zip_size_mb = zip_path.stat().st_size / (1024 * 1024)
    print(f"\n" + "=" * 70)
    print("   RELEASE PACKAGING COMPLETE!")
    print("=" * 70)
    print(f" [Distribution Folder]: {app_dist_dir}")
    print(f" [Executable File]    : {exe_path}")
    print(f" [Release ZIP Archive]: {zip_path} ({zip_size_mb:.1f} MB)")
    print("=" * 70)


if __name__ == "__main__":
    main()

"""
PyInstaller Executable Packager Script.

Compiles the desktop receiver into a standalone executable (WirelessWebcamReceiver.exe)
that users can launch with a single double-click.
"""

import os
import shutil
import sys
from pathlib import Path

import PyInstaller.__main__

desktop_dir = Path(__file__).resolve().parent
src_dir = desktop_dir / "src"
static_dir = src_dir / "static"

print("=" * 70)
print("Building Standalone Executable for Wireless Webcam Receiver...")
print("=" * 70)

# PyInstaller Arguments
args = [
    str(desktop_dir / "run_desktop.py"),
    "--name=WirelessWebcamReceiver",
    "--onedir",
    "--console",
    "--noconfirm",
    "--clean",
    f"--add-data={static_dir};static",
    f"--paths={src_dir}",
]

# Run PyInstaller compiler
PyInstaller.__main__.run(args)

print("=" * 70)
print("BUILD COMPLETE!")
print(f"Executable output path: {desktop_dir / 'dist' / 'WirelessWebcamReceiver' / 'WirelessWebcamReceiver.exe'}")
print("=" * 70)

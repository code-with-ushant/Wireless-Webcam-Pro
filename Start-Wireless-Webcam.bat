@echo off
title Wireless Webcam Pro - Receiver
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo.
echo   ===========================================================
echo   #   W I R E L E S S   W E B C A M   P R O                #
echo   #   Ultra-Low Latency Camera for OBS Studio               #
echo   ===========================================================
echo.

:: ---------------------------------------------------------------
:: Priority 1: Use compiled Standalone Executable (Zero Dependencies)
:: ---------------------------------------------------------------
if exist "desktop\dist\WirelessWebcam\WirelessWebcam.exe" (
    echo   [OK] Found Standalone Executable
    echo   [..] Starting Wireless Webcam Receiver...
    echo.
    cd desktop\dist\WirelessWebcam
    WirelessWebcam.exe
    goto :exit
)

cd desktop

:: ---------------------------------------------------------------
:: Priority 2: Use existing virtual environment (.venv)
:: ---------------------------------------------------------------
if exist ".venv\Scripts\python.exe" (
    echo   [OK] Found Python virtual environment
    echo   [..] Starting receiver server...
    echo.
    ".venv\Scripts\python.exe" run_desktop.py
    goto :exit
)

:: ---------------------------------------------------------------
:: Priority 3: Use uv package manager if present
:: ---------------------------------------------------------------
where uv >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo   [OK] Found uv package manager
    echo   [..] Starting receiver server with uv...
    echo.
    uv run python run_desktop.py
    goto :exit
)

if exist "%USERPROFILE%\.local\bin\uv.exe" (
    echo   [OK] Found uv at %USERPROFILE%\.local\bin\uv.exe
    echo   [..] Starting receiver server with uv...
    echo.
    "%USERPROFILE%\.local\bin\uv.exe" run python run_desktop.py
    goto :exit
)

:: ---------------------------------------------------------------
:: Priority 4: Use system Python directly
:: ---------------------------------------------------------------
where python >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo   [OK] Found system Python
    echo   [..] Starting receiver server...
    echo.
    python run_desktop.py
    goto :exit
)

:: ---------------------------------------------------------------
:: Priority 5: Auto-Bootstrap using Windows PowerShell
:: ---------------------------------------------------------------
echo.
echo   [!] No Python environment detected on this machine.
echo   [..] Automatically bootstrapping lightweight package runner (uv)...
echo.
powershell -NoProfile -ExecutionPolicy Bypass -Command "irm https://astral.sh/uv/install.ps1 | iex"

if exist "%USERPROFILE%\.local\bin\uv.exe" (
    echo.
    echo   [OK] Successfully bootstrapped uv!
    echo   [..] Setting up Python dependencies (one-time setup)...
    echo.
    cd /d "%~dp0"
    call Setup.bat
    cd desktop
    "%USERPROFILE%\.local\bin\uv.exe" run python run_desktop.py
    goto :exit
)

:: ---------------------------------------------------------------
:: Fallback Error
:: ---------------------------------------------------------------
echo.
echo   [ERROR] Could not start Wireless Webcam Receiver.
echo.
echo   Please run Setup.bat to prepare your environment,
echo   or install Python 3.12+ from: https://www.python.org/downloads/
echo.

:exit
echo.
echo   Press any key to close...
pause >nul

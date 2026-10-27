@echo off
title Wireless Webcam Pro - First Time Setup
setlocal enabledelayedexpansion
cd /d "%~dp0desktop"

echo ============================================================
echo       WIRELESS WEBCAM PRO - ONE TIME SETUP
echo ============================================================
echo.
echo This will install all dependencies and prepare the desktop
echo receiver for use. You only need to run this ONCE.
echo.
echo ============================================================

:: Step 1: Check if uv is available
where uv >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo [OK] Found uv package manager
    set UV_CMD=uv
    goto :install_with_uv
)

:: Check user-local uv path
if exist "%USERPROFILE%\.local\bin\uv.exe" (
    echo [OK] Found uv at %USERPROFILE%\.local\bin\uv.exe
    set UV_CMD="%USERPROFILE%\.local\bin\uv.exe"
    goto :install_with_uv
)

:: Step 2: Check if system Python is available
where python >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo [OK] Found system Python
    goto :install_with_pip
)

:: Step 3: Neither uv nor Python found - Auto-Bootstrap uv via PowerShell
echo [INFO] No Python installation found on this machine.
echo [..] Automatically downloading lightweight package runner (uv)...
echo.
powershell -NoProfile -ExecutionPolicy Bypass -Command "irm https://astral.sh/uv/install.ps1 | iex"

if exist "%USERPROFILE%\.local\bin\uv.exe" (
    echo.
    echo [OK] Successfully installed uv!
    set UV_CMD="%USERPROFILE%\.local\bin\uv.exe"
    goto :install_with_uv
)

echo.
echo [ERROR] Automatic setup could not proceed.
echo Please install Python 3.12+ from https://python.org and rerun Setup.bat.
pause
exit /b 1

:install_with_uv
echo.
echo [STEP 1/2] Creating Python virtual environment...
%UV_CMD% venv .venv
echo.
echo [STEP 2/2] Installing dependencies...
%UV_CMD% pip install -r requirements.txt
goto :done

:install_with_pip
echo.
echo [STEP 1/2] Creating Python virtual environment...
python -m venv .venv
echo.
echo [STEP 2/2] Installing dependencies...
".venv\Scripts\pip.exe" install -r requirements.txt
goto :done

:done
echo.
echo ============================================================
echo       SETUP COMPLETE!
echo ============================================================
echo.
echo You can now launch the receiver by double-clicking:
echo     Start-Wireless-Webcam.bat
echo.
echo ============================================================
pause

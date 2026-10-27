@echo off
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

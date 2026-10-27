@echo off
title Push Wireless Webcam Pro to GitHub
cd /d "%~dp0"

echo ============================================================
echo   PUSHING WIRELESS WEBCAM PRO TO GITHUB
echo   Repo: https://github.com/code-with-ushant/Wireless-Webcam-Pro
echo ============================================================
echo.

git push -u origin main --force

echo.
echo ============================================================
pause

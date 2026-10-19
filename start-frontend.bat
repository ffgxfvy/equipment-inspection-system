@echo off
REM ============================================================
REM  Start frontend (Vue + Vite). Just double-click this file.
REM  Make sure the backend is already running.
REM ============================================================
chcp 65001 >nul
cd /d "%~dp0"
echo Starting frontend ... please wait.
echo.
powershell -NoProfile -ExecutionPolicy Bypass -NoExit -File "%~dp0start-frontend.ps1"

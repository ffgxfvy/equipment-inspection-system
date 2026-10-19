@echo off
REM ============================================================
REM  Start backend (SpringBoot). Just double-click this file.
REM
REM  Why a .bat wrapper instead of running the .ps1 directly:
REM    1. -ExecutionPolicy Bypass : never blocked by script policy
REM    2. -NoExit                 : window stays open after exit
REM    3. chcp 65001              : UTF-8 so Chinese logs render
REM ============================================================
chcp 65001 >nul
cd /d "%~dp0"
echo Starting backend ... please wait.
echo.
powershell -NoProfile -ExecutionPolicy Bypass -NoExit -File "%~dp0start-backend.ps1"

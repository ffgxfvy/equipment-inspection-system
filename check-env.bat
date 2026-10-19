@echo off
REM ============================================================
REM  Check whether Java / Maven / Node are ready.
REM  Just double-click this file.
REM ============================================================
chcp 65001 >nul
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -NoExit -File "%~dp0check-env.ps1"

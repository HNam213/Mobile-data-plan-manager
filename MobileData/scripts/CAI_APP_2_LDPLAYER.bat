@echo off
setlocal
chcp 65001 >nul
PowerShell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0CAI_APP_2_LDPLAYER.ps1"
set "CODE=%ERRORLEVEL%"
echo.
pause
exit /b %CODE%

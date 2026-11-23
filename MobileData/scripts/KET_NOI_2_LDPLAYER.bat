@echo off
setlocal
chcp 65001 >nul
PowerShell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0KET_NOI_2_LDPLAYER.ps1"
set "CODE=%ERRORLEVEL%"
echo.
pause
exit /b %CODE%

@echo off
setlocal
cd /d "%~dp0..\mobiledata-api" || (echo Khong tim thay thu muc API. & pause & exit /b 1)
set "ASPNETCORE_ENVIRONMENT=Development"
echo MobileData API (cuc bo) - http://127.0.0.1:5080
dotnet run --project MobileData.Api.csproj --urls "http://127.0.0.1:5080"
if errorlevel 1 (echo API loi. Kiem tra .NET 10 va SQL Server. & pause)

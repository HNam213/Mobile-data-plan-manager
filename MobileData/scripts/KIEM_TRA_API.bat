@echo off
setlocal
echo Kiem tra Web API va SQL Server:
curl -i --max-time 6 "http://127.0.0.1:5080/api/health"
echo.
echo Neu HTTP 200 + status ok => API va SQL Server da ket noi.
pause

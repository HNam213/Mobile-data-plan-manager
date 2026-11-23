$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\LDPLAYER_COMMON.ps1"
Write-Host '===== MOBILEDATA: KET NOI LDPLAYER -> WEB API =====' -ForegroundColor Cyan
try {
    $adb = Find-MobileDataAdb
    Write-Host "ADB: $adb"
    $apiReady = Test-MobileDataApi
    $devices = @(Get-MobileDataEmulators $adb)
    if ($devices.Count -eq 0) {
        Write-Host 'Khong thay LDPlayer nao. Mo LDPlayer Multi-instance va bat ADB debugging: Open local connection.' -ForegroundColor Red
        & $adb devices
        exit 2
    }
    Write-Host "Da tim thay $($devices.Count) may ao: $($devices -join ', ')"
    if ($devices.Count -lt 2) {
        Write-Host 'CHU Y: Moi phat hien 1 may ao. De demo 2 tai khoan, mo ca hai LDPlayer truoc khi chay.' -ForegroundColor Yellow
    }
    $ok = 0
    foreach ($serial in $devices) {
        Write-Host "`n[$serial] Dang chuyen cong 5080..."
        $output = & $adb -s $serial reverse tcp:5080 tcp:5080 2>&1
        if ($LASTEXITCODE -ne 0) { Write-Host "LOI [$serial]: $output" -ForegroundColor Red; continue }
        $routes = (& $adb -s $serial reverse --list 2>&1) -join "`n"
        if ($LASTEXITCODE -eq 0 -and $routes.Contains('tcp:5080 tcp:5080')) {
            Write-Host "OK [$serial]: tcp:5080 -> tcp:5080" -ForegroundColor Green
            $ok++
        } else { Write-Host "Khong xac nhan duoc reverse [$serial]: $routes" -ForegroundColor Yellow }
    }
    Write-Host "`nDa ket noi $ok/$($devices.Count) may ao."
    if (-not $apiReady) { Write-Host 'Can khoi dong API truoc khi dang nhap.' -ForegroundColor Yellow }
    Write-Host 'Mo MobileData tren tung LDPlayer, dang nhap 2 so dien thoai KHAC NHAU.'
    if ($ok -ne $devices.Count) { exit 3 }
    exit 0
} catch { Write-Host "LOI: $_" -ForegroundColor Red; exit 1 }

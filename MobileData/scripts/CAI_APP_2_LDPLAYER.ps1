$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\LDPLAYER_COMMON.ps1"
Write-Host '===== MOBILEDATA: CAI APP LEN CAC LDPLAYER =====' -ForegroundColor Cyan
try {
    $adb = Find-MobileDataAdb
    $apk = Join-Path $PSScriptRoot '..\android-app\app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) {
        Write-Host 'Khong tim thay APK debug.' -ForegroundColor Yellow
        Write-Host 'Mo Android Studio -> Build -> Build APK(s), sau do chay lai script nay.'
        exit 2
    }
    $devices = @(Get-MobileDataEmulators $adb)
    if ($devices.Count -eq 0) { Write-Host 'Khong thay LDPlayer nao trong ADB devices.' -ForegroundColor Red; exit 3 }
    $ok = 0
    foreach ($serial in $devices) {
        Write-Host "[$serial] Cai app-debug.apk..."
        $result = & $adb -s $serial install -r $apk 2>&1
        if ($LASTEXITCODE -eq 0 -and (($result -join "`n") -match 'Success')) {
            Write-Host "OK: $serial" -ForegroundColor Green; $ok++
        } else { Write-Host "LOI: $result" -ForegroundColor Red }
    }
    Write-Host "Da cai tren $ok/$($devices.Count) may ao."
    if ($ok -ne $devices.Count) { exit 4 }
    exit 0
} catch { Write-Host "LOI: $_" -ForegroundColor Red; exit 1 }

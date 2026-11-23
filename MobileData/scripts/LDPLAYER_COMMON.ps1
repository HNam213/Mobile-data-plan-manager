# Helper chung cho PowerShell 5.1, chon ADB tu Android SDK.
function Find-MobileDataAdb {
    $candidates = @()
    if ($env:ANDROID_SDK_ROOT) { $candidates += (Join-Path $env:ANDROID_SDK_ROOT 'platform-tools\adb.exe') }
    if ($env:ANDROID_HOME) { $candidates += (Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe') }
    $candidates += (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe')
    foreach ($c in $candidates) { if ($c -and (Test-Path -LiteralPath $c -PathType Leaf)) { return $c } }
    $cmd = Get-Command adb.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw 'Khong tim thay adb.exe. Kiem tra Android SDK Platform-Tools trong Android Studio.'
}
function Get-MobileDataEmulators([string] $adb) {
    $list = & $adb devices 2>&1
    if ($LASTEXITCODE -ne 0) { throw "ADB bi loi: $list" }
    $serials = @()
    foreach ($line in $list) {
        if ($line -match '^\s*(\S+)\s+device\s*$') {
            $id = $Matches[1]
            if ($id -match '^(emulator-\d+|127\.0\.0\.1:\d+|localhost:\d+)$') { $serials += $id }
        }
    }
    $serials | Select-Object -Unique
}
function Test-MobileDataApi {
    try {
        $res = Invoke-WebRequest -Uri 'http://127.0.0.1:5080/api/carriers' -UseBasicParsing -TimeoutSec 5
        Write-Host "API may tinh: HTTP $($res.StatusCode) - OK" -ForegroundColor Green
        return $true
    } catch {
        $status = $null
        try { $status = [int]$_.Exception.Response.StatusCode } catch { }
        if ($status) {
            Write-Host "API may tinh da phan hoi HTTP $status (kiem tra endpoint)." -ForegroundColor Yellow
            return $true
        }
        Write-Host 'CANH BAO: API chua mo tren may tinh port 5080. Hay khoi dong Web API truoc.' -ForegroundColor Yellow
        return $false
    }
}

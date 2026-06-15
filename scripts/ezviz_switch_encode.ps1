param(
    [string]$DeviceSerial = "BK4225491",
    [ValidateSet("H264", "H265")]
    [string]$EncodeType = "H264",
    [ValidateSet(1, 2)]
    [int]$StreamType = 1,
    [string]$LocalIndex = "1",
    [string]$EnvFile = (Join-Path (Split-Path $PSScriptRoot -Parent) ".env")
)

$ErrorActionPreference = "Stop"

# Read .env config
if (-not (Test-Path -LiteralPath $EnvFile)) {
    throw "Env file not found: $EnvFile"
}

$vars = @{}
Get-Content -Encoding UTF8 $EnvFile | ForEach-Object {
    $line = [string]$_
    if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith("#")) {
        return
    }

    $idx = $line.IndexOf("=")
    if ($idx -gt 0) {
        $key = $line.Substring(0, $idx).Trim()
        $value = $line.Substring($idx + 1).Trim()
        $value = $value.Trim('"', "'")
        $vars[$key] = $value
    }
}

$appKey = $vars["EZVIZ_APP_KEY"]
$appSecret = $vars["EZVIZ_APP_SECRET"]
if ([string]::IsNullOrWhiteSpace($appKey) -or [string]::IsNullOrWhiteSpace($appSecret)) {
    throw "EZVIZ_APP_KEY/EZVIZ_APP_SECRET is empty in $EnvFile"
}

# 1. Get accessToken
$tokenParams = @{
    Method      = "Post"
    Uri         = "https://open.ys7.com/api/lapp/token/get"
    ContentType = "application/x-www-form-urlencoded"
    Body        = @{
        appKey    = $appKey
        appSecret = $appSecret
    }
}

$tokenResp = Invoke-RestMethod @tokenParams

$tokenCode = ""
if ($null -ne $tokenResp.meta) {
    $tokenCode = [string]$tokenResp.meta.code
} elseif ($null -ne $tokenResp.code) {
    $tokenCode = [string]$tokenResp.code
}

if ($tokenCode -ne "200") {
    throw "Get accessToken failed: $($tokenResp | ConvertTo-Json -Compress -Depth 8)"
}

$accessToken = $tokenResp.data.accessToken

# 2. Switch encode type (PUT, auth params in Headers, encode params in Body)
$switchParams = @{
    Method      = "Put"
    Uri         = "https://open.ys7.com/api/v3/device/video/encodeType"
    ContentType = "application/x-www-form-urlencoded"
    Headers     = @{
        accessToken  = $accessToken
        deviceSerial = $DeviceSerial
        localIndex   = $LocalIndex
    }
    Body        = @{
        encodeType   = $EncodeType
        streamType   = $StreamType
    }
}

try {
    $resp = Invoke-RestMethod @switchParams
    $respCode = ""
    if ($null -ne $resp.meta) {
        $respCode = [string]$resp.meta.code
    } elseif ($null -ne $resp.code) {
        $respCode = [string]$resp.code
    }
    if ($respCode -ne "200") {
        throw "Switch encode type failed: $($resp | ConvertTo-Json -Compress -Depth 8)"
    }
    $resp | ConvertTo-Json -Depth 8
} catch {
    # Extract error response body from ErrorDetails (Invoke-RestMethod puts HTTP error body here)
    $errCode = ""
    $errMsg = ""
    if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
        try {
            $errResp = $_.ErrorDetails.Message | ConvertFrom-Json
            $errCode = if ($errResp.meta) { [string]$errResp.meta.code } elseif ($errResp.code) { [string]$errResp.code } else { "" }
            $errMsg = if ($errResp.meta) { $errResp.meta.message } elseif ($errResp.msg) { $errResp.msg } else { "" }
        } catch {
            $errMsg = $_.ErrorDetails.Message
        }
    }
    if (-not $errMsg) { $errMsg = $_.Exception.Message }

    $knownErrors = @{
        "60020" = "Device does not support this command (capability limitation, try EZVIZ Studio or device web console instead)"
        "20007" = "Device is offline"
        "20011" = "Device does not support or device is abnormal"
        "20002" = "Device not found"
        "20018" = "Current user does not own this device"
        "10002" = "accessToken expired or invalid"
        "10001" = "Parameter error"
        "20008" = "Device response timeout"
    }
    $hint = $knownErrors[$errCode]
    if ($hint) {
        throw "Switch encode type failed: code=$errCode, message=$errMsg -- $hint"
    } else {
        throw "Switch encode type failed: code=$errCode, message=$errMsg"
    }
}

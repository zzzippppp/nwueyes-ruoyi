$ErrorActionPreference = "Stop"

$toolDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$exe = Join-Path $toolDir "go2rtc.exe"
$config = Join-Path $toolDir "go2rtc.yaml"

if (-not (Test-Path $exe)) {
    throw "go2rtc.exe 不存在: $exe"
}

& $exe -config $config

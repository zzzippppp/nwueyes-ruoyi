<#
Exports the current PostgreSQL database and persistent media folders, then uploads
them to the Ubuntu server. Run in PowerShell on the source machine.

Before running: stop the Java application and any active recognition worker so
the database and media files are a consistent snapshot.

Example:
  $env:PGPASSWORD = 'your-postgres-password'
  .\migrate-from-windows.ps1 -ServerHost 192.168.1.20 -ServerUser deploy
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory)] [string] $ServerHost,
  [Parameter(Mandatory)] [string] $ServerUser,
  [string] $Database = 'nwueyes',
  [string] $DatabaseUser = 'postgres',
  [string] $DataRoot = 'E:\nwueyes',
  [string] $OutputDirectory = (Join-Path $PSScriptRoot 'migration-output')
)

$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$databaseDump = Join-Path $OutputDirectory "nwueyes-$timestamp.dump"
$mediaArchive = Join-Path $OutputDirectory "nwueyes-media-$timestamp.tar.gz"

if (-not (Get-Command pg_dump -ErrorAction SilentlyContinue)) {
  throw 'pg_dump is not in PATH. Install PostgreSQL client tools or add their bin directory to PATH.'
}

& pg_dump -Fc -U $DatabaseUser -d $Database -f $databaseDump
if ($LASTEXITCODE -ne 0) { throw 'Database export failed.' }

$mediaFolders = @(
  'uploadPath',
  'log_library',
  'face_library',
  'body_library',
  'snapshot_library',
  'capture_manifest'
) | ForEach-Object { Join-Path $DataRoot $_ } | Where-Object { Test-Path $_ }

if ($mediaFolders.Count -eq 0) {
  throw "No persistent media folders found below $DataRoot."
}

Push-Location $DataRoot
try {
  $relativeFolders = $mediaFolders | ForEach-Object { Split-Path $_ -Leaf }
  & tar.exe -czf $mediaArchive @relativeFolders
  if ($LASTEXITCODE -ne 0) { throw 'Media archive creation failed.' }
}
finally {
  Pop-Location
}

$remoteDirectory = "/tmp/nwueyes-migration/$timestamp"
& ssh "$ServerUser@$ServerHost" "mkdir -p $remoteDirectory"
& scp $databaseDump $mediaArchive "$ServerUser@$ServerHost`:$remoteDirectory/"
if ($LASTEXITCODE -ne 0) { throw 'Upload to server failed.' }

Write-Host "Uploaded migration files to $remoteDirectory"
Write-Host "On the server run:"
Write-Host "  sudo bash /opt/nwueyes/ruoyi/deploy/ubuntu/restore-on-ubuntu.sh --input $remoteDirectory --replace"

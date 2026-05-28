# 本地启动若依后端（使用 application-druid.yml 中的 nwueyes，勿覆盖数据源）
$ErrorActionPreference = "Stop"
$ruoyiRoot = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $ruoyiRoot "ruoyi-admin\target\ruoyi-admin.jar"
if (-not (Test-Path $jar)) {
    Write-Host "未找到 jar，正在编译..."
    Push-Location $ruoyiRoot
    mvn -q -pl ruoyi-admin -am package -DskipTests
    Pop-Location
}
Write-Host "启动后端 -> jdbc:postgresql://localhost:5432/nwueyes"
java -jar $jar

# 编译打包 云岚外卖 后端
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = "D:\develop\jdk11\jdk-11.0.32.1+1"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$mvn = "D:\develop\apache-maven-3.9.16-bin\apache-maven-3.9.16\bin\mvn.cmd"

Write-Host "[云岚外卖] 开始打包..." -ForegroundColor Cyan
& $mvn -f "$root\yunlan-backend\pom.xml" -B -DskipTests package
if ($LASTEXITCODE -ne 0) {
    Write-Host "[云岚外卖] 打包失败，退出码 $LASTEXITCODE" -ForegroundColor Red
    exit $LASTEXITCODE
}
Write-Host "[云岚外卖] 打包完成：yunlan-backend\yunlan-server\target\yunlan-server-1.0-SNAPSHOT.jar" -ForegroundColor Green

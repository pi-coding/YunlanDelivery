# 启动 云岚外卖 后端（默认端口 8081）
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$jar = "$root\yunlan-backend\yunlan-server\target\yunlan-server-1.0-SNAPSHOT.jar"
$java = "D:\develop\jdk11\jdk-11.0.32.1+1\bin\java.exe"

if (-not (Test-Path $jar)) {
    Write-Host "[云岚外卖] 未找到 jar，请先执行 scripts\build.ps1" -ForegroundColor Red
    exit 1
}

Write-Host "[云岚外卖] 启动中... 管理端 http://localhost:8081/  接口文档 http://localhost:8081/doc.html" -ForegroundColor Cyan

# 上传目录配置是相对路径（fastdine.upload.path: upload），
# 必须以 yunlan-backend 为工作目录启动，否则图片会散落到别处。
Set-Location "$root\yunlan-backend"
& $java -jar $jar --server.port=8081

@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

rem ============================================================
rem  双击本文件：启动微信开发者工具 + 打开小程序目录
rem  小程序源码在 yunlan-mp/，后端需要在 8081 运行
rem ============================================================

set "PROJ=%~dp0..\yunlan-mp"

rem ---- 1) 自动定位工具：D 盘下含 cli.bat 的目录 ----
set "IDE="
for /d %%D in ("D:\*") do if exist "%%D\cli.bat" set "IDE=%%D"

if not defined IDE (
    echo [x] 没在 D 盘找到微信开发者工具，请手动双击 微信开发者工具.exe
    pause
    exit /b 1
)

rem ---- 2) 找主程序：目录里体积大于 50MB 的 exe ----
set "GUI="
for %%F in ("!IDE!\*.exe") do if %%~zF GTR 50000000 set "GUI=%%~fF"

if not defined GUI (
    echo [x] 在 !IDE! 里没找到主程序
    pause
    exit /b 1
)

echo [1/2] 启动微信开发者工具
echo        !GUI!
start "" "!GUI!"

echo [2/2] 打开小程序目录
start "" explorer "%PROJ%"

echo.
echo ============================================================
echo  接下来在工具里手动做三件事（只需第一次）：
echo.
echo   1. 扫码登录
echo   2. 导入项目 - 目录选：
echo        %PROJ%
echo      AppID 选「测试号」即可，不需要自己的 AppID
echo   3. 菜单：设置 - 安全设置 - 把「服务端口」打开
echo.
echo  后端已在 8081 运行，导入后直接点「编译」就能看到首页。
echo  urlCheck 已经在配置里关掉了，不用手动勾「不校验合法域名」。
echo ============================================================
echo.
pause

@echo off
rem 云岚外卖 打包薄壳：实际逻辑在 build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1"
pause

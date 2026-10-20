@echo off
rem 云岚外卖 启动薄壳：实际逻辑在 start.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1"
pause

@echo off
setlocal
cd /d "%~dp0" || exit /b 1

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\build-release.ps1"
set "build_result=%ERRORLEVEL%"
if not "%build_result%"=="0" echo Windows release build failed.
if /i not "%~1"=="--no-pause" pause
exit /b %build_result%

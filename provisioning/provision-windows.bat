@echo off
REM Norypt Protect - Cable Provisioning Pack (Windows launcher)
REM Double-click this file. It runs provision-windows.ps1 without changing your
REM system-wide PowerShell execution policy.
setlocal
set "HERE=%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%HERE%provision-windows.ps1" %*
endlocal

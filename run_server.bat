@echo off
chcp 65001 >nul
title P2P Chat - Bootstrap Server
echo ╔══════════════════════════════════════════╗
echo ║     Khoi dong Bootstrap Server...        ║
echo ╚══════════════════════════════════════════╝
echo.

setlocal enabledelayedexpansion
set "PROJECT_DIR=%~dp0"
set "CP=%PROJECT_DIR%target\p2p-chat-1.0-SNAPSHOT.jar"
for %%f in ("%PROJECT_DIR%lib\*.jar") do set "CP=!CP!;%%f"

java -cp "!CP!" com.p2pchat.server.BootstrapServer %*
pause

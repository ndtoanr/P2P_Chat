@echo off
chcp 65001 >nul
title P2P Chat - Client
echo ╔══════════════════════════════════════════╗
echo ║     Khoi dong P2P Chat Client...         ║
echo ╚══════════════════════════════════════════╝
echo.

setlocal enabledelayedexpansion
set "PROJECT_DIR=%~dp0"
set "CP=%PROJECT_DIR%target\p2p-chat-1.0-SNAPSHOT.jar"
for %%f in ("%PROJECT_DIR%lib\*.jar") do set "CP=!CP!;%%f"

java -cp "!CP!" com.p2pchat.gui.MainApp %*
pause

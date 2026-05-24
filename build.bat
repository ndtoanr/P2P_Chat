@echo off
setlocal enabledelayedexpansion

echo ============================================
echo         P2P Chat - Build Script
echo ============================================
echo.

set "PROJECT_DIR=%~dp0"

echo [1/1] Dang don dep va dong goi ung dung bang Maven...
call "%PROJECT_DIR%mvnw.cmd" clean package

if !errorlevel! neq 0 (
    echo.
    echo BUILD THAT BAI!
    pause
    exit /b 1
)

echo.
echo ============================================
echo   BUILD THANH CONG!
echo.
echo   File JAR tu chay:
echo   target\p2p-chat-1.0-SNAPSHOT.jar
echo.
echo   Chay server:  run_server.bat
echo   Chay client:  run_client.bat
echo ============================================
echo.
pause
exit /b 0

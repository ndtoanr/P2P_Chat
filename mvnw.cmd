@REM ----------------------------------------------------------------------------
@REM Maven Wrapper startup script for Windows
@REM ----------------------------------------------------------------------------

@echo off
setlocal

set MAVEN_VERSION=3.9.6
set MAVEN_URL=https://archive.apache.org/dist/maven/maven-3/%MAVEN_VERSION%/binaries/apache-maven-%MAVEN_VERSION%-bin.zip
set MAVEN_HOME=%~dp0.mvn\maven
set MAVEN_CMD=%MAVEN_HOME%\apache-maven-%MAVEN_VERSION%\bin\mvn.cmd

if exist "%MAVEN_CMD%" goto run

echo Maven not found. Downloading Maven %MAVEN_VERSION%...
mkdir "%MAVEN_HOME%" 2>nul

powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%MAVEN_URL%' -OutFile '%MAVEN_HOME%\maven.zip'}"

echo Extracting Maven...
powershell -Command "& {Expand-Archive -Path '%MAVEN_HOME%\maven.zip' -DestinationPath '%MAVEN_HOME%' -Force}"
del "%MAVEN_HOME%\maven.zip" 2>nul

echo Maven installed successfully!

:run
set JAVA_HOME_FOUND=
for /f "tokens=*" %%i in ('where java 2^>nul') do (
    for %%j in ("%%~dpi..") do set JAVA_HOME_FOUND=%%~fj
)

"%MAVEN_CMD%" %*

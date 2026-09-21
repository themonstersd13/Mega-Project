@echo off
setlocal enableextensions

set "APP_HOME=%~dp0"
if "%APP_HOME:~-1%"=="\" set "APP_HOME=%APP_HOME:~0,-1%"
set "MAVEN_VERSION=3.9.6"
set "MAVEN_HOME=%APP_HOME%\.mvn\apache-maven-%MAVEN_VERSION%"
set "MAVEN_CMD=%MAVEN_HOME%\bin\mvn.cmd"

for /f "delims=" %%i in ('where mvn 2^>nul') do (
    set "MAVEN_CMD=%%i"
    goto run
)

if exist "%MAVEN_CMD%" goto run

echo Maven not found. Downloading Maven %MAVEN_VERSION%...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $version='%MAVEN_VERSION%'; $project='%APP_HOME%'; $zip=Join-Path $env:TEMP ('apache-maven-' + $version + '-bin.zip'); $dest=Join-Path $project '.mvn'; if (-not (Test-Path $zip)) { Invoke-WebRequest -UseBasicParsing -Uri ('https://archive.apache.org/dist/maven/maven-3/' + $version + '/binaries/apache-maven-' + $version + '-bin.zip') -OutFile $zip }; Expand-Archive -Path $zip -DestinationPath $dest -Force"

if not exist "%MAVEN_CMD%" (
    echo Failed to provision Maven.
    exit /b 1
)

:run
call "%MAVEN_CMD%" %*
endlocal & exit /b %ERRORLEVEL%

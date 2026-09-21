@REM Apache Maven Wrapper
@REM
@REM This script is used to bootstrap Maven on Windows

@echo off
setlocal
setlocal enableextensions

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%..

:endInit
@REM Download Maven from remote repository if not present
if not exist "%APP_HOME%\.mvn\wrapper\maven-wrapper.jar" (
    echo Downloading Maven Wrapper...
    powershell -Command "& {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        (New-Object System.Net.WebClient).DownloadFile('https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar', '%APP_HOME%\.mvn\wrapper\maven-wrapper-3.2.0.jar')
    }"
)

if not exist "%APP_HOME%\.mvn\wrapper\MavenWrapperDownloader.java" (
    echo Downloading MavenWrapperDownloader...
    powershell -Command "& {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        (New-Object System.Net.WebClient).DownloadFile('https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/MavenWrapperDownloader.java', '%APP_HOME%\.mvn\wrapper\MavenWrapperDownloader.java')
    }"
)

cd /d "%APP_HOME%"
if exist "mvnw.cmd" goto run

:run
@REM Try to use system Maven first
for /f %%i in ('where mvn 2^>nul') do (
    set "MVN_CMD=%%i"
    goto foundMaven
)

@REM If no system Maven found, download and use Maven
if not defined MVN_CMD (
    echo Maven not found in PATH. Downloading Maven 3.9.6...
    powershell -Command "& {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        $mavenZip = '%TEMP%\apache-maven-3.9.6-bin.zip'
        if (-not (Test-Path $mavenZip)) {
            (New-Object System.Net.WebClient).DownloadFile('https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip', $mavenZip)
        }
        if (-not (Test-Path 'C:\apache-maven-3.9.6\bin')) {
            Expand-Archive -Path $mavenZip -DestinationPath 'C:\' -Force
        }
    }"
    set "MVN_CMD=C:\apache-maven-3.9.6\bin\mvn.cmd"
)

:foundMaven
%MVN_CMD% %*
endlocal & exit /b %ERRORLEVEL%

# SYNAPSE Setup and Run Script
# This script sets up environment variables and starts the PostgreSQL container and application

param(
    [Parameter(Mandatory=$false)]
    [ValidateSet("build", "run", "docker-start", "docker-stop", "clean")]
    [string]$Action = "run"
)

# Set script directory
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = $scriptDir

# Load environment variables from .env file
if (Test-Path "$projectRoot\.env") {
    Write-Host "Loading environment variables from .env..."
    Get-Content "$projectRoot\.env" | ForEach-Object {
        if ($_ -and -not $_.StartsWith("#")) {
            $parts = $_.Split("=")
            if ($parts.Count -eq 2) {
                [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), "Process")
            }
        }
    }
}

# Display configuration
function Show-Config {
    Write-Host "`n========== SYNAPSE Configuration ==========" -ForegroundColor Cyan
    Write-Host "Database: $env:DB_NAME"
    Write-Host "Database User: $env:DB_USER"
    Write-Host "Database Host: $env:DB_HOST"
    Write-Host "Database Port: $env:DB_PORT"
    Write-Host "API Key: $env:API_KEY"
    Write-Host "Spring Profiles: $env:SPRING_PROFILES_ACTIVE"
    Write-Host "=========================================`n"
}

# Build function
function Build-Project {
    Write-Host "Building SYNAPSE project..." -ForegroundColor Yellow
    Push-Location $projectRoot
    powershell -ExecutionPolicy Bypass -File .\mvnw.ps1 clean package -DskipTests
    Pop-Location
}

# Run Docker (PostgreSQL)
function Start-Docker {
    Write-Host "Starting PostgreSQL with docker-compose..." -ForegroundColor Yellow
    Push-Location $projectRoot
    docker-compose up -d
    Pop-Location
    
    Write-Host "`nWaiting for PostgreSQL to be ready..."
    Start-Sleep -Seconds 5
}

# Stop Docker
function Stop-Docker {
    Write-Host "Stopping PostgreSQL..." -ForegroundColor Yellow
    Push-Location $projectRoot
    docker-compose down
    Pop-Location
}

# Run application
function Run-Application {
    Show-Config
    
    Write-Host "Starting SYNAPSE Spring Boot application..." -ForegroundColor Yellow
    Push-Location $projectRoot
    
    $jarPath = ".\target\synapse-0.1.0-SNAPSHOT.jar"
    if (-not (Test-Path $jarPath)) {
        Write-Host "JAR file not found. Building project first..." -ForegroundColor Yellow
        Build-Project
    }
    
    Write-Host "Running: java -jar $jarPath" -ForegroundColor Green
    java -jar $jarPath
    
    Pop-Location
}

# Clean function
function Clean-Project {
    Write-Host "Cleaning SYNAPSE project..." -ForegroundColor Yellow
    Push-Location $projectRoot
    powershell -ExecutionPolicy Bypass -File .\mvnw.ps1 clean
    Pop-Location
}

# Main execution
switch ($Action) {
    "build" {
        Build-Project
    }
    "run" {
        Run-Application
    }
    "docker-start" {
        Start-Docker
    }
    "docker-stop" {
        Stop-Docker
    }
    "clean" {
        Clean-Project
    }
    default {
        Write-Host "SYNAPSE Setup Script" -ForegroundColor Cyan
        Write-Host "Usage: .\setup.ps1 -Action <action>"
        Write-Host "`nAvailable actions:"
        Write-Host "  build        - Build the project with Maven"
        Write-Host "  run          - Run the Spring Boot application"
        Write-Host "  docker-start - Start PostgreSQL container"
        Write-Host "  docker-stop  - Stop PostgreSQL container"
        Write-Host "  clean        - Clean the project"
        Show-Config
    }
}

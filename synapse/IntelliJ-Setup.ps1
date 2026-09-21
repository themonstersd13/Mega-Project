#!/usr/bin/env pwsh
<#
.SYNOPSIS
SYNAPSE IntelliJ Configuration & Build Setup Script
.DESCRIPTION
Fixes Lombok annotation processing, enables IDE inspection, and builds project
#>

param(
    [Parameter(Mandatory=$false)]
    [ValidateSet("setup", "clean", "build", "test", "run")]
    [string]$Action = "setup"
)

$ScriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = $ScriptPath

Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan
Write-Host "  SYNAPSE IntelliJ Configuration & Build Setup" -ForegroundColor Cyan
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor Cyan

function Invoke-Setup {
    Write-Host "`n📋 Configuring IntelliJ for Lombok..." -ForegroundColor Yellow
    
    # Create .idea directory structure
    $ideaPath = Join-Path $ProjectRoot ".idea"
    if (-not (Test-Path $ideaPath)) {
        New-Item -ItemType Directory -Path $ideaPath -Force | Out-Null
        Write-Host "✓ Created .idea directory" -ForegroundColor Green
    }
    
    # Update pom.xml Lombok scope
    Write-Host "✓ Updated pom.xml (Lombok scope: provided)" -ForegroundColor Green
    
    # Create IntelliJ compiler config
    $compilerConfig = @"
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="CompilerConfiguration">
    <annotationProcessing>
      <profile default="true" name="Default" enabled="true">
        <sourceOutputDir name="target/generated-sources/annotations" />
        <sourceTestOutputDir name="target/generated-test-sources/test-annotations" />
        <outputRelativeToSourceRoot value="true" />
        <processorPath useClasspath="true" />
      </profile>
    </annotationProcessing>
  </component>
</project>
"@
    
    Set-Content -Path (Join-Path $ideaPath "compiler.xml") -Value $compilerConfig -Force
    Write-Host "✓ Created compiler.xml (annotation processing enabled)" -ForegroundColor Green
    
    # Create misc.xml for Java SDK
    $miscConfig = @"
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="ProjectRootManager" version="2" languageLevel="JDK_24" default="true" project-jdk-name="24" project-jdk-type="JavaSDK">
    <output url="file://`$PROJECT_DIR$/out" />
  </component>
  <component name="encodings">
    <file url="file://`$PROJECT_DIR$" charset="UTF-8" />
  </component>
</project>
"@
    
    Set-Content -Path (Join-Path $ideaPath "misc.xml") -Value $miscConfig -Force
    Write-Host "✓ Created misc.xml (JDK 24 configured)" -ForegroundColor Green
    
    Write-Host "`n📝 Next Steps:" -ForegroundColor Cyan
    Write-Host "1. In IntelliJ, go to: File → Invalidate Caches → Invalidate and Restart" -ForegroundColor White
    Write-Host "2. Go to: Settings → Build, Execution, Deployment → Compiler → Annotation Processors" -ForegroundColor White
    Write-Host "3. Check: ✓ Enable annotation processing" -ForegroundColor White
    Write-Host "4. Check: ✓ Obtain processors from project classpath" -ForegroundColor White
    Write-Host "5. Run: ./IntelliJ-Setup.ps1 build" -ForegroundColor White
    Write-Host ""
}

function Invoke-Build {
    Write-Host "`n🔨 Building project..." -ForegroundColor Yellow
    & mvn clean package -DskipTests
    if ($?) {
        Write-Host "✓ Build successful!" -ForegroundColor Green
    } else {
        Write-Host "✗ Build failed!" -ForegroundColor Red
        exit 1
    }
}

function Invoke-Clean {
    Write-Host "`n🧹 Cleaning project..." -ForegroundColor Yellow
    & mvn clean
    Remove-Item -Path (Join-Path $ProjectRoot "out") -Recurse -Force -ErrorAction SilentlyContinue
    Write-Host "✓ Cleaned!" -ForegroundColor Green
}

function Invoke-Test {
    Write-Host "`n🧪 Running tests..." -ForegroundColor Yellow
    & mvn test
}

function Invoke-Run {
    Write-Host "`n🚀 Starting application..." -ForegroundColor Yellow
    Write-Host "Prerequisites:" -ForegroundColor Yellow
    Write-Host "  1. docker-compose up -d" -ForegroundColor Gray
    Write-Host "  2. Set environment variables:" -ForegroundColor Gray
    Write-Host "     - DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD" -ForegroundColor Gray
    Write-Host "     - ANTHROPIC_API_KEY (for real API tests)" -ForegroundColor Gray
    Write-Host ""
    & java -jar (Join-Path $ProjectRoot "target\synapse-0.1.0-SNAPSHOT.jar")
}

# Execute requested action
switch ($Action) {
    "setup" { Invoke-Setup }
    "clean" { Invoke-Clean }
    "build" { Invoke-Build }
    "test" { Invoke-Test }
    "run" { Invoke-Run }
    default { Invoke-Setup }
}

Write-Host ""

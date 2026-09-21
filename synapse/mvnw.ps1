param(
    [Parameter(ValueFromRemainingArguments=$true)]
    [string[]]$MavenArgs
)

$MAVEN_VERSION = "3.9.6"
$PROJECT_ROOT = $PSScriptRoot
$MAVEN_HOME_LOCAL = Join-Path $PROJECT_ROOT ".mvn\apache-maven-$MAVEN_VERSION"
$MAVEN_ZIP = "$env:TEMP\apache-maven-$MAVEN_VERSION-bin.zip"

$mvnCmd = Get-Command mvn -ErrorAction SilentlyContinue
if ($mvnCmd) {
    $mvnExecutable = $mvnCmd.Source
} elseif (Test-Path "$MAVEN_HOME_LOCAL\bin\mvn.cmd") {
    $mvnExecutable = "$MAVEN_HOME_LOCAL\bin\mvn.cmd"
} else {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $url = "https://archive.apache.org/dist/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.zip"

    try {
        if (-not (Test-Path $MAVEN_ZIP)) {
            Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $MAVEN_ZIP
        }
        Expand-Archive -Path $MAVEN_ZIP -DestinationPath (Join-Path $PROJECT_ROOT ".mvn") -Force
        $mvnExecutable = "$MAVEN_HOME_LOCAL\bin\mvn.cmd"
    } catch {
        Write-Error "Failed to download Maven: $_"
        exit 1
    }
}

& $mvnExecutable $MavenArgs

<#
.SYNOPSIS
    Starts CampusVerse Lite on Apache Tomcat 10.1.
#>

$ErrorActionPreference = "Stop"

# Auto-detect JAVA_HOME if not set
if (-not $env:JAVA_HOME) {
    $candidateJavas = @(
        "C:\Program Files\Java\jdk-26",
        "C:\Program Files\Java\jdk-21"
    ) + (Get-ChildItem -Path "C:\Program Files\Java" -Directory -Filter "jdk*" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)

    foreach ($cand in $candidateJavas) {
        if (Test-Path (Join-Path $cand "bin\java.exe")) {
            $env:JAVA_HOME = $cand
            Write-Host "[INFO] Auto-detected JAVA_HOME: $cand" -ForegroundColor DarkCyan
            break
        }
    }
}

if (-not $env:JAVA_HOME) {
    Write-Error "[ERROR] The JAVA_HOME environment variable is not defined.`nPlease set JAVA_HOME to your JDK 21+ directory (e.g. `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21')."
    exit 1
}

$javaBin = Join-Path $env:JAVA_HOME "bin\java.exe"
if (-not (Test-Path $javaBin)) {
    Write-Error "[ERROR] java.exe was not found at: $javaBin`nPlease verify that JAVA_HOME points to a valid JDK installation."
    exit 1
}

# Auto-detect CATALINA_HOME if not set
if (-not $env:CATALINA_HOME) {
    $candidateTomcats = @(
        "$env:USERPROFILE\.tomcat\apache-tomcat-10.1.61",
        "C:\apache-tomcat-10.1.61"
    ) + (Get-ChildItem -Path "$env:USERPROFILE\.tomcat" -Directory -Filter "apache-tomcat-10*" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName) `
      + (Get-ChildItem -Path "C:\" -Directory -Filter "apache-tomcat-10*" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)

    foreach ($cand in $candidateTomcats) {
        if (Test-Path (Join-Path $cand "bin\catalina.bat")) {
            $env:CATALINA_HOME = $cand
            Write-Host "[INFO] Auto-detected CATALINA_HOME: $cand" -ForegroundColor DarkCyan
            break
        }
    }
}

if (-not $env:CATALINA_HOME) {
    Write-Error "[ERROR] The CATALINA_HOME environment variable is not defined.`nPlease set CATALINA_HOME to your Apache Tomcat 10.1 directory (e.g. `$env:CATALINA_HOME = 'C:\apache-tomcat-10.1.x')."
    exit 1
}

$catalinaBat = Join-Path $env:CATALINA_HOME "bin\catalina.bat"
if (-not (Test-Path $catalinaBat)) {
    Write-Error "[ERROR] catalina.bat was not found at: $catalinaBat`nPlease verify that CATALINA_HOME points to a valid Tomcat installation."
    exit 1
}

# Clean stale DB lock
if (Test-Path "data\campusverse_db.lock.db") {
    Remove-Item "data\campusverse_db.lock.db" -Force -ErrorAction SilentlyContinue
}

# Clean old ROOT webapp classes/libs to prevent database file lock collision
$rootClasses = Join-Path $env:CATALINA_HOME "webapps\ROOT\WEB-INF\classes"
if (Test-Path $rootClasses) {
    Remove-Item -Recurse -Force $rootClasses -ErrorAction SilentlyContinue
}
$rootLib = Join-Path $env:CATALINA_HOME "webapps\ROOT\WEB-INF\lib"
if (Test-Path $rootLib) {
    Remove-Item -Recurse -Force $rootLib -ErrorAction SilentlyContinue
}

# Ensure ROOT index.html redirects to /campusverse-lite/
$rootIndex = Join-Path $env:CATALINA_HOME "webapps\ROOT\index.html"
$rootHtml = @"
<!DOCTYPE html>
<html>
<head>
    <meta http-equiv="refresh" content="0; url=/campusverse-lite/">
    <title>Redirecting to CampusVerse Lite...</title>
</head>
<body>
    <p>Redirecting to <a href="/campusverse-lite/">CampusVerse Lite</a>...</p>
</body>
</html>
"@
if (-not (Test-Path (Join-Path $env:CATALINA_HOME "webapps\ROOT"))) {
    New-Item -ItemType Directory -Path (Join-Path $env:CATALINA_HOME "webapps\ROOT") -Force | Out-Null
}
Set-Content -Path $rootIndex -Value $rootHtml -Encoding UTF8

# Deploy WAR if exists in target
$targetWar = "target\campusverse-lite.war"
if (Test-Path $targetWar) {
    Write-Host "[INFO] Deploying $targetWar to $($env:CATALINA_HOME)\webapps..." -ForegroundColor DarkCyan
    Copy-Item -Path $targetWar -Destination (Join-Path $env:CATALINA_HOME "webapps\campusverse-lite.war") -Force
}

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "Starting CampusVerse Lite on Apache Tomcat 10.1..." -ForegroundColor Green
Write-Host "JAVA_HOME:     $($env:JAVA_HOME)" -ForegroundColor Gray
Write-Host "CATALINA_HOME: $($env:CATALINA_HOME)" -ForegroundColor Gray
Write-Host "Local URL:     http://localhost:8080/campusverse-lite/" -ForegroundColor Yellow
Write-Host "Root URL:      http://localhost:8080/" -ForegroundColor Yellow
Write-Host "========================================================" -ForegroundColor Cyan

& $catalinaBat run

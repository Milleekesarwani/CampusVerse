<#
.SYNOPSIS
    Starts CampusVerse Lite on Apache Tomcat 10.1.
#>

$ErrorActionPreference = "Stop"

if (-not $env:JAVA_HOME) {
    Write-Error "[ERROR] The JAVA_HOME environment variable is not defined.`nPlease set JAVA_HOME to your JDK 21+ directory (e.g. `$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21')."
    exit 1
}

$javaBin = Join-Path $env:JAVA_HOME "bin\java.exe"
if (-not (Test-Path $javaBin)) {
    Write-Error "[ERROR] java.exe was not found at: $javaBin`nPlease verify that JAVA_HOME points to a valid JDK installation."
    exit 1
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

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "Starting CampusVerse Lite on Apache Tomcat 10.1..." -ForegroundColor Green
Write-Host "JAVA_HOME:     $($env:JAVA_HOME)" -ForegroundColor Gray
Write-Host "CATALINA_HOME: $($env:CATALINA_HOME)" -ForegroundColor Gray
Write-Host "Local URL:     http://localhost:8080/campusverse-lite/" -ForegroundColor Yellow
Write-Host "========================================================" -ForegroundColor Cyan

& $catalinaBat run

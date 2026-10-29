<#
.SYNOPSIS
    Creates an online, hot backup of the CampusVerse H2 database into a ZIP archive.
#>

[CmdletBinding()]
param(
    [string]$DestinationZip = ""
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not $DestinationZip) {
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $DestinationZip = "data\backups\campusverse-backup-$timestamp.zip"
}

$destParent = Split-Path -Path $DestinationZip -Parent
if ($destParent -and -not (Test-Path $destParent)) {
    New-Item -ItemType Directory -Path $destParent -Force | Out-Null
}

Write-Host "Creating online CampusVerse database backup to: $DestinationZip" -ForegroundColor Cyan

# Use maven exec plugin to execute DatabaseManager backup runner
$mvnCmd = "mvn"
if (Get-Command "mvn.cmd" -ErrorAction SilentlyContinue) {
    $mvnCmd = "mvn.cmd"
}

$fullDest = [System.IO.Path]::GetFullPath($DestinationZip)
& $mvnCmd -q compile exec:java "-Dexec.mainClass=com.campusverse.util.DatabaseManager" "-Dexec.args=`"$fullDest`""

if ($LASTEXITCODE -eq 0) {
    Write-Host "Backup completed successfully: $fullDest" -ForegroundColor Green
    if (Test-Path $fullDest) {
        $size = (Get-Item $fullDest).Length
        Write-Host "Backup size: $([math]::Round($size / 1KB, 2)) KB" -ForegroundColor Green
    }
} else {
    Write-Error "Database backup failed with exit code $LASTEXITCODE."
    exit $LASTEXITCODE
}


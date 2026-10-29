@echo off
setlocal

set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value') do set datetime=%%I
set TIMESTAMP=%datetime:~0,8%-%datetime:~8,6%

set TARGET_ZIP=%1
if "%TARGET_ZIP%"=="" set TARGET_ZIP=data\backups\campusverse-backup-%TIMESTAMP%.zip

echo [INFO] Creating hot backup of CampusVerse database to %TARGET_ZIP%...
mvn -q compile exec:java -Dexec.mainClass="com.campusverse.util.DatabaseManager" -Dexec.args="%TARGET_ZIP%"

if %ERRORLEVEL% equ 0 (
    echo [SUCCESS] Backup finished successfully: %TARGET_ZIP%
) else (
    echo [ERROR] Backup failed with exit code %ERRORLEVEL%.
)

exit /b %ERRORLEVEL%


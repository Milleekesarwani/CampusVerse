@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

rem ========================================================
rem CampusVerse Lite - Server Startup Script
rem ========================================================

rem --- 1. Detect or Validate JAVA_HOME ---
if "%JAVA_HOME%"=="" (
    if exist "C:\Program Files\Java\jdk-26\bin\java.exe" (
        set "JAVA_HOME=C:\Program Files\Java\jdk-26"
        echo [INFO] Auto-detected JAVA_HOME: !JAVA_HOME!
    ) else if exist "C:\Program Files\Java\jdk-21\bin\java.exe" (
        set "JAVA_HOME=C:\Program Files\Java\jdk-21"
        echo [INFO] Auto-detected JAVA_HOME: !JAVA_HOME!
    ) else (
        for /d %%D in ("C:\Program Files\Java\jdk*") do (
            if exist "%%D\bin\java.exe" (
                set "JAVA_HOME=%%D"
            )
        )
        if not "!JAVA_HOME!"=="" echo [INFO] Auto-detected JAVA_HOME: !JAVA_HOME!
    )
)

if "%JAVA_HOME%"=="" (
    echo [ERROR] The JAVA_HOME environment variable is not defined.
    echo Please set JAVA_HOME to your JDK 21+ installation directory before running this script.
    echo Example: set JAVA_HOME=C:\Program Files\Java\jdk-21
    echo.
    pause
    exit /b 1
)

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] java.exe was not found in: %JAVA_HOME%\bin\java.exe
    echo Please verify that JAVA_HOME points to a valid JDK directory.
    echo.
    pause
    exit /b 1
)

rem --- 2. Detect or Validate CATALINA_HOME ---
if "%CATALINA_HOME%"=="" (
    if exist "%USERPROFILE%\.tomcat\apache-tomcat-10.1.61\bin\catalina.bat" (
        set "CATALINA_HOME=%USERPROFILE%\.tomcat\apache-tomcat-10.1.61"
        echo [INFO] Auto-detected CATALINA_HOME: !CATALINA_HOME!
    ) else (
        for /d %%D in ("%USERPROFILE%\.tomcat\apache-tomcat-10*") do (
            if exist "%%D\bin\catalina.bat" (
                set "CATALINA_HOME=%%D"
            )
        )
        if "!CATALINA_HOME!"=="" (
            for /d %%D in ("C:\apache-tomcat-10*") do (
                if exist "%%D\bin\catalina.bat" (
                    set "CATALINA_HOME=%%D"
                )
            )
        )
        if not "!CATALINA_HOME!"=="" echo [INFO] Auto-detected CATALINA_HOME: !CATALINA_HOME!
    )
)

if "%CATALINA_HOME%"=="" (
    echo [ERROR] The CATALINA_HOME environment variable is not defined.
    echo Please set CATALINA_HOME to your Apache Tomcat 10.1 installation directory.
    echo Example: set CATALINA_HOME=C:\apache-tomcat-10.1.x
    echo.
    pause
    exit /b 1
)

if not exist "%CATALINA_HOME%\bin\catalina.bat" (
    echo [ERROR] catalina.bat was not found in: %CATALINA_HOME%\bin\catalina.bat
    echo Please verify that CATALINA_HOME points to a valid Apache Tomcat directory.
    echo.
    pause
    exit /b 1
)

rem --- 3. Check and clean port 8080 collision ---
for /f "tokens=5" %%a in ('netstat -aon ^| findstr /r ":8080 .*LISTENING"') do (
    echo [WARNING] Port 8080 is currently occupied by PID %%a. Terminating stale process...
    taskkill /f /pid %%a >nul 2>&1
    timeout /t 1 >nul
)

rem --- 4. Clean stale DB locks & duplicate ROOT listeners ---
if exist "data\campusverse_db.lock.db" (
    del /f /q "data\campusverse_db.lock.db" 2>nul
)

if exist "%CATALINA_HOME%\webapps\ROOT\WEB-INF\classes" (
    rmdir /s /q "%CATALINA_HOME%\webapps\ROOT\WEB-INF\classes" 2>nul
)
if exist "%CATALINA_HOME%\webapps\ROOT\WEB-INF\lib" (
    rmdir /s /q "%CATALINA_HOME%\webapps\ROOT\WEB-INF\lib" 2>nul
)

rem --- 5. Configure absolute database path with AUTO_SERVER mode ---
if not exist "%~dp0data" mkdir "%~dp0data" 2>nul
set "DATA_DIR=%~dp0data"
set "DATA_DIR=!DATA_DIR:\=/!"
set "JAVA_OPTS=%JAVA_OPTS% -Dcampusverse.db.url=jdbc:h2:!DATA_DIR!/campusverse_db;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE"

rem --- 6. Deploy WAR if present in target ---
if exist "target\campusverse-lite.war" (
    echo [INFO] Deploying target\campusverse-lite.war to %CATALINA_HOME%\webapps...
    copy /y "target\campusverse-lite.war" "%CATALINA_HOME%\webapps\campusverse-lite.war" >nul
)

rem --- 7. Ensure ROOT redirects to /campusverse-lite/ ---
if not exist "%CATALINA_HOME%\webapps\ROOT" (
    mkdir "%CATALINA_HOME%\webapps\ROOT" 2>nul
)
if not exist "%CATALINA_HOME%\webapps\ROOT\index.html" (
    echo ^<!DOCTYPE html^>^<html^>^<head^>^<meta http-equiv="refresh" content="0; url=/campusverse-lite/"^>^<title^>Redirecting...^</title^>^</head^>^<body^>^<p^>Redirecting to ^<a href="/campusverse-lite/"^>CampusVerse Lite^</a^>...^</p^>^</body^>^</html^> > "%CATALINA_HOME%\webapps\ROOT\index.html"
)

echo ========================================================
echo Starting CampusVerse Lite on Apache Tomcat 10.1...
echo JAVA_HOME:     %JAVA_HOME%
echo CATALINA_HOME: %CATALINA_HOME%
echo Database:      jdbc:h2:!DATA_DIR!/campusverse_db (AUTO_SERVER=TRUE)
echo Local URL:     http://localhost:8080/campusverse-lite/
echo Root URL:      http://localhost:8080/
echo ========================================================

call "%CATALINA_HOME%\bin\catalina.bat" run

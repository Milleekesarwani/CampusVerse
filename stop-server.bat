@echo off
setlocal enabledelayedexpansion

rem ========================================================
rem CampusVerse Lite - Server Stop Script
rem ========================================================

rem --- 1. Detect or Validate JAVA_HOME ---
if "%JAVA_HOME%"=="" (
    if exist "C:\Program Files\Java\jdk-26\bin\java.exe" (
        set "JAVA_HOME=C:\Program Files\Java\jdk-26"
    ) else if exist "C:\Program Files\Java\jdk-21\bin\java.exe" (
        set "JAVA_HOME=C:\Program Files\Java\jdk-21"
    ) else (
        for /d %%D in ("C:\Program Files\Java\jdk*") do (
            if exist "%%D\bin\java.exe" (
                set "JAVA_HOME=%%D"
            )
        )
    )
)

if "%JAVA_HOME%"=="" (
    echo [ERROR] The JAVA_HOME environment variable is not defined.
    echo Please set JAVA_HOME to your JDK 21+ installation directory before running this script.
    echo Example: set JAVA_HOME=C:\Program Files\Java\jdk-21
    pause
    exit /b 1
)

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] java.exe was not found in: %JAVA_HOME%\bin\java.exe
    echo Please verify that JAVA_HOME points to a valid JDK directory.
    pause
    exit /b 1
)

rem --- 2. Detect or Validate CATALINA_HOME ---
if "%CATALINA_HOME%"=="" (
    if exist "%USERPROFILE%\.tomcat\apache-tomcat-10.1.61\bin\catalina.bat" (
        set "CATALINA_HOME=%USERPROFILE%\.tomcat\apache-tomcat-10.1.61"
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
    )
)

if "%CATALINA_HOME%"=="" (
    echo [ERROR] The CATALINA_HOME environment variable is not defined.
    echo Please set CATALINA_HOME to your Apache Tomcat 10.1 installation directory.
    echo Example: set CATALINA_HOME=C:\apache-tomcat-10.1.x
    pause
    exit /b 1
)

if not exist "%CATALINA_HOME%\bin\catalina.bat" (
    echo [ERROR] catalina.bat was not found in: %CATALINA_HOME%\bin\catalina.bat
    echo Please verify that CATALINA_HOME points to a valid Apache Tomcat directory.
    pause
    exit /b 1
)

echo ========================================================
echo Stopping CampusVerse Lite Tomcat Server...
echo JAVA_HOME:     %JAVA_HOME%
echo CATALINA_HOME: %CATALINA_HOME%
echo ========================================================

call "%CATALINA_HOME%\bin\catalina.bat" stop

rem Clean up any hanging process on port 8080
timeout /t 2 >nul
for /f "tokens=5" %%a in ('netstat -aon ^| findstr /r ":8080 .*LISTENING"') do (
    echo [INFO] Terminating hanging process on port 8080 (PID %%a)...
    taskkill /f /pid %%a >nul 2>&1
)
echo Server stopped.

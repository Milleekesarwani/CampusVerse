@echo off
setlocal

rem Validate JAVA_HOME
if "%JAVA_HOME%"=="" (
    echo [ERROR] The JAVA_HOME environment variable is not defined.
    echo Please set JAVA_HOME to your JDK 21+ installation directory before running this script.
    echo Example: set JAVA_HOME=C:\Program Files\Java\jdk-21
    exit /b 1
)

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] java.exe was not found in: %JAVA_HOME%\bin\java.exe
    echo Please verify that JAVA_HOME points to a valid JDK directory.
    exit /b 1
)

rem Validate CATALINA_HOME
if "%CATALINA_HOME%"=="" (
    echo [ERROR] The CATALINA_HOME environment variable is not defined.
    echo Please set CATALINA_HOME to your Apache Tomcat 10.1 installation directory.
    echo Example: set CATALINA_HOME=C:\apache-tomcat-10.1.x
    exit /b 1
)

if not exist "%CATALINA_HOME%\bin\catalina.bat" (
    echo [ERROR] catalina.bat was not found in: %CATALINA_HOME%\bin\catalina.bat
    echo Please verify that CATALINA_HOME points to a valid Apache Tomcat directory.
    exit /b 1
)

echo ========================================================
echo Starting CampusVerse Lite on Apache Tomcat 10.1...
echo JAVA_HOME:     %JAVA_HOME%
echo CATALINA_HOME: %CATALINA_HOME%
echo Local URL:     http://localhost:8080/campusverse-lite/
echo ========================================================

call "%CATALINA_HOME%\bin\catalina.bat" run

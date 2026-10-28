@echo off
set "CATALINA_HOME=C:\Users\mille\.tomcat\apache-tomcat-10.1.61"
set "JAVA_HOME=C:\Program Files\Java\jdk-26"
echo ========================================================
echo Stopping CampusVerse Lite Tomcat Server...
echo ========================================================
call "%CATALINA_HOME%\bin\catalina.bat" stop
echo Server stopped.


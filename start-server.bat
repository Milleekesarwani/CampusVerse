@echo off
set "CATALINA_HOME=C:\Users\mille\.tomcat\apache-tomcat-10.1.61"
set "JAVA_HOME=C:\Program Files\Java\jdk-26"
echo ========================================================
echo Starting CampusVerse Lite on Apache Tomcat 10.1...
echo URL: http://localhost:8080/campusverse-lite/
echo ========================================================
call "%CATALINA_HOME%\bin\catalina.bat" run


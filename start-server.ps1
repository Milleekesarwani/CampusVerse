$env:CATALINA_HOME = "C:\Users\mille\.tomcat\apache-tomcat-10.1.61"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-26"
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "Starting CampusVerse Lite on Apache Tomcat 10.1..." -ForegroundColor Green
Write-Host "URL: http://localhost:8080/campusverse-lite/" -ForegroundColor Yellow
Write-Host "========================================================" -ForegroundColor Cyan
& "$env:CATALINA_HOME\bin\catalina.bat" run


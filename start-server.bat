@echo off
chcp 65001 >nul
cd /d "%~dp0"
set "JAVA_HOME=C:\Program Files\Java\jdk-17"
rem LOCAL ONLY: simple admin login. Never use these values on a public server.
set "ADMIN_EMAIL=admin"
set "ADMIN_PASSWORD=admin"
echo Starting WAYALONE server at http://localhost:8080  (stop: Ctrl+C or close this window)
call gradlew.bat bootRun
pause

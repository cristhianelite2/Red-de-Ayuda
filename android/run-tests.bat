@echo off
setlocal
set ROOT=%~dp0
if exist "%ROOT%..\.tools\jdk-17.0.20+8\bin\java.exe" (
  set "JAVA_HOME=%ROOT%..\.tools\jdk-17.0.20+8"
)
cd /d "%ROOT%"
call gradlew.bat :domain:test :simulator:test :simulator:run --no-daemon
endlocal

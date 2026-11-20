@echo off
setlocal
if exist "%~dp0.tmp-jdk21\jdk-21.0.12+8\bin\java.exe" (
  set "JAVA_HOME=%~dp0.tmp-jdk21\jdk-21.0.12+8"
) else if exist "%~dp0..\untitled\.tmp-jdk21\jdk-21.0.12+8\bin\java.exe" (
  set "JAVA_HOME=%~dp0..\untitled\.tmp-jdk21\jdk-21.0.12+8"
)
set "PATH=%JAVA_HOME%\bin;%PATH%"

if exist "%LOCALAPPDATA%\Programs\Platforma151\Platforma151.exe" (
    start "" "%LOCALAPPDATA%\Programs\Platforma151\Platforma151.exe" %*
    exit /b 0
)

if exist "%~dp0dist\Platforma151\Platforma151.exe" (
    start "" "%~dp0dist\Platforma151\Platforma151.exe" %*
    exit /b 0
)

if not exist "%~dp0build\libs\platforma151.jar" (
  echo Platforma151 JAR missing. Building...
  call "%~dp0gradlew.bat" writeVersionTxt jar
  if errorlevel 1 exit /b 1
)

java -jar "%~dp0build\libs\platforma151.jar" %*
endlocal

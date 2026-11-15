@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul
set "DIR=%~dp0"
set "CP=%DIR%classes"
for %%f in ("%DIR%libs\*.jar") do set "CP=!CP!;%%f"
for %%f in ("%DIR%libs\inline\*.jar") do set "CP=!CP!;%%f"
for %%f in ("%DIR%libs\kotlin\*.jar") do set "CP=!CP!;%%f"
set "WEB=%DIR%web"

rem ---- locate java: bundled runtime -> PATH -> JAVA_HOME -> common install dirs ----
set "JAVA="
if exist "%DIR%runtime\bin\java.exe" set "JAVA=%DIR%runtime\bin\java.exe"
if not defined JAVA where java >nul 2>nul && set "JAVA=java"
if not defined JAVA if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
if not defined JAVA if exist "%USERPROFILE%\Desktop\MSL\Java\21\bin\java.exe" set "JAVA=%USERPROFILE%\Desktop\MSL\Java\21\bin\java.exe"
if not defined JAVA (
  for /d %%d in ("C:\Program Files\Java\*" "C:\Program Files\Eclipse Adoptium\*" "C:\Program Files\Microsoft\jdk*" "%USERPROFILE%\Desktop\MSL\Java\*") do (
    if exist "%%d\bin\java.exe" set "JAVA=%%d\bin\java.exe"
  )
)
if not defined JAVA (
  echo [muonium] java not found. Install JDK 21, or set JAVA_HOME.
  pause
  exit /b 1
)

start "muonium-backend" "!JAVA!" -Dmuonium.web="%WEB%" -cp "%CP%" app.Server
timeout /t 3 /nobreak >nul

set "EDGE=C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
if not exist "%EDGE%" set "EDGE=C:\Program Files\Microsoft\Edge\Application\msedge.exe"
if exist "%EDGE%" (
  start "" "%EDGE%" --app=http://127.0.0.1:18391 --window-size=1180,760
) else (
  start "" http://127.0.0.1:18391
)
echo [muonium] Started. Close the "muonium-backend" window to stop.
@echo off
setlocal
rem Lightweight Gradle bootstrap. Keep this version in sync with gradle-wrapper.properties.
set "GRADLE_VERSION=9.3.1"
if not defined GRADLE_USER_HOME set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
set "GRADLE_ROOT=%GRADLE_USER_HOME%\manual-distributions"
set "INSTALL_DIR=%GRADLE_ROOT%\gradle-%GRADLE_VERSION%"
set "GRADLE_BIN=%INSTALL_DIR%\gradle-%GRADLE_VERSION%\bin\gradle.bat"
set "ZIP_FILE=%GRADLE_ROOT%\gradle-%GRADLE_VERSION%-bin.zip"

if exist "%GRADLE_BIN%" goto runGradle
if not exist "%GRADLE_ROOT%" mkdir "%GRADLE_ROOT%"
echo Downloading Gradle %GRADLE_VERSION%...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ZIP_FILE%'; Expand-Archive -LiteralPath '%ZIP_FILE%' -DestinationPath '%INSTALL_DIR%' -Force"
if errorlevel 1 exit /b %errorlevel%
del "%ZIP_FILE%"

:runGradle
call "%GRADLE_BIN%" %*
exit /b %errorlevel%

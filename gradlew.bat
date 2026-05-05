@echo off
setlocal EnableDelayedExpansion

set "GRADLE_VERSION=8.1.1"
set "DIST_NAME=gradle-%GRADLE_VERSION%-bin"

if not defined GRADLE_USER_HOME set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
set "BASE_DIR=%GRADLE_USER_HOME%\rehab-wrapper"
set "DIST_DIR=%BASE_DIR%\gradle-%GRADLE_VERSION%"
set "ZIP_FILE=%BASE_DIR%\%DIST_NAME%.zip"
set "GRADLE_BIN=%DIST_DIR%\bin\gradle.bat"

if not defined JAVA_HOME (
  if exist "C:\Program Files\Android\Android Studio\jbr" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
)

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo ERROR: JAVA_HOME is not configured and Android Studio JBR was not found.
  exit /b 1
)

if not exist "%BASE_DIR%" mkdir "%BASE_DIR%"

if not exist "!GRADLE_BIN!" (
  for /f "delims=" %%G in ('dir /b /s "%USERPROFILE%\.gradle\wrapper\dists\gradle-*\*\gradle-*\bin\gradle.bat" 2^>nul') do (
    set "GRADLE_BIN=%%G"
    goto found_gradle
  )
)

:found_gradle

if not exist "!GRADLE_BIN!" (
  echo Gradle %GRADLE_VERSION% not found. Downloading...
  curl.exe -L --retry 3 --connect-timeout 20 -o "%ZIP_FILE%" "https://services.gradle.org/distributions/%DIST_NAME%.zip"
  if errorlevel 1 exit /b 1

  if exist "!DIST_DIR!" rmdir /s /q "!DIST_DIR!"
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ProgressPreference='SilentlyContinue'; Expand-Archive -Path '%ZIP_FILE%' -DestinationPath '%BASE_DIR%' -Force"
  if errorlevel 1 exit /b 1
)

"!GRADLE_BIN!" %*
exit /b %ERRORLEVEL%

@echo off
echo =======================================================
echo  Android 15 / 16KB Page Size Alignment Tool
echo =======================================================

set SDK_BUILD_TOOLS=%LOCALAPPDATA%\Android\Sdk\build-tools\36.0.0
if not exist "%SDK_BUILD_TOOLS%\zipalign.exe" (
    set SDK_BUILD_TOOLS=%LOCALAPPDATA%\Android\Sdk\build-tools\35.0.0
)
if not exist "%SDK_BUILD_TOOLS%\zipalign.exe" (
    set SDK_BUILD_TOOLS=%LOCALAPPDATA%\Android\Sdk\build-tools\34.0.0
)

set ZIPALIGN_EXE=%SDK_BUILD_TOOLS%\zipalign.exe
set APK_INPUT=app\build\outputs\apk\debug\app-debug.apk
set APK_OUTPUT=app\build\outputs\apk\debug\app-debug-16k-aligned.apk

if exist "%APK_INPUT%" (
    echo [1/2] Aligning native libraries and APK entries to 16KB boundaries...
    "%ZIPALIGN_EXE%" -f -P 16 4 "%APK_INPUT%" "%APK_OUTPUT%"

    echo [2/2] Verifying 16KB page alignment...
    "%ZIPALIGN_EXE%" -c -P 16 4 "%APK_OUTPUT%"

    echo.
    echo SUCCESS: 16KB Aligned APK generated at:
    echo %APK_OUTPUT%
) else (
    echo ERROR: Input APK '%APK_INPUT%' not found.
    echo Please run 'gradlew assembleDebug' or 'gradlew zipalignApk16k' first.
)

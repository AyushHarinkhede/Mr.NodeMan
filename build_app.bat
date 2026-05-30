@echo off
echo ===================================================
echo   Mr.NodeMan Android WebView Wrapper Build Script
echo ===================================================
echo.

:: Step 1: Create assets folder and copy index.html
echo [1/4] Preparing assets...
if not exist "app\src\main\assets" (
    mkdir "app\src\main\assets"
)
copy /Y "index.html" "app\src\main\assets\index.html" >nul
if errorlevel 1 (
    echo [ERROR] Failed to copy index.html to assets folder.
    pause
    exit /b 1
)
echo      index.html copied to app assets successfully!

:: Step 2: Download gradle-wrapper.jar
echo [2/4] Bootstrapping Gradle Wrapper...
if not exist "gradle\wrapper" (
    mkdir "gradle\wrapper"
)
if not exist "gradle\wrapper\gradle-wrapper.jar" (
    echo      Downloading gradle-wrapper.jar from official Gradle repository...
    powershell -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://github.com/gradle/gradle/raw/v8.4.0/gradle/wrapper/gradle-wrapper.jar' -OutFile 'gradle/wrapper/gradle-wrapper.jar'"
    if errorlevel 1 (
        echo [ERROR] Failed to download gradle-wrapper.jar. Ensure internet access.
        pause
        exit /b 1
    )
)
echo      Gradle Wrapper ready!

:: Step 3: Compile the Debug APK
echo [3/4] Building Mr.NodeMan Debug APK...
echo      This might take a minute on the first run to fetch Gradle 8.4...
echo.
call gradlew.bat assembleDebug
if errorlevel 1 (
    echo.
    echo [ERROR] Build failed! Please ensure you have Java Development Kit (JDK 17 or higher) installed and set in your PATH.
    pause
    exit /b 1
)

echo.
echo ===================================================
echo   BUILD SUCCESSFUL!
echo ===================================================
echo.
echo Your native Android Debug APK has been generated successfully!
echo.
echo APK Location:
if exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo [SUCCESS] %~dp0app\build\outputs\apk\debug\app-debug.apk
    copy /Y "app\build\outputs\apk\debug\app-debug.apk" "app-debug.apk" >nul
    echo.
    echo [TIP] Copied a shortcut 'app-debug.apk' directly to the root of your workspace!
) else (
    echo [WARNING] APK was compiled but could not be located automatically in the standard outputs folder.
)
echo.
pause

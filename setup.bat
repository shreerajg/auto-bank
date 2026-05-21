@echo off
setlocal enabledelayedexpansion

echo === AutoBank Setup ===
echo Downloading dependencies into lib\...
echo (Run this once on each PC. Needs internet.)
echo.

if not exist lib mkdir lib
if not exist lib\javafx mkdir lib\javafx

set BASE=https://repo1.maven.org/maven2

:: Regular JARs
call :get %BASE%/com/mysql/mysql-connector-j/9.0.0/mysql-connector-j-9.0.0.jar              lib\mysql-connector-j-9.0.0.jar
call :get %BASE%/com/zaxxer/HikariCP/5.1.0/HikariCP-5.1.0.jar                              lib\HikariCP-5.1.0.jar
call :get %BASE%/org/mindrot/jbcrypt/0.4/jbcrypt-0.4.jar                                   lib\jbcrypt-0.4.jar
call :get %BASE%/com/google/code/gson/gson/2.11.0/gson-2.11.0.jar                           lib\gson-2.11.0.jar
call :get %BASE%/org/slf4j/slf4j-api/2.0.13/slf4j-api-2.0.13.jar                           lib\slf4j-api-2.0.13.jar
call :get %BASE%/ch/qos/logback/logback-classic/1.5.6/logback-classic-1.5.6.jar             lib\logback-classic-1.5.6.jar
call :get %BASE%/ch/qos/logback/logback-core/1.5.6/logback-core-1.5.6.jar                  lib\logback-core-1.5.6.jar

:: JavaFX 22 Windows JARs (contain native DLLs for the current platform)
call :get %BASE%/org/openjfx/javafx-base/22.0.2/javafx-base-22.0.2-win.jar                 lib\javafx\javafx-base-22.0.2-win.jar
call :get %BASE%/org/openjfx/javafx-graphics/22.0.2/javafx-graphics-22.0.2-win.jar         lib\javafx\javafx-graphics-22.0.2-win.jar
call :get %BASE%/org/openjfx/javafx-controls/22.0.2/javafx-controls-22.0.2-win.jar         lib\javafx\javafx-controls-22.0.2-win.jar
call :get %BASE%/org/openjfx/javafx-fxml/22.0.2/javafx-fxml-22.0.2-win.jar                 lib\javafx\javafx-fxml-22.0.2-win.jar

echo.
echo Done! Run build.bat next.
goto :eof

:get
set URL=%~1
set DEST=%~2
if exist "%DEST%" (
    echo [skip] %DEST%
) else (
    echo [get]  %DEST%
    powershell -NoProfile -Command "Invoke-WebRequest -Uri '%URL%' -OutFile '%DEST%'" 2>nul
    if not exist "%DEST%" echo [FAIL] %DEST% -- check internet / proxy
)
goto :eof

@echo off
setlocal

echo === AutoBank Build ===

if not exist lib\javafx\javafx-controls-22.0.2-win.jar (
    echo ERROR: Dependencies not found. Run setup.bat first.
    exit /b 1
)

if not exist bin mkdir bin

:: Write all .java source paths to a temp file (avoids command-line length limit)
dir /b /s /a-d src\main\java\*.java > sources.txt

echo Compiling...
javac --module-path lib\javafx --add-modules javafx.controls,javafx.fxml -cp "bin;lib\*" -d bin @sources.txt
del sources.txt
if errorlevel 1 (
    echo.
    echo BUILD FAILED
    exit /b 1
)

:: Copy resources (FXML, CSS, lang files, etc.)
xcopy /E /Y /Q src\main\resources\* bin\ >nul

echo BUILD OK

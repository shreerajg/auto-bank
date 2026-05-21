@echo off
setlocal

if not exist bin\com\autobank\Launcher.class (
    echo Not built yet. Run build.bat first.
    exit /b 1
)

java --module-path lib\javafx --add-modules javafx.controls,javafx.fxml --enable-native-access=javafx.graphics -cp "bin;lib\*" com.autobank.Launcher

@echo off
rem Lance Gantt Simulator sous Windows (double-clic).
rem Java 17 et JavaFX sont installes automatiquement si necessaire.
setlocal
cd /d "%~dp0.."
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1"
if errorlevel 1 (
    echo.
    echo Le demarrage a echoue. Consultez les messages ci-dessus.
    pause
)
endlocal

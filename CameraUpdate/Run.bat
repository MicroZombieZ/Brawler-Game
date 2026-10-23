@echo off
setlocal

cd /d "%~dp0"

where java >nul 2>nul
if errorlevel 1 (
    echo Java was not found on PATH.
    echo Install JDK 11 or newer, then run this file again.
    pause
    exit /b 1
)

javac CubeMovement.java CombatSystem.java DevConsole.java LobbyWorld.java LobbyNetwork.java LobbyPanel.java GameEffect.java ParticleSystem.java
if errorlevel 1 (
    echo Compilation failed. Make sure JDK 11 or newer is installed.
    pause
    exit /b 1
)

java CubeMovement
if errorlevel 1 (
    echo.
    echo The program could not be started. Make sure a JDK 11 or newer is installed.
    pause
    exit /b 1
)

endlocal

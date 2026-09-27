@echo off
setlocal
cd /d "%~dp0" || (
    echo Cannot open the game folder.
    pause
    exit /b 1
)

:menu
echo.
echo ===== HaiKyuu Launcher =====
echo 1. Local two-player
echo 2. Practice
echo 3. Host server only
echo 4. Join server
echo 5. Exit
choice /c 12345 /n /m "Select [1-5]: "
if errorlevel 5 goto end
if errorlevel 4 goto join
if errorlevel 3 goto server
if errorlevel 2 goto practice
goto local

:local
set "game_mode="
goto run

:server
set "game_mode=server"
echo The server runs in this window. Open this file again and choose Join server to play.
goto run

:join
set "game_mode=join"
echo Enter the server IPv4 address in the game dialog.
goto run

:practice
set "game_mode=practice"
goto run

:run
call :compile
if errorlevel 1 (
    pause
    goto menu
)
if "%game_mode%"=="" (
    "%jdk_bin%\java.exe" -cp "build" Main
) else (
    "%jdk_bin%\java.exe" -cp "build" Main %game_mode%
)
if errorlevel 1 (
    echo The game failed to start. Check the error above.
    pause
)
goto menu

:compile
call :find_jdk
if errorlevel 1 (
    echo JDK not found. Install a JDK or set JAVA_HOME or PATH to its bin folder.
    exit /b 1
)
if not exist "build" mkdir "build"
if errorlevel 1 (
    echo Cannot create the build folder.
    exit /b 1
)
echo Compiling...
"%jdk_bin%\javac.exe" -encoding UTF-8 -d "build" Main.java
if errorlevel 1 (
    echo Compilation failed. Check the error above.
    exit /b 1
)
exit /b 0

:find_jdk
set "jdk_bin="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" set "jdk_bin=%JAVA_HOME%\bin"
if not defined jdk_bin (
    for /f "delims=" %%J in ('where javac 2^>nul') do if not defined jdk_bin set "jdk_bin=%%~dpJ"
)
if not defined jdk_bin (
    for /d %%J in ("%ProgramFiles%\Java\*" "%ProgramFiles%\Eclipse Adoptium\*" "%ProgramFiles%\Microsoft\*" "%ProgramFiles%\Amazon Corretto\*") do (
        if not defined jdk_bin if exist "%%~fJ\bin\javac.exe" set "jdk_bin=%%~fJ\bin"
    )
)
if not defined jdk_bin exit /b 1
if not exist "%jdk_bin%\java.exe" exit /b 1
exit /b 0

:end
exit /b 0

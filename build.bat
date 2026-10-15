@echo off
setlocal
cd /d "%~dp0"

if not exist out mkdir out
javac -encoding UTF-8 -cp "lib\*" -sourcepath src -d out src\com\example\crud\Main.java
if errorlevel 1 (
    echo.
    echo BUILD FAILED
    exit /b 1
)

echo BUILD SUCCESS

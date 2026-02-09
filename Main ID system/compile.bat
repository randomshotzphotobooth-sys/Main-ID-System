@echo off
echo ========================================
echo RFID Attendance System
echo ========================================

REM Check if Java is installed
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java is not installed or not in PATH
    pause
    exit /b 1
)
echo [OK] Java found

REM Check if MySQL connector exists
if not exist "lib\mysql-connector-j-8.0.33.jar" (
    echo [ERROR] MySQL connector not found in lib\
    echo Please download mysql-connector-j-8.0.33.jar and place it in lib\
    pause
    exit /b 1
)
echo [OK] MySQL connector found

REM Check if JavaMail exists
if not exist "lib\javax.mail-1.6.2.jar" (
    echo [WARNING] JavaMail library not found!
    echo.
    echo Please download javax.mail-1.6.2.jar from:
    echo https://repo1.maven.org/maven2/com/sun/mail/javax.mail/1.6.2/javax.mail-1.6.2.jar
    echo.
    echo Place it in the lib\ folder and try again.
    echo.
    pause
    exit /b 1
)
echo [OK] JavaMail library found

REM Set classpath with both JARs
set CLASSPATH=lib\mysql-connector-j-8.0.33.jar;lib\javax.mail-1.6.2.jar;.

REM Compile
echo Compiling...
javac -cp "%CLASSPATH%" RFIDAttendanceSystem.java 2> error.log

if %errorlevel% equ 0 (
    echo [OK] Compilation successful!
    del error.log 2>nul
) else (
    echo [ERROR] Compilation failed! Check error.log
    type error.log
    pause
    exit /b 1
)

echo ========================================
echo Ready to run! Use run.bat to start
echo ========================================
pause

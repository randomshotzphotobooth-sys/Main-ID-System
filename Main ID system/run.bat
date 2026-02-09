@echo off
echo ========================================
echo Starting RFID Attendance System
echo ========================================

REM Set classpath with both JARs
set CLASSPATH=lib\mysql-connector-j-8.0.33.jar;lib\javax.mail-1.6.2.jar;.

REM Run the application
java -cp "%CLASSPATH%" RFIDAttendanceSystem

if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Application crashed or closed with error
    pause
)

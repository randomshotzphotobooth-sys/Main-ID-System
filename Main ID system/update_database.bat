@echo off
echo ========================================
echo Database Update - Add Parent Email
echo ========================================
echo.

set DB_USER=root
set DB_PASS=krissahermosa2125
set DB_NAME=rfid_attendance

echo Adding parent_email column to users table...
echo.

mysql -u %DB_USER% -p%DB_PASS% -e "USE %DB_NAME%; ALTER TABLE users ADD COLUMN IF NOT EXISTS parent_email VARCHAR(100);"

if %errorlevel% equ 0 (
    echo [OK] Column added successfully!
    echo.
    echo Verifying changes...
    mysql -u %DB_USER% -p%DB_PASS% -e "USE %DB_NAME%; DESCRIBE users;"
    echo.
    echo ========================================
    echo Database updated successfully!
    echo You can now run the application.
    echo ========================================
) else (
    echo [ERROR] Failed to add column!
    echo.
    echo Please run the SQL manually:
    echo   1. Open MySQL Workbench
    echo   2. Run: ALTER TABLE users ADD COLUMN parent_email VARCHAR(100);
    echo.
)

echo.
pause

@echo off
echo ========================================
echo JavaMail Library Check
echo ========================================
echo.

if exist "lib\javax.mail-1.6.2.jar" (
    echo [OK] JavaMail library found!
    echo Location: lib\javax.mail-1.6.2.jar
    echo.
    echo You can now compile the project.
    echo Run: compile.bat
) else (
    echo [ERROR] JavaMail library NOT found!
    echo.
    echo ========================================
    echo DOWNLOAD REQUIRED
    echo ========================================
    echo.
    echo You need to download the JavaMail library first.
    echo.
    echo Method 1 - Use PowerShell (EASIEST):
    echo    1. Run: download_javamail.ps1
    echo.
    echo Method 2 - Manual Download:
    echo    1. Open this link in your browser:
    echo       https://repo1.maven.org/maven2/com/sun/mail/javax.mail/1.6.2/javax.mail-1.6.2.jar
    echo    2. Save the file to: lib\javax.mail-1.6.2.jar
    echo.
    echo Method 3 - Use Browser:
    echo    1. Copy this link: https://repo1.maven.org/maven2/com/sun/mail/javax.mail/1.6.2/javax.mail-1.6.2.jar
    echo    2. Paste in browser and download
    echo    3. Move to lib\ folder
    echo.
    echo After downloading, run compile.bat again.
    echo ========================================
)

echo.
pause

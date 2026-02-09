# PowerShell Script to Download JavaMail Library
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "JavaMail Library Downloader" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Create lib directory if it doesn't exist
if (!(Test-Path "lib")) {
    Write-Host "Creating lib directory..." -ForegroundColor Yellow
    New-Item -ItemType Directory -Path "lib" | Out-Null
}

# Download JavaMail JAR
$url = "https://repo1.maven.org/maven2/com/sun/mail/javax.mail/1.6.2/javax.mail-1.6.2.jar"
$output = "lib\javax.mail-1.6.2.jar"

Write-Host "Downloading javax.mail-1.6.2.jar..." -ForegroundColor Yellow
Write-Host "From: $url" -ForegroundColor Gray
Write-Host "To: $output" -ForegroundColor Gray
Write-Host ""

try {
    # Try using WebClient first (faster)
    $webClient = New-Object System.Net.WebClient
    $webClient.DownloadFile($url, $output)
    Write-Host "[OK] Download completed successfully!" -ForegroundColor Green
}
catch {
    Write-Host "[ERROR] WebClient failed, trying Invoke-WebRequest..." -ForegroundColor Red
    try {
        Invoke-WebRequest -Uri $url -OutFile $output -UseBasicParsing
        Write-Host "[OK] Download completed successfully!" -ForegroundColor Green
    }
    catch {
        Write-Host "[ERROR] Download failed: $_" -ForegroundColor Red
        Write-Host ""
        Write-Host "Please download manually from:" -ForegroundColor Yellow
        Write-Host $url -ForegroundColor Cyan
        Write-Host ""
        Write-Host "And save it to: $output" -ForegroundColor Yellow
        Read-Host "Press Enter to exit"
        exit 1
    }
}

# Verify file size
if (Test-Path $output) {
    $fileSize = (Get-Item $output).Length
    $fileSizeKB = [math]::Round($fileSize / 1024, 2)
    
    if ($fileSize -gt 500000) {  # Should be around 700KB
        Write-Host ""
        Write-Host "File size: $fileSizeKB KB" -ForegroundColor Green
        Write-Host "[OK] File downloaded successfully!" -ForegroundColor Green
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host "Next Steps:" -ForegroundColor Cyan
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host "1. Run compile.bat to compile the project" -ForegroundColor White
        Write-Host "2. Run run.bat to start the application" -ForegroundColor White
        Write-Host ""
    }
    else {
        Write-Host ""
        Write-Host "[WARNING] File size is too small ($fileSizeKB KB)" -ForegroundColor Yellow
        Write-Host "The download may have failed. Expected size: ~700 KB" -ForegroundColor Yellow
        Write-Host ""
        Write-Host "Please download manually from:" -ForegroundColor Yellow
        Write-Host $url -ForegroundColor Cyan
    }
}

Write-Host ""
Read-Host "Press Enter to exit"

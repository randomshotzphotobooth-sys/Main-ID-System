#!/bin/bash

###################################################
# RFID Attendance System - Linux/Mac Launcher
###################################################

echo "========================================"
echo "RFID Attendance System"
echo "========================================"
echo ""

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "[ERROR] Java is not installed or not in PATH!"
    echo "Please install Java JDK:"
    echo "  Ubuntu/Debian: sudo apt install default-jdk"
    echo "  Mac: brew install openjdk"
    exit 1
fi

echo "[OK] Java found: $(java -version 2>&1 | head -n 1)"
echo ""

# Check if MySQL connector exists
if [ ! -f "mysql-connector-java-8.0.33.jar" ] && [ ! -f mysql-connector-j-*.jar ]; then
    echo "[ERROR] MySQL JDBC Connector not found!"
    echo "Please download mysql-connector-java-8.0.33.jar"
    echo "Download from: https://dev.mysql.com/downloads/connector/j/"
    echo "Place it in the same folder as this script."
    exit 1
fi

echo "[OK] MySQL connector found"
echo ""

# Compile
echo "Compiling..."
javac -cp ".:mysql-connector-java-8.0.33.jar" RFIDAttendanceSystem.java 2>error.log

if [ $? -ne 0 ]; then
    echo "[ERROR] Compilation failed! Check error.log"
    cat error.log
    exit 1
fi

echo "[OK] Compilation successful"
echo ""

# Run
echo "Starting RFID Attendance System..."
echo ""
echo "----------------------------------------"
echo "IMPORTANT REMINDERS:"
echo "----------------------------------------"
echo "1. Make sure MySQL Server is running"
echo "2. Check database credentials in the code"
echo "3. Press Ctrl+F2 to register Admin card"
echo "4. Focus must be on the window for RFID to work"
echo "----------------------------------------"
echo ""

java -cp ".:mysql-connector-java-8.0.33.jar" RFIDAttendanceSystem

# Clean up
if [ -f "error.log" ]; then
    rm error.log
fi

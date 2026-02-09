# 📥 Download Links & Quick Start

## Required Downloads

### 1. Java JDK (Required)
**Choose ONE based on your OS:**

- **Windows/Mac/Linux:** 
  - Oracle JDK: https://www.oracle.com/java/technologies/downloads/
  - OpenJDK: https://adoptium.net/

**Installation:**
- Windows: Run installer → Set JAVA_HOME → Add to PATH
- Mac: `brew install openjdk`
- Ubuntu/Debian: `sudo apt install default-jdk`
- Verify: `java -version`

---

### 2. MySQL Server (Required)
**Choose ONE based on your OS:**

- **Windows:** https://dev.mysql.com/downloads/installer/
  - Download "MySQL Installer for Windows"
  - Choose "Developer Default" during installation
  - Set root password during setup
  - Start as Windows Service

- **Mac:** 
  ```bash
  brew install mysql
  brew services start mysql
  mysql_secure_installation
  ```

- **Ubuntu/Debian:**
  ```bash
  sudo apt update
  sudo apt install mysql-server
  sudo systemctl start mysql
  sudo mysql_secure_installation
  ```

**Verify:** `mysql --version`

---

### 3. MySQL JDBC Connector (Required)
**Download:** https://dev.mysql.com/downloads/connector/j/

- Select "Platform Independent"
- Download the ZIP file
- Extract and find: `mysql-connector-java-8.0.33.jar` (or similar version)
- Place in your project folder

**Direct Download Link (if available):**
https://dev.mysql.com/get/Downloads/Connector-J/mysql-connector-j-8.0.33.zip

---

## 📂 Project Folder Structure

Create a folder with this structure:

```
RFIDAttendanceProject/
│
├── RFIDAttendanceSystem.java          ← The main program
├── mysql-connector-java-8.0.33.jar    ← JDBC driver
├── run.bat                             ← Windows launcher
├── run.sh                              ← Linux/Mac launcher
├── SETUP_GUIDE.md                      ← Detailed guide
│
└── photos/                             ← Auto-created on first run
```

---

## ⚡ Quick Start (Windows)

1. **Install Java JDK**
   - Download from link above
   - Run installer
   - Open Command Prompt and verify: `java -version`

2. **Install MySQL Server**
   - Download MySQL Installer
   - Run it, choose "Developer Default"
   - Set root password (remember it!)
   - Finish installation

3. **Download MySQL Connector**
   - Download ZIP from link above
   - Extract and copy `.jar` file to project folder

4. **Edit Database Password**
   - Open `RFIDAttendanceSystem.java`
   - Find line 27: `private static final String DB_PASSWORD = "";`
   - Change to your MySQL password: `private static final String DB_PASSWORD = "your_password";`

5. **Run the Program**
   - Double-click `run.bat`
   - OR open Command Prompt in project folder:
     ```bash
     javac -cp ".;mysql-connector-java-8.0.33.jar" RFIDAttendanceSystem.java
     java -cp ".;mysql-connector-java-8.0.33.jar" RFIDAttendanceSystem
     ```

6. **Register Admin Card**
   - Press `Ctrl + F2`
   - Tap your RFID card
   - Done! You're now admin

---

## ⚡ Quick Start (Mac/Linux)

1. **Install Java JDK**
   ```bash
   # Mac
   brew install openjdk
   
   # Ubuntu/Debian
   sudo apt install default-jdk
   ```

2. **Install MySQL Server**
   ```bash
   # Mac
   brew install mysql
   brew services start mysql
   mysql_secure_installation
   
   # Ubuntu/Debian
   sudo apt install mysql-server
   sudo systemctl start mysql
   sudo mysql_secure_installation
   ```

3. **Download MySQL Connector**
   - Download ZIP from link above
   - Extract and copy `.jar` file to project folder

4. **Edit Database Password**
   - Open `RFIDAttendanceSystem.java`
   - Find line 27: `private static final String DB_PASSWORD = "";`
   - Change to your MySQL password

5. **Run the Program**
   ```bash
   chmod +x run.sh
   ./run.sh
   ```
   
   OR compile manually:
   ```bash
   javac -cp ".:mysql-connector-java-8.0.33.jar" RFIDAttendanceSystem.java
   java -cp ".:mysql-connector-java-8.0.33.jar" RFIDAttendanceSystem
   ```

6. **Register Admin Card**
   - Press `Ctrl + F2`
   - Tap your RFID card
   - Done! You're now admin

---

## 🎯 First Usage

### 1️⃣ Register Your Admin Card
- Launch the program
- Press `Ctrl + F2`
- Tap your RFID card
- Message: "Admin card registered successfully!"

### 2️⃣ Add Users
- Tap your admin card (opens Admin Panel)
- Click "➕ Add User"
- Fill in:
  - **Full Name** (required)
  - **RFID UID** (tap card or type manually)
  - **Role:** Student or Teacher
  - If Student: **Grade** and **Section** (required)
  - **Photo** (optional)
- Click "💾 Save"

### 3️⃣ Test Attendance
- Close Admin Panel
- Tap a student/teacher card
- First tap = TIME-IN
- Second tap = TIME-OUT
- Profile appears with attendance info

---

## 🔍 Troubleshooting

### ❌ "Database Error: Access denied"
- Check MySQL password in code (line 27)
- Make sure MySQL is running:
  - Windows: Services → MySQL → Start
  - Mac: `brew services start mysql`
  - Linux: `sudo systemctl start mysql`

### ❌ "ClassNotFoundException: com.mysql.cj.jdbc.Driver"
- MySQL connector JAR not found
- Make sure `mysql-connector-java-8.0.33.jar` is in project folder
- Check classpath in compile/run command

### ❌ RFID Scanner Not Working
- Scanner must be USB keyboard type
- Test in Notepad: tap card → should type UID + Enter
- Make sure program window is focused
- Scanner needs to output UID + ENTER key

### ❌ "Communications link failure"
- MySQL server not running
- Wrong port (default: 3306)
- Firewall blocking connection

---

## 📞 Need Help?

1. Read `SETUP_GUIDE.md` for detailed instructions
2. Check error messages in console
3. Verify all requirements are installed:
   ```bash
   java -version
   javac -version
   mysql --version
   ```
4. Make sure MySQL server is running
5. Check database credentials in code

---

## 🎓 System Features

✅ **Attendance Tracking**
- Automatic TIME-IN/TIME-OUT
- Daily attendance records
- Total days present counter

✅ **User Management**
- Add/Remove users
- Student/Teacher/Admin roles
- Profile photos support
- Grade & section for students

✅ **Admin Panel**
- Full user management
- Search functionality
- Soft/Hard delete options

✅ **Security**
- Masked RFID UIDs on display
- Admin-only access control
- Inactive user blocking

✅ **Easy RFID Scanning**
- Seamless tap (no clicking)
- Auto-focus on hidden field
- Beep confirmation

---

## 📊 Database Tables (Auto-Created)

The program creates these tables automatically on first run:

1. **users** - User information
2. **students** - Student-specific data (grade, section)
3. **attendance** - Daily IN/OUT records

You can query the database directly:
```sql
mysql -u root -p
USE rfid_attendance;
SELECT * FROM users;
SELECT * FROM attendance WHERE date = CURDATE();
```

---

**🚀 Ready to go! Follow the Quick Start for your OS above.**

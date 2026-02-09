# RFID Attendance System - Complete Setup Guide

## 📋 System Requirements

### Software Requirements:
1. **Java Development Kit (JDK) 8 or higher**
2. **MySQL Server 5.7 or higher**
3. **MySQL JDBC Connector** (mysql-connector-java)
4. **RFID Scanner** (USB keyboard-type scanner)

### Hardware Requirements:
- Computer with USB port
- RFID Scanner (keyboard wedge type recommended)
- RFID cards/tags (125kHz or 13.56MHz depending on your scanner)

---

## 🚀 Installation Steps

### Step 1: Install Java JDK

**Windows:**
1. Download JDK from: https://www.oracle.com/java/technologies/downloads/
2. Run installer
3. Set JAVA_HOME environment variable:
   - Right-click "This PC" → Properties → Advanced System Settings
   - Environment Variables → New (System Variable)
   - Variable name: `JAVA_HOME`
   - Variable value: `C:\Program Files\Java\jdk-XX` (your JDK path)
4. Add to PATH: `%JAVA_HOME%\bin`

**Verify installation:**
```bash
java -version
javac -version
```

---

### Step 2: Install MySQL Server

**Windows:**
1. Download MySQL Installer: https://dev.mysql.com/downloads/installer/
2. Run installer, choose "Developer Default"
3. During setup:
   - Set root password (remember this!)
   - Default port: 3306
   - Start MySQL as Windows Service

**macOS:**
```bash
brew install mysql
brew services start mysql
mysql_secure_installation
```

**Linux (Ubuntu/Debian):**
```bash
sudo apt update
sudo apt install mysql-server
sudo systemctl start mysql
sudo mysql_secure_installation
```

**Verify installation:**
```bash
mysql --version
```

---

### Step 3: Download MySQL JDBC Connector

**Option A: Direct Download**
1. Go to: https://dev.mysql.com/downloads/connector/j/
2. Download "Platform Independent" ZIP
3. Extract and find `mysql-connector-java-X.X.XX.jar`

**Option B: Maven (if you use it)**
Add to pom.xml:
```xml
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <version>8.0.33</version>
</dependency>
```

---

### Step 4: Setup MySQL Database

1. **Login to MySQL:**
```bash
mysql -u root -p
```
Enter your root password

2. **Create database user (optional but recommended):**
```sql
CREATE USER 'rfid_user'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON rfid_attendance.* TO 'rfid_user'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

3. **The application will auto-create the database and tables on first run!**

---

### Step 5: Configure the Application

**Edit the Java file if needed:**

Open `RFIDAttendanceSystem.java` and modify these lines (around line 25-27):

```java
private static final String DB_URL = "jdbc:mysql://localhost:3306/rfid_attendance";
private static final String DB_USER = "root"; // Change to your MySQL username
private static final String DB_PASSWORD = ""; // Change to your MySQL password
```

**Example if you created rfid_user:**
```java
private static final String DB_USER = "rfid_user";
private static final String DB_PASSWORD = "your_password";
```

---

## 📁 Project Directory Structure

Create this folder structure:

```
RFIDAttendanceProject/
│
├── RFIDAttendanceSystem.java    (your main file)
├── mysql-connector-java-8.0.33.jar    (JDBC driver)
│
└── photos/    (will be auto-created)
    └── (user photos will be stored here)
```

---

## 🔨 Compilation and Running

### Method 1: Command Line (Recommended for beginners)

**1. Navigate to project folder:**
```bash
cd C:\path\to\RFIDAttendanceProject
```

**2. Compile the program:**
```bash
javac -cp ".;mysql-connector-java-8.0.33.jar" RFIDAttendanceSystem.java
```

**Linux/Mac:**
```bash
javac -cp ".:mysql-connector-java-8.0.33.jar" RFIDAttendanceSystem.java
```

**3. Run the program:**
```bash
java -cp ".;mysql-connector-java-8.0.33.jar" RFIDAttendanceSystem
```

**Linux/Mac:**
```bash
java -cp ".:mysql-connector-java-8.0.33.jar" RFIDAttendanceSystem
```

---

### Method 2: Using an IDE (Eclipse/IntelliJ/NetBeans)

**IntelliJ IDEA:**
1. File → New → Project from Existing Sources
2. Select your folder
3. Right-click project → Open Module Settings → Libraries
4. Add JAR: Click + → Java → Select `mysql-connector-java-8.0.33.jar`
5. Right-click `RFIDAttendanceSystem.java` → Run

**Eclipse:**
1. File → New → Java Project
2. Copy your `.java` file into `src` folder
3. Right-click project → Build Path → Add External JARs
4. Select `mysql-connector-java-8.0.33.jar`
5. Right-click file → Run As → Java Application

**NetBeans:**
1. File → New Project → Java Application
2. Copy `.java` file into Source Packages
3. Right-click Libraries → Add JAR/Folder
4. Select `mysql-connector-java-8.0.33.jar`
5. Right-click file → Run File

---

## 🎯 First Time Setup

### 1. Start the Application
- The database and tables will be created automatically
- You should see: "Database initialized successfully!"

### 2. Register Admin Card
- Press `Ctrl + F2`
- Message appears: "ADMIN MODE: Tap your Admin RFID card now..."
- Tap your RFID card on the scanner
- Success message: "Admin card registered successfully!"

### 3. Access Admin Panel
- Tap the same admin card again
- Admin Panel opens

### 4. Add Users
- Click "➕ Add User" button
- Fill in details:
  - Full Name (required)
  - RFID UID (required) - tap card or type manually
  - Role: Student or Teacher
  - If Student: Grade and Section (required)
  - Photo (optional)
- Click "💾 Save"

### 5. Test Attendance
- Close Admin Panel
- Tap a student/teacher card
- Profile should appear with TIME-IN
- Tap again for TIME-OUT

---

## 🔧 Troubleshooting

### Problem: "Database Error: Access denied for user"
**Solution:**
- Check MySQL username and password in the code
- Make sure MySQL server is running:
  ```bash
  # Windows
  net start MySQL
  
  # Linux
  sudo systemctl start mysql
  
  # Mac
  brew services start mysql
  ```

### Problem: "ClassNotFoundException: com.mysql.cj.jdbc.Driver"
**Solution:**
- Make sure `mysql-connector-java-X.X.XX.jar` is in your project folder
- Include it in classpath when compiling/running
- For IDE: Add as library (see IDE setup above)

### Problem: "RFID NOT REGISTERED"
**Solution:**
- Make sure the card is registered in Admin Panel first
- Check if status is "active" in database

### Problem: RFID scanner not working
**Solution:**
- Check USB connection
- Scanner should work like a keyboard (type in notepad to test)
- The scanner must output UID + ENTER key
- Configure scanner to add suffix "Enter" if needed

### Problem: "Communications link failure"
**Solution:**
- MySQL server not running
- Wrong port (default is 3306)
- Firewall blocking connection

---

## 📊 Database Schema Reference

The application creates these tables automatically:

### USERS table
```sql
user_id (INT, PK, AUTO_INCREMENT)
rfid_uid (VARCHAR(50), UNIQUE, NOT NULL)
full_name (VARCHAR(100), NOT NULL)
role (VARCHAR(20), NOT NULL)
status (VARCHAR(10), DEFAULT 'active')
photo_path (VARCHAR(255))
```

### STUDENTS table
```sql
user_id (INT, PK, FK → users)
grade (VARCHAR(20))
section (VARCHAR(20))
```

### ATTENDANCE table
```sql
id (INT, PK, AUTO_INCREMENT)
user_id (INT, FK → users)
date (DATE, NOT NULL)
time_in (TIME)
time_out (TIME)
```

---

## 🎮 Usage Guide

### For Students/Teachers:
1. **Time IN:** Tap card (morning arrival)
2. **Time OUT:** Tap card again (leaving)
3. View your profile with total attendance days

### For Admins:
1. **Access Admin Panel:** Tap admin card
2. **Add User:** Click ➕ Add User → Fill form → Save
3. **Remove User:** 
   - Select user in table
   - Click ❌ Remove User
   - Choose: Soft delete (inactive) or Hard delete (permanent)
4. **Search:** Type name or UID in search box

### Keyboard Shortcuts:
- `Ctrl + F2` = Register new admin card

---

## 🔐 Security Notes

1. **RFID UIDs are masked** on display (shows only last 4 digits)
2. Admin cards have special privileges (no attendance logging)
3. Inactive users cannot use their cards
4. Database passwords should be changed from default

---

## 📱 RFID Scanner Configuration

### Recommended Scanner Types:
- USB Keyboard Wedge RFID readers (125kHz or 13.56MHz)
- Examples: 
  - HID ProxPoint Plus
  - ZKTeco FR1200
  - Generic USB EM4100 readers

### Scanner Setup:
1. Plug scanner into USB port
2. No drivers needed (works as keyboard)
3. Test by tapping card → should output UID + Enter
4. Configure scanner suffix to "Enter" if needed (consult manual)

---

## 🆘 Support

If you encounter issues:

1. Check MySQL is running: `mysql --version`
2. Check Java is installed: `java -version`
3. Verify JDBC connector is in classpath
4. Check database credentials in code
5. Review console output for error messages

---

## 📝 Customization Tips

### Change database connection:
Edit lines 25-27 in `RFIDAttendanceSystem.java`

### Change photo directory:
Edit line 32: `private static final String PHOTO_DIR = "photos/";`

### Add more roles:
Modify database and role combo box in `AddUserForm`

### Export attendance reports:
You can query the database directly:
```sql
SELECT u.full_name, a.date, a.time_in, a.time_out 
FROM attendance a 
JOIN users u ON a.user_id = u.user_id 
WHERE a.date BETWEEN '2024-01-01' AND '2024-12-31';
```

---

## ✅ Quick Start Checklist

- [ ] Java JDK installed and in PATH
- [ ] MySQL Server installed and running
- [ ] MySQL root password set
- [ ] JDBC connector downloaded
- [ ] Project folder created
- [ ] Files copied to folder
- [ ] Database credentials updated in code
- [ ] Compiled successfully
- [ ] Application running
- [ ] Admin card registered (Ctrl+F2)
- [ ] Test user added
- [ ] Attendance working

---

**🎉 You're all set! Your RFID Attendance System is ready to use!**

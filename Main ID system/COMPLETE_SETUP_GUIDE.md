# Complete Setup Guide - RFID Attendance System

## 📋 Required Files Checklist

Download and place these files in your project folder:

### ✅ Main Files:
- [ ] `RFIDAttendanceSystem.java` (the main code)
- [ ] `compile.bat` (to compile the code)
- [ ] `run.bat` (to run the application)

### ✅ Helper Files:
- [ ] `download_javamail.ps1` (downloads JavaMail library)
- [ ] `check_libraries.bat` (verifies libraries)
- [ ] `update_database.bat` (adds parent_email column to database)

### ✅ Library Files (in `lib` folder):
- [ ] `lib\mysql-connector-j-8.0.33.jar` (you should already have this)
- [ ] `lib\javax.mail-1.6.2.jar` (download using PowerShell script)

---

## 🚀 Complete Setup Steps

### Step 1: Create Project Structure

Create this folder structure:
```
your-project/
├── RFIDAttendanceSystem.java
├── compile.bat
├── run.bat
├── download_javamail.ps1
├── check_libraries.bat
├── update_database.bat
├── lib/
│   ├── mysql-connector-j-8.0.33.jar
│   └── javax.mail-1.6.2.jar (will be downloaded)
└── photos/ (will be created automatically)
```

### Step 2: Download JavaMail Library

**Option A - PowerShell (Easiest):**
1. Right-click `download_javamail.ps1`
2. Select "Run with PowerShell"

**Option B - Manual Download:**
1. Open: https://repo1.maven.org/maven2/com/sun/mail/javax.mail/1.6.2/javax.mail-1.6.2.jar
2. Save to `lib\javax.mail-1.6.2.jar`

### Step 3: Verify Libraries

Run:
```batch
check_libraries.bat
```

You should see:
```
[OK] JavaMail library found!
```

### Step 4: Update Database (if upgrading from old version)

Run:
```batch
update_database.bat
```

This adds the `parent_email` column to your database.

### Step 5: Configure Email Settings

Open `RFIDAttendanceSystem.java` and update these lines (around line 24-25):

```java
private static final String EMAIL_FROM = "your-school-email@gmail.com";
private static final String EMAIL_PASSWORD = "your-16-char-app-password";
```

**To get Gmail App Password:**
1. Go to: https://myaccount.google.com/security
2. Enable 2-Step Verification
3. Go to "App passwords"
4. Generate new password for "Mail"
5. Copy the 16-character password

### Step 6: Compile

Run:
```batch
compile.bat
```

You should see:
```
[OK] Compilation successful!
```

### Step 7: Run

Run:
```batch
run.bat
```

The application window should open!

---

## 🔧 Troubleshooting

### "compile.bat not found"
- Download the `compile.bat` file I provided
- Place it in your project root folder (same folder as RFIDAttendanceSystem.java)

### "JavaMail library not found"
- Run `download_javamail.ps1` OR
- Manually download from the link above
- Verify with `check_libraries.bat`

### "Column 'parent_email' not found"
- Run `update_database.bat` OR
- Run this SQL manually:
  ```sql
  USE rfid_attendance;
  ALTER TABLE users ADD COLUMN parent_email VARCHAR(100);
  ```

### Compilation errors about javax.mail
- JavaMail library is missing from `lib` folder
- Re-run `download_javamail.ps1`

---

## 📝 Quick Reference Commands

```batch
# Check if libraries are installed
check_libraries.bat

# Download JavaMail library (PowerShell)
download_javamail.ps1

# Update database schema
update_database.bat

# Compile the code
compile.bat

# Run the application
run.bat
```

---

## ✅ Pre-Flight Checklist

Before running the application:

- [ ] MySQL is running
- [ ] Both JAR files are in `lib` folder
- [ ] Database has `parent_email` column
- [ ] Email credentials configured (optional, for email notifications)
- [ ] Code compiled successfully
- [ ] RFID reader connected

---

## 🎯 What Each File Does

| File | Purpose |
|------|---------|
| `RFIDAttendanceSystem.java` | Main application code |
| `compile.bat` | Compiles Java code to .class files |
| `run.bat` | Runs the compiled application |
| `download_javamail.ps1` | Downloads JavaMail library |
| `check_libraries.bat` | Verifies required libraries |
| `update_database.bat` | Updates database schema |
| `lib\mysql-connector-j-8.0.33.jar` | MySQL driver |
| `lib\javax.mail-1.6.2.jar` | Email functionality |

---

## 🆘 Need Help?

If you're stuck:

1. **Run diagnostics:**
   ```batch
   check_libraries.bat
   ```

2. **Check error logs:**
   - Compilation errors: Look at console output
   - Runtime errors: Check the error dialogs

3. **Common issues:**
   - Missing libraries → Run `download_javamail.ps1`
   - Database errors → Run `update_database.bat`
   - MySQL not running → Start MySQL service

---

## 📧 Email Feature Setup (Optional)

The email notifications are optional. To enable them:

1. Get Gmail App Password (see Step 5 above)
2. Update email credentials in code
3. When adding students, include parent email
4. Emails will be sent automatically on tap in/out

To test:
1. Add a test student with your own email
2. Tap the card in
3. Check your email inbox

---

## ✨ You're All Set!

Once all files are in place and libraries downloaded:

```batch
compile.bat
run.bat
```

The system should start and be ready to use! 🎉

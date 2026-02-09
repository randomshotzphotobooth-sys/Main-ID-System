# ⚠️ STOP - You're Missing the JavaMail Library!

The error means you don't have the JavaMail JAR file yet.

---

## 🚀 QUICK FIX (Choose ONE method):

### ✅ Method 1: PowerShell Script (EASIEST - RECOMMENDED)

1. Right-click on `download_javamail.ps1`
2. Select **"Run with PowerShell"**
3. Wait for download to complete
4. Run `compile.bat` again

---

### ✅ Method 2: Browser Download (SIMPLE)

**Click this link to download:**
👉 https://repo1.maven.org/maven2/com/sun/mail/javax.mail/1.6.2/javax.mail-1.6.2.jar

**Then:**
1. Save the file as `javax.mail-1.6.2.jar`
2. Move it to your `lib` folder
3. Your `lib` folder should now have:
   - ✅ `mysql-connector-j-8.0.33.jar`
   - ✅ `javax.mail-1.6.2.jar` ← New!

---

### ✅ Method 3: Command Line (For Advanced Users)

**Open PowerShell in your project folder and run:**
```powershell
New-Item -ItemType Directory -Force -Path "lib"
Invoke-WebRequest -Uri "https://repo1.maven.org/maven2/com/sun/mail/javax.mail/1.6.2/javax.mail-1.6.2.jar" -OutFile "lib\javax.mail-1.6.2.jar"
```

**Or using curl (if available):**
```cmd
curl -L -o lib\javax.mail-1.6.2.jar https://repo1.maven.org/maven2/com/sun/mail/javax.mail/1.6.2/javax.mail-1.6.2.jar
```

---

## ✔️ Verify Installation

After downloading, run this to check:
```batch
check_libraries.bat
```

You should see:
```
[OK] JavaMail library found!
```

---

## 📁 Your Project Structure Should Be:

```
your-project/
├── RFIDAttendanceSystem.java
├── compile.bat
├── run.bat
├── download_javamail.ps1
├── check_libraries.bat          ← Use this to verify
├── lib/
│   ├── mysql-connector-j-8.0.33.jar  ✅
│   └── javax.mail-1.6.2.jar          ✅ ← Must have this!
└── photos/
```

---

## 🔄 After Installing the Library:

1. **Verify:** Run `check_libraries.bat`
2. **Compile:** Run `compile.bat`
3. **Run:** Run `run.bat`

---

## ❓ Still Having Issues?

### "File not downloading"
- Try a different method above
- Check your internet connection
- Try downloading from browser (Method 2)

### "File downloads but compile still fails"
- Check file size: Should be ~700 KB
- Make sure filename is exactly: `javax.mail-1.6.2.jar`
- Make sure it's in the `lib` folder
- Check that `compile.bat` has been updated with the new classpath

### "Access denied" or "Permission error"
- Run PowerShell/Command Prompt as Administrator
- Or manually download using your browser (Method 2)

---

## 📝 Notes:

- This library is needed for email notifications to work
- It's a standard Java library from Oracle/Sun
- File size: Approximately 700 KB
- No installation needed - just download and place in lib folder

---

## Quick Checklist:

- [ ] Downloaded `javax.mail-1.6.2.jar`
- [ ] Placed it in `lib\` folder
- [ ] Ran `check_libraries.bat` - shows "OK"
- [ ] Updated `compile.bat` with new classpath (already done if you used my files)
- [ ] Ready to compile!

---

**After completing these steps, run:**
```batch
compile.bat
```

✅ Compilation should succeed!

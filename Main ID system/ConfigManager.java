import java.io.*;
import java.util.Properties;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * ConfigManager - Handles all system configuration
 * Replaces hardcoded values with configurable properties
 */
public class ConfigManager {
    
    private static ConfigManager instance;
    private Properties properties;
    private static final String CONFIG_FILE = "config.properties";
    private static final String ENCRYPTION_KEY = "RFIDSys2024Key!!"; // 16 chars for AES
    
    private ConfigManager() {
        properties = new Properties();
        loadConfiguration();
    }
    
    public static ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }
    
    private void loadConfiguration() {
        try (InputStream input = new FileInputStream(CONFIG_FILE)) {
            properties.load(input);
            System.out.println("✓ Configuration loaded successfully from " + CONFIG_FILE);
        } catch (FileNotFoundException e) {
            System.err.println("⚠ Configuration file not found. Creating default config...");
            createDefaultConfig();
        } catch (IOException e) {
            System.err.println("❌ Error loading configuration: " + e.getMessage());
            createDefaultConfig();
        }
    }
    
    private void createDefaultConfig() {
        // Set default values
        properties.setProperty("db.host", "localhost");
        properties.setProperty("db.port", "3306");
        properties.setProperty("db.name", "rfid_attendance");
        properties.setProperty("db.user", "root");
        properties.setProperty("db.password", "");
        
        properties.setProperty("email.enabled", "true");
        properties.setProperty("email.smtp.host", "smtp.gmail.com");
        properties.setProperty("email.smtp.port", "587");
        properties.setProperty("email.from", "");
        properties.setProperty("email.password", "");
        
        properties.setProperty("admin.default.uid", "0009269290");
        properties.setProperty("attendance.late.threshold.minutes", "15");
        
        properties.setProperty("system.theme", "modern");
        properties.setProperty("system.dark.mode", "false");
        
        saveConfiguration();
    }
    
    public void saveConfiguration() {
        try (OutputStream output = new FileOutputStream(CONFIG_FILE)) {
            properties.store(output, "RFID Attendance System Configuration");
            System.out.println("✓ Configuration saved successfully");
        } catch (IOException e) {
            System.err.println("❌ Error saving configuration: " + e.getMessage());
        }
    }
    
    // Database Configuration
    public String getDatabaseUrl() {
        String host = properties.getProperty("db.host", "localhost");
        String port = properties.getProperty("db.port", "3306");
        String name = properties.getProperty("db.name", "rfid_attendance");
        return String.format("jdbc:mysql://%s:%s/%s", host, port, name);
    }
    
    public String getDatabaseUser() {
        return properties.getProperty("db.user", "root");
    }
    
    public String getDatabasePassword() {
        String encrypted = properties.getProperty("db.password", "");
        return encrypted.isEmpty() ? "" : decrypt(encrypted);
    }
    
    public void setDatabasePassword(String password) {
        properties.setProperty("db.password", encrypt(password));
        saveConfiguration();
    }
    
    // Email Configuration
    public boolean isEmailEnabled() {
        return Boolean.parseBoolean(properties.getProperty("email.enabled", "false"));
    }
    
    public String getEmailSmtpHost() {
        return properties.getProperty("email.smtp.host", "smtp.gmail.com");
    }
    
    public int getEmailSmtpPort() {
        return Integer.parseInt(properties.getProperty("email.smtp.port", "587"));
    }
    
    public String getEmailFrom() {
        return properties.getProperty("email.from", "");
    }
    
    public String getEmailPassword() {
        String encrypted = properties.getProperty("email.password", "");
        return encrypted.isEmpty() ? "" : decrypt(encrypted);
    }
    
    public void setEmailPassword(String password) {
        properties.setProperty("email.password", encrypt(password));
        saveConfiguration();
    }
    
    // Admin Configuration
    public String getAdminDefaultUid() {
        return properties.getProperty("admin.default.uid", "0009269290");
    }
    
    public int getAdminSessionTimeout() {
        return Integer.parseInt(properties.getProperty("admin.session.timeout", "30"));
    }
    
    // Attendance Rules
    public int getLateThresholdMinutes() {
        return Integer.parseInt(properties.getProperty("attendance.late.threshold.minutes", "15"));
    }
    
    public int getEarlyDepartureThresholdMinutes() {
        return Integer.parseInt(properties.getProperty("attendance.early.departure.threshold.minutes", "30"));
    }
    
    public int getGracePeriodMinutes() {
        return Integer.parseInt(properties.getProperty("attendance.grace.period.minutes", "5"));
    }
    
    // System Settings
    public String getSystemTheme() {
        return properties.getProperty("system.theme", "modern");
    }
    
    public boolean isDarkMode() {
        return Boolean.parseBoolean(properties.getProperty("system.dark.mode", "false"));
    }
    
    public void setDarkMode(boolean enabled) {
        properties.setProperty("system.dark.mode", String.valueOf(enabled));
        saveConfiguration();
    }
    
    public boolean isAutoBackupEnabled() {
        return Boolean.parseBoolean(properties.getProperty("system.auto.backup", "true"));
    }
    
    public String getBackupTime() {
        return properties.getProperty("system.backup.time", "02:00");
    }
    
    // Notification Settings
    public boolean isSendOnTimeIn() {
        return Boolean.parseBoolean(properties.getProperty("notification.send.on.timein", "true"));
    }
    
    public boolean isSendOnTimeOut() {
        return Boolean.parseBoolean(properties.getProperty("notification.send.on.timeout", "true"));
    }
    
    public boolean isSendOnLate() {
        return Boolean.parseBoolean(properties.getProperty("notification.send.on.late", "true"));
    }
    
    public boolean isSendOnAbsent() {
        return Boolean.parseBoolean(properties.getProperty("notification.send.on.absent", "true"));
    }
    
    // Photo Settings
    public String getPhotoDirectory() {
        return properties.getProperty("photo.directory", "photos/");
    }
    
    public int getPhotoMaxSizeMB() {
        return Integer.parseInt(properties.getProperty("photo.max.size.mb", "5"));
    }
    
    // Security Settings
    public int getMaxLoginAttempts() {
        return Integer.parseInt(properties.getProperty("security.max.login.attempts", "5"));
    }
    
    public int getLockoutDurationMinutes() {
        return Integer.parseInt(properties.getProperty("security.lockout.duration.minutes", "15"));
    }
    
    public boolean isAuditLogEnabled() {
        return Boolean.parseBoolean(properties.getProperty("security.enable.audit.log", "true"));
    }
    
    // Generic getter
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
    
    // Generic setter
    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
        saveConfiguration();
    }
    
    // Simple encryption (for basic security)
    private String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return "";
        }
        try {
            SecretKeySpec key = new SecretKeySpec(ENCRYPTION_KEY.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(plainText.getBytes());
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            System.err.println("Encryption error: " + e.getMessage());
            return plainText; // Return plain text if encryption fails
        }
    }
    
    private String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return "";
        }
        try {
            SecretKeySpec key = new SecretKeySpec(ENCRYPTION_KEY.getBytes(), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedText));
            return new String(decrypted);
        } catch (Exception e) {
            System.err.println("Decryption error: " + e.getMessage());
            return encryptedText; // Return encrypted text if decryption fails
        }
    }
    
    // Reload configuration from file
    public void reload() {
        loadConfiguration();
    }
    
    // Display configuration (for debugging)
    public void displayConfiguration() {
        System.out.println("\n=== SYSTEM CONFIGURATION ===");
        System.out.println("Database URL: " + getDatabaseUrl());
        System.out.println("Database User: " + getDatabaseUser());
        System.out.println("Email Enabled: " + isEmailEnabled());
        System.out.println("Email From: " + getEmailFrom());
        System.out.println("Admin Default UID: " + getAdminDefaultUid());
        System.out.println("Late Threshold: " + getLateThresholdMinutes() + " minutes");
        System.out.println("System Theme: " + getSystemTheme());
        System.out.println("Dark Mode: " + isDarkMode());
        System.out.println("Auto Backup: " + isAutoBackupEnabled());
        System.out.println("===========================\n");
    }
}

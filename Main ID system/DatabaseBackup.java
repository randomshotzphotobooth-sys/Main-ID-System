import java.io.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Timer;
import java.util.TimerTask;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * DatabaseBackup - Automated database backup system
 * Features: Scheduled backups, compression, retention policy
 */
public class DatabaseBackup {
    
    private ConfigManager config;
    private Timer backupTimer;
    private static final DateTimeFormatter TIMESTAMP_FORMAT = 
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String BACKUP_DIR = "backups/";
    
    public DatabaseBackup() {
        this.config = ConfigManager.getInstance();
        new File(BACKUP_DIR).mkdirs();
    }
    
    /**
     * Start automated backup service
     */
    public void startAutomatedBackup() {
        if (!config.isAutoBackupEnabled()) {
            System.out.println("⚠ Automated backup is disabled in configuration");
            return;
        }
        
        String backupTime = config.getBackupTime(); // e.g., "02:00"
        
        backupTimer = new Timer("DatabaseBackupTimer", true);
        
        // Schedule daily backup
        long delay = calculateInitialDelay(backupTime);
        long period = 24 * 60 * 60 * 1000; // 24 hours in milliseconds
        
        backupTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                performBackup("automated");
            }
        }, delay, period);
        
        System.out.println("✓ Automated backup scheduled for " + backupTime + " daily");
    }
    
    /**
     * Stop automated backup service
     */
    public void stopAutomatedBackup() {
        if (backupTimer != null) {
            backupTimer.cancel();
            System.out.println("✓ Automated backup service stopped");
        }
    }
    
    /**
     * Perform manual backup
     */
    public boolean performManualBackup() {
        return performBackup("manual");
    }
    
    /**
     * Perform database backup
     */
    private boolean performBackup(String backupType) {
        System.out.println("\n=== Starting " + backupType + " database backup ===");
        
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String backupFileName = String.format("rfid_backup_%s_%s.sql", backupType, timestamp);
        String backupFilePath = BACKUP_DIR + backupFileName;
        
        try {
            // Method 1: Using mysqldump (preferred)
            boolean success = backupUsingMySQLDump(backupFilePath);
            
            if (!success) {
                // Method 2: Fallback to SQL export
                System.out.println("⚠ mysqldump not available, using SQL export method...");
                success = backupUsingSQLExport(backupFilePath);
            }
            
            if (success) {
                // Compress the backup file
                compressBackup(backupFilePath);
                
                // Clean old backups
                cleanOldBackups();
                
                System.out.println("✓ Backup completed successfully: " + backupFileName + ".zip");
                logBackup(backupFileName + ".zip", backupType, true, null);
                return true;
            } else {
                System.out.println("❌ Backup failed");
                logBackup(backupFileName, backupType, false, "Backup process failed");
                return false;
            }
            
        } catch (Exception e) {
            System.err.println("❌ Backup error: " + e.getMessage());
            e.printStackTrace();
            logBackup(backupFileName, backupType, false, e.getMessage());
            return false;
        }
    }
    
    /**
     * Backup using mysqldump command
     */
    private boolean backupUsingMySQLDump(String backupFilePath) {
        try {
            String[] command;
            String os = System.getProperty("os.name").toLowerCase();
            
            // Build mysqldump command
            String mysqldumpCmd = String.format(
                "mysqldump -u%s -p%s --databases %s --routines --triggers --events",
                config.getDatabaseUser(),
                config.getDatabasePassword(),
                "rfid_attendance"
            );
            
            if (os.contains("win")) {
                command = new String[]{"cmd.exe", "/c", mysqldumpCmd + " > " + backupFilePath};
            } else {
                command = new String[]{"/bin/sh", "-c", mysqldumpCmd + " > " + backupFilePath};
            }
            
            Process process = Runtime.getRuntime().exec(command);
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                File backupFile = new File(backupFilePath);
                if (backupFile.exists() && backupFile.length() > 0) {
                    System.out.println("✓ mysqldump backup successful (" + 
                        formatFileSize(backupFile.length()) + ")");
                    return true;
                }
            }
            
            return false;
            
        } catch (Exception e) {
            System.err.println("⚠ mysqldump error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Backup using SQL export (fallback method)
     */
    private boolean backupUsingSQLExport(String backupFilePath) {
        try (Connection conn = DriverManager.getConnection(
                config.getDatabaseUrl() + "?allowPublicKeyRetrieval=true&useSSL=false",
                config.getDatabaseUser(),
                config.getDatabasePassword());
             PrintWriter writer = new PrintWriter(new FileWriter(backupFilePath))) {
            
            DatabaseMetaData metaData = conn.getMetaData();
            
            writer.println("-- ========================================");
            writer.println("-- RFID Attendance System Database Backup");
            writer.println("-- Generated: " + LocalDateTime.now());
            writer.println("-- ========================================");
            writer.println();
            writer.println("SET FOREIGN_KEY_CHECKS=0;");
            writer.println();
            
            // Get all tables
            ResultSet tables = metaData.getTables("rfid_attendance", null, "%", 
                new String[]{"TABLE"});
            
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                exportTable(conn, writer, tableName);
            }
            
            writer.println();
            writer.println("SET FOREIGN_KEY_CHECKS=1;");
            writer.println("-- Backup completed successfully");
            
            System.out.println("✓ SQL export backup successful");
            return true;
            
        } catch (Exception e) {
            System.err.println("❌ SQL export error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Export single table
     */
    private void exportTable(Connection conn, PrintWriter writer, String tableName) 
            throws SQLException {
        
        System.out.println("  Backing up table: " + tableName);
        
        // Get CREATE TABLE statement
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SHOW CREATE TABLE " + tableName);
        
        if (rs.next()) {
            writer.println("-- Table: " + tableName);
            writer.println("DROP TABLE IF EXISTS `" + tableName + "`;");
            writer.println(rs.getString(2) + ";");
            writer.println();
        }
        rs.close();
        
        // Get table data
        rs = stmt.executeQuery("SELECT * FROM " + tableName);
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        
        int rowCount = 0;
        while (rs.next()) {
            if (rowCount == 0) {
                writer.println("INSERT INTO `" + tableName + "` VALUES");
            } else {
                writer.println(",");
            }
            
            writer.print("(");
            for (int i = 1; i <= columnCount; i++) {
                Object value = rs.getObject(i);
                
                if (value == null) {
                    writer.print("NULL");
                } else if (value instanceof String || value instanceof Date || 
                           value instanceof Time || value instanceof Timestamp) {
                    writer.print("'" + value.toString().replace("'", "''") + "'");
                } else {
                    writer.print(value.toString());
                }
                
                if (i < columnCount) writer.print(", ");
            }
            writer.print(")");
            rowCount++;
        }
        
        if (rowCount > 0) {
            writer.println(";");
        }
        writer.println();
        
        rs.close();
        stmt.close();
        
        System.out.println("    ✓ Exported " + rowCount + " rows");
    }
    
    /**
     * Compress backup file to ZIP
     */
    private void compressBackup(String backupFilePath) throws IOException {
        File sourceFile = new File(backupFilePath);
        if (!sourceFile.exists()) {
            throw new IOException("Backup file not found: " + backupFilePath);
        }
        
        String zipFilePath = backupFilePath + ".zip";
        
        try (FileInputStream fis = new FileInputStream(sourceFile);
             FileOutputStream fos = new FileOutputStream(zipFilePath);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            
            ZipEntry zipEntry = new ZipEntry(sourceFile.getName());
            zos.putNextEntry(zipEntry);
            
            byte[] buffer = new byte[8192];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, length);
            }
            
            zos.closeEntry();
        }
        
        // Delete original SQL file, keep only ZIP
        sourceFile.delete();
        
        File zipFile = new File(zipFilePath);
        System.out.println("✓ Backup compressed: " + formatFileSize(zipFile.length()));
    }
    
    /**
     * Clean old backup files based on retention policy
     */
    private void cleanOldBackups() {
        int retentionDays = Integer.parseInt(
            config.getProperty("system.backup.retention.days", "30"));
        
        File backupDir = new File(BACKUP_DIR);
        File[] backups = backupDir.listFiles((dir, name) -> 
            name.startsWith("rfid_backup_") && name.endsWith(".zip"));
        
        if (backups == null || backups.length == 0) return;
        
        long cutoffTime = System.currentTimeMillis() - 
            (retentionDays * 24L * 60L * 60L * 1000L);
        
        int deletedCount = 0;
        for (File backup : backups) {
            if (backup.lastModified() < cutoffTime) {
                if (backup.delete()) {
                    deletedCount++;
                    System.out.println("  Deleted old backup: " + backup.getName());
                }
            }
        }
        
        if (deletedCount > 0) {
            System.out.println("✓ Cleaned " + deletedCount + " old backup(s)");
        }
    }
    
    /**
     * Log backup operation to database
     */
    private void logBackup(String fileName, String backupType, boolean success, String error) {
        try (Connection conn = DriverManager.getConnection(
                config.getDatabaseUrl() + "?allowPublicKeyRetrieval=true&useSSL=false",
                config.getDatabaseUser(),
                config.getDatabasePassword())) {
            
            String sql = "INSERT INTO audit_logs (user_id, action, details) VALUES (NULL, ?, ?)";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, "DATABASE_BACKUP");
            
            String details = String.format("Type: %s, File: %s, Success: %s%s",
                backupType, fileName, success, 
                error != null ? ", Error: " + error : "");
            
            stmt.setString(2, details);
            stmt.executeUpdate();
            stmt.close();
            
        } catch (SQLException e) {
            // Don't throw exception, just log to console
            System.err.println("⚠ Could not log backup to database: " + e.getMessage());
        }
    }
    
    /**
     * Calculate initial delay for scheduled backup
     */
    private long calculateInitialDelay(String backupTime) {
        // Parse backup time (e.g., "02:00")
        String[] parts = backupTime.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextBackup = now.withHour(hour).withMinute(minute).withSecond(0);
        
        // If backup time has passed today, schedule for tomorrow
        if (nextBackup.isBefore(now)) {
            nextBackup = nextBackup.plusDays(1);
        }
        
        long delay = java.time.Duration.between(now, nextBackup).toMillis();
        
        System.out.println("  Next backup scheduled at: " + nextBackup);
        return delay;
    }
    
    /**
     * List all available backups
     */
    public void listBackups() {
        File backupDir = new File(BACKUP_DIR);
        File[] backups = backupDir.listFiles((dir, name) -> 
            name.startsWith("rfid_backup_") && name.endsWith(".zip"));
        
        if (backups == null || backups.length == 0) {
            System.out.println("No backups found");
            return;
        }
        
        System.out.println("\n=== Available Backups ===");
        for (File backup : backups) {
            System.out.printf("%-50s %10s %s\n",
                backup.getName(),
                formatFileSize(backup.length()),
                new java.util.Date(backup.lastModified()));
        }
        System.out.println("Total: " + backups.length + " backup(s)\n");
    }
    
    /**
     * Restore from backup (simplified version)
     */
    public void restoreFromBackup(String backupFileName) {
        System.out.println("⚠ WARNING: This will replace current database!");
        System.out.println("Make sure to create a backup of current data first.");
        System.out.println("\nTo restore manually:");
        System.out.println("1. Extract " + backupFileName);
        System.out.println("2. Run: mysql -u root -p rfid_attendance < backup.sql");
    }
    
    /**
     * Format file size for display
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
    
    /**
     * Test backup system
     */
    public static void main(String[] args) {
        System.out.println("=== Database Backup System Test ===\n");
        
        DatabaseBackup backup = new DatabaseBackup();
        
        // Perform manual backup
        boolean success = backup.performManualBackup();
        
        if (success) {
            System.out.println("\n✓ Backup test successful!");
            backup.listBackups();
        } else {
            System.out.println("\n❌ Backup test failed!");
        }
    }
}

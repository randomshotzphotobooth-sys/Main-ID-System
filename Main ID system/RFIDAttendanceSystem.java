import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

public class RFIDAttendanceSystem extends JFrame {
    
    private static ConfigManager config = ConfigManager.getInstance();
    private static final String DB_URL = config.getDatabaseUrl();
    private static final String DB_USER = config.getDatabaseUser();
    private static final String DB_PASSWORD = config.getDatabasePassword();
    private static final String EMAIL_FROM = config.getEmailFrom();
    private static final String EMAIL_PASSWORD = config.getEmailPassword();
    // Admin UID
    private static final String ADMIN_UID = "0009269290";
    
    // UI Components
    private JTextField hiddenRfidField;
    private JLabel statusLabel;
    private JLabel clockLabel;
    private JPanel profilePanel;
    private boolean adminScanMode = false;
    private Timer clockTimer;
    private boolean isFullScreen = false;
    
    // Photo directory
    private static final String PHOTO_DIR = "photos/";
    
    public RFIDAttendanceSystem() {
        setTitle("RFID Attendance System");
        setSize(900, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        // Create photos directory if it doesn't exist
        new File(PHOTO_DIR).mkdirs();
        
        // Initialize database
        initializeDatabase();
        
        // Setup UI
        setupUI();
        
        // Setup keyboard shortcuts
        setupKeyboardShortcuts();
        
        // Start real-time clock
        startClock();
        
        setVisible(true);
    }
    
    private void startClock() {
        clockTimer = new Timer(1000, e -> updateClock());
        clockTimer.start();
        updateClock(); // Initial update
    }
    
    private void updateClock() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy  •  hh:mm:ss a");
        clockLabel.setText(now.format(formatter));
    }
    
    private void initializeDatabase() {
        try {
            // First, connect without database to create it
            Connection conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/?allowPublicKeyRetrieval=true&useSSL=false", 
                DB_USER, 
                DB_PASSWORD
            );
            Statement stmt = conn.createStatement();
            
            // Create database if not exists
            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS rfid_attendance");
            stmt.close();
            conn.close();
            
            // Now connect to the database
            conn = DriverManager.getConnection(DB_URL + "?allowPublicKeyRetrieval=true&useSSL=false", DB_USER, DB_PASSWORD);
            stmt = conn.createStatement();
            
            // Create USERS table
            String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" +
                "user_id INT PRIMARY KEY AUTO_INCREMENT," +
                "rfid_uid VARCHAR(50) UNIQUE NOT NULL," +
                "full_name VARCHAR(100) NOT NULL," +
                "role VARCHAR(20) NOT NULL," +
                "status VARCHAR(10) DEFAULT 'active'," +
                "photo_path VARCHAR(255)," +
                "parent_email VARCHAR(100)," +
                "birthday DATE" +
                ")";
            stmt.executeUpdate(createUsersTable);
            
            // Create STUDENTS table
            String createStudentsTable = "CREATE TABLE IF NOT EXISTS students (" +
                "user_id INT PRIMARY KEY," +
                "grade VARCHAR(20)," +
                "section VARCHAR(20)," +
                "class_type VARCHAR(10) DEFAULT 'morning'," +
                "FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE" +
                ")";
            stmt.executeUpdate(createStudentsTable);
            
            // Create ATTENDANCE table
            String createAttendanceTable = "CREATE TABLE IF NOT EXISTS attendance (" +
                "id INT PRIMARY KEY AUTO_INCREMENT," +
                "user_id INT NOT NULL," +
                "date DATE NOT NULL," +
                "time_in TIME," +
                "time_out TIME," +
                "status VARCHAR(20)," +
                "late_minutes INT DEFAULT 0," +
                "early_out_minutes INT DEFAULT 0," +
                "FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE," +
                "UNIQUE KEY unique_attendance (user_id, date)" +
                ")";
            stmt.executeUpdate(createAttendanceTable);
            
            // ADD MISSING COLUMNS IF TABLES ALREADY EXIST
            try {
                // Add class_type column to students table if it doesn't exist
                stmt.executeUpdate("ALTER TABLE students ADD COLUMN IF NOT EXISTS class_type VARCHAR(10) DEFAULT 'morning'");
                System.out.println("✓ Added class_type column to students table");
            } catch (SQLException e) {
                System.out.println("Note: class_type column may already exist in students table");
            }
            
            try {
                // Add status column to attendance table if it doesn't exist
                stmt.executeUpdate("ALTER TABLE attendance ADD COLUMN IF NOT EXISTS status VARCHAR(20)");
                System.out.println("✓ Added status column to attendance table");
            } catch (SQLException e) {
                System.out.println("Note: status column may already exist in attendance table");
            }
            
            try {
                // Add late_minutes column to attendance table if it doesn't exist
                stmt.executeUpdate("ALTER TABLE attendance ADD COLUMN IF NOT EXISTS late_minutes INT DEFAULT 0");
                System.out.println("✓ Added late_minutes column to attendance table");
            } catch (SQLException e) {
                System.out.println("Note: late_minutes column may already exist in attendance table");
            }
            
            try {
                // Add early_out_minutes column to attendance table if it doesn't exist
                stmt.executeUpdate("ALTER TABLE attendance ADD COLUMN IF NOT EXISTS early_out_minutes INT DEFAULT 0");
                System.out.println("✓ Added early_out_minutes column to attendance table");
            } catch (SQLException e) {
                System.out.println("Note: early_out_minutes column may already exist in attendance table");
            }
            
            stmt.close();
            conn.close();
            
            // Register default admin card if not already registered
            registerDefaultAdmin();
            
            System.out.println("Database initialized successfully!");
            
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, 
                "Database Error: " + e.getMessage() + 
                "\n\nMake sure MySQL is running and credentials are correct!", 
                "Database Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void registerDefaultAdmin() {
        try (Connection conn = DriverManager.getConnection(DB_URL + "?allowPublicKeyRetrieval=true&useSSL=false", DB_USER, DB_PASSWORD)) {
            
            // Check if admin card already exists
            String checkSql = "SELECT * FROM users WHERE rfid_uid = ?";
            PreparedStatement checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setString(1, ADMIN_UID);
            ResultSet rs = checkStmt.executeQuery();
            
            if (!rs.next()) {
                // Admin doesn't exist, insert it
                String insertSql = "INSERT INTO users (rfid_uid, full_name, role, status, photo_path) " +
                                 "VALUES (?, 'System Admin', 'admin', 'active', 'photos/admin.jpg')";
                PreparedStatement insertStmt = conn.prepareStatement(insertSql);
                insertStmt.setString(1, ADMIN_UID);
                insertStmt.executeUpdate();
                System.out.println("✓ Default admin card registered with UID: " + ADMIN_UID);
            } else {
                System.out.println("✓ Admin card already registered with UID: " + ADMIN_UID);
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Error registering default admin: " + e.getMessage());
        }
    }
    
    private void setupUI() {
        setLayout(new BorderLayout(10, 10));
        
        // Top panel with clock and status
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 10, 15));
        
        // Clock label
        clockLabel = new JLabel("", SwingConstants.CENTER);
        clockLabel.setFont(new Font("Arial", Font.BOLD, 20));
        clockLabel.setForeground(new Color(50, 50, 50));
        topPanel.add(clockLabel, BorderLayout.NORTH);
        
        // Status label
        statusLabel = new JLabel("System Ready - Waiting for RFID scan...", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 22));
        statusLabel.setForeground(new Color(0, 128, 0));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        topPanel.add(statusLabel, BorderLayout.CENTER);
        
        // Hidden RFID field (auto-focused)
        hiddenRfidField = new JTextField();
        hiddenRfidField.setPreferredSize(new Dimension(0, 0));
        hiddenRfidField.setBorder(null);
        hiddenRfidField.addActionListener(e -> processRfidScan());
        topPanel.add(hiddenRfidField, BorderLayout.SOUTH);
        
        add(topPanel, BorderLayout.NORTH);
        
        // Center panel for profile display
        profilePanel = new JPanel();
        profilePanel.setLayout(new BoxLayout(profilePanel, BoxLayout.Y_AXIS));
        profilePanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        JLabel welcomeLabel = new JLabel("Tap your RFID card to begin");
        welcomeLabel.setFont(new Font("Arial", Font.PLAIN, 28));
        welcomeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        profilePanel.add(Box.createVerticalGlue());
        profilePanel.add(welcomeLabel);
        profilePanel.add(Box.createVerticalGlue());
        
        add(new JScrollPane(profilePanel), BorderLayout.CENTER);
        
        // Auto-focus on hidden field
        addWindowFocusListener(new WindowAdapter() {
            public void windowGainedFocus(WindowEvent e) {
                hiddenRfidField.requestFocusInWindow();
            }
        });
    }
    
    private void setupKeyboardShortcuts() {
        // Ctrl+F2 for admin registration
        KeyStroke ctrlF2 = KeyStroke.getKeyStroke(KeyEvent.VK_F2, KeyEvent.CTRL_DOWN_MASK);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ctrlF2, "registerAdmin");
        getRootPane().getActionMap().put("registerAdmin", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                enterAdminScanMode();
            }
        });
        
        // F11 for full screen
        KeyStroke f11 = KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(f11, "toggleFullScreen");
        getRootPane().getActionMap().put("toggleFullScreen", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                toggleFullScreen();
            }
        });
    }
    
    private void toggleFullScreen() {
        if (!isFullScreen) {
            // Enter full screen
            dispose();
            setUndecorated(true);
            setExtendedState(JFrame.MAXIMIZED_BOTH);
            setVisible(true);
            isFullScreen = true;
        } else {
            // Exit full screen
            dispose();
            setUndecorated(false);
            setExtendedState(JFrame.NORMAL);
            setSize(900, 700);
            setLocationRelativeTo(null);
            setVisible(true);
            isFullScreen = false;
        }
    }
    
    private void enterAdminScanMode() {
        adminScanMode = true;
        statusLabel.setText("ADMIN MODE: Tap your Admin RFID card now...");
        statusLabel.setForeground(Color.BLUE);
        hiddenRfidField.setText("");
        hiddenRfidField.requestFocusInWindow();
    }
    
    private void processRfidScan() {
        String rfidUid = hiddenRfidField.getText().trim();
        hiddenRfidField.setText(""); // Clear field
        
        if (rfidUid.isEmpty()) {
            return;
        }
        
        // Beep sound (optional)
        Toolkit.getDefaultToolkit().beep();
        
        if (adminScanMode) {
            registerAdminCard(rfidUid);
            adminScanMode = false;
        } else {
            processAttendance(rfidUid);
        }
        
        // Refocus on hidden field
        SwingUtilities.invokeLater(() -> hiddenRfidField.requestFocusInWindow());
    }
    
    private void registerAdminCard(String rfidUid) {
        try (Connection conn = DriverManager.getConnection(DB_URL + "?allowPublicKeyRetrieval=true&useSSL=false", DB_USER, DB_PASSWORD)) {
            
            // Check if UID already exists
            String checkSql = "SELECT * FROM users WHERE rfid_uid = ?";
            PreparedStatement checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setString(1, rfidUid);
            ResultSet rs = checkStmt.executeQuery();
            
            if (rs.next()) {
                statusLabel.setText("ERROR: This RFID card is already registered!");
                statusLabel.setForeground(Color.RED);
                return;
            }
            
            // Insert admin
            String insertSql = "INSERT INTO users (rfid_uid, full_name, role, status, photo_path) " +
                             "VALUES (?, 'System Admin', 'admin', 'active', 'photos/admin.jpg')";
            PreparedStatement insertStmt = conn.prepareStatement(insertSql);
            insertStmt.setString(1, rfidUid);
            insertStmt.executeUpdate();
            
            statusLabel.setText("✓ Admin card registered successfully! UID: " + maskUid(rfidUid));
            statusLabel.setForeground(new Color(0, 128, 0));
            
            JOptionPane.showMessageDialog(this, 
                "Admin card registered successfully!\n\nYou can now tap this card to access the Admin Panel.",
                "Success",
                JOptionPane.INFORMATION_MESSAGE);
            
        } catch (SQLException e) {
            e.printStackTrace();
            statusLabel.setText("ERROR: Database error!");
            statusLabel.setForeground(Color.RED);
        }
    }
    
    private void processAttendance(String rfidUid) {
        try (Connection conn = DriverManager.getConnection(DB_URL + "?allowPublicKeyRetrieval=true&useSSL=false", DB_USER, DB_PASSWORD)) {
            
            // Lookup user
            String userSql = "SELECT * FROM users WHERE rfid_uid = ?";
            PreparedStatement userStmt = conn.prepareStatement(userSql);
            userStmt.setString(1, rfidUid);
            ResultSet userRs = userStmt.executeQuery();
            
            if (!userRs.next()) {
                statusLabel.setText("❌ RFID NOT REGISTERED - UID: " + maskUid(rfidUid));
                statusLabel.setForeground(Color.RED);
                return;
            }
            
            int userId = userRs.getInt("user_id");
            String fullName = userRs.getString("full_name");
            String role = userRs.getString("role");
            String status = userRs.getString("status");
            String photoPath = userRs.getString("photo_path");
            String parentEmail = userRs.getString("parent_email");
            
            if ("inactive".equals(status)) {
                statusLabel.setText("❌ CARD INACTIVE - " + fullName);
                statusLabel.setForeground(Color.RED);
                return;
            }
            
            // Admin opens admin panel
            if ("admin".equals(role)) {
                openAdminPanel();
                return;
            }
            
            // Process attendance for student/teacher
            LocalDate today = LocalDate.now();
            LocalTime now = LocalTime.now();
            
            // Get class type for late calculation
            String classType = "morning"; // default
            if ("student".equals(role)) {
                // Check if class_type column exists first
                try {
                    String classSql = "SELECT class_type FROM students WHERE user_id = ?";
                    PreparedStatement classStmt = conn.prepareStatement(classSql);
                    classStmt.setInt(1, userId);
                    ResultSet classRs = classStmt.executeQuery();
                    if (classRs.next()) {
                        classType = classRs.getString("class_type");
                    }
                } catch (SQLException e) {
                    // Column might not exist yet, use default
                    classType = "morning";
                }
            }
            
            // Calculate if late based on class type
            LocalTime lateThreshold = classType.equalsIgnoreCase("morning") ? 
                LocalTime.of(7, 30) : LocalTime.of(13, 0); // 1:00 PM for afternoon
            int lateMinutes = 0;
            if (now.isAfter(lateThreshold)) {
                lateMinutes = (int) java.time.Duration.between(lateThreshold, now).toMinutes();
            }
            
            // Check today's attendance
            String attendanceSql = "SELECT * FROM attendance WHERE user_id = ? AND date = ?";
            PreparedStatement attendanceStmt = conn.prepareStatement(attendanceSql);
            attendanceStmt.setInt(1, userId);
            attendanceStmt.setDate(2, Date.valueOf(today));
            ResultSet attendanceRs = attendanceStmt.executeQuery();
            
            String action = "";
            String attendanceStatus = "PRESENT";
            
            if (!attendanceRs.next()) {
                // TIME-IN
                if (lateMinutes > 0) {
                    attendanceStatus = "LATE";
                }
                
                // Check if status column exists
                String insertSql;
                try {
                    // Try with new columns first
                    insertSql = "INSERT INTO attendance (user_id, date, time_in, status, late_minutes) VALUES (?, ?, ?, ?, ?)";
                    PreparedStatement insertStmt = conn.prepareStatement(insertSql);
                    insertStmt.setInt(1, userId);
                    insertStmt.setDate(2, Date.valueOf(today));
                    insertStmt.setTime(3, Time.valueOf(now));
                    insertStmt.setString(4, attendanceStatus);
                    insertStmt.setInt(5, lateMinutes);
                    insertStmt.executeUpdate();
                } catch (SQLException e) {
                    // If new columns don't exist, use old query
                    insertSql = "INSERT INTO attendance (user_id, date, time_in) VALUES (?, ?, ?)";
                    PreparedStatement insertStmt = conn.prepareStatement(insertSql);
                    insertStmt.setInt(1, userId);
                    insertStmt.setDate(2, Date.valueOf(today));
                    insertStmt.setTime(3, Time.valueOf(now));
                    insertStmt.executeUpdate();
                }
                
                action = "TIME-IN";
                statusLabel.setForeground(new Color(0, 128, 0));
                
                // Send email notification for TIME-IN
                if (parentEmail != null && !parentEmail.isEmpty()) {
                    sendEmailNotification(parentEmail, fullName, "arrived", now.toString());
                }
            } else {
                Time timeOut = attendanceRs.getTime("time_out");
                if (timeOut == null) {
                    // TIME-OUT
                    Time timeIn = attendanceRs.getTime("time_in");
                    
                    // Calculate early out and total hours
                    LocalTime earlyOutThreshold = classType.equalsIgnoreCase("morning") ? 
                        LocalTime.of(16, 0) : LocalTime.of(20, 0); // 8:00 PM for afternoon
                    int earlyOutMinutes = 0;
                    if (now.isBefore(earlyOutThreshold)) {
                        earlyOutMinutes = (int) java.time.Duration.between(now, earlyOutThreshold).toMinutes();
                    }
                    
                    // Try to update with new columns first
                    try {
                        String updateSql = "UPDATE attendance SET time_out = ?, early_out_minutes = ? WHERE user_id = ? AND date = ?";
                        PreparedStatement updateStmt = conn.prepareStatement(updateSql);
                        updateStmt.setTime(1, Time.valueOf(now));
                        updateStmt.setInt(2, earlyOutMinutes);
                        updateStmt.setInt(3, userId);
                        updateStmt.setDate(4, Date.valueOf(today));
                        updateStmt.executeUpdate();
                    } catch (SQLException e) {
                        // If new columns don't exist, use old query
                        String updateSql = "UPDATE attendance SET time_out = ? WHERE user_id = ? AND date = ?";
                        PreparedStatement updateStmt = conn.prepareStatement(updateSql);
                        updateStmt.setTime(1, Time.valueOf(now));
                        updateStmt.setInt(2, userId);
                        updateStmt.setDate(3, Date.valueOf(today));
                        updateStmt.executeUpdate();
                    }
                    
                    action = "TIME-OUT";
                    statusLabel.setForeground(new Color(255, 140, 0));
                    
                    // Send email notification for TIME-OUT
                    if (parentEmail != null && !parentEmail.isEmpty()) {
                        sendEmailNotification(parentEmail, fullName, "left", now.toString());
                    }
                } else {
                    action = "ATTENDANCE COMPLETED";
                    statusLabel.setForeground(Color.BLUE);
                }
            }
            
            // Get student info if applicable
            String grade = "";
            String section = "";
            String userCode = ""; // For display ID
            if ("student".equals(role)) {
                // Try to get class_type, but handle if column doesn't exist
                String studentSql = "SELECT grade, section FROM students WHERE user_id = ?";
                PreparedStatement studentStmt = conn.prepareStatement(studentSql);
                studentStmt.setInt(1, userId);
                ResultSet studentRs = studentStmt.executeQuery();
                if (studentRs.next()) {
                    grade = studentRs.getString("grade");
                    section = studentRs.getString("section");
                    userCode = "STU-" + String.format("%05d", userId);
                }
            } else if ("teacher".equals(role)) {
                userCode = "TCH-" + String.format("%05d", userId);
            }
            
            // Get total days present - Fixed query to count distinct dates
            String countSql = "SELECT COUNT(DISTINCT date) as total FROM attendance WHERE user_id = ? AND time_in IS NOT NULL";
            PreparedStatement countStmt = conn.prepareStatement(countSql);
            countStmt.setInt(1, userId);
            ResultSet countRs = countStmt.executeQuery();
            int totalDays = 0;
            if (countRs.next()) {
                totalDays = countRs.getInt("total");
            }
            
            // Get today's times and status
            String todaySql = "SELECT * FROM attendance WHERE user_id = ? AND date = ?";
            PreparedStatement todayStmt = conn.prepareStatement(todaySql);
            todayStmt.setInt(1, userId);
            todayStmt.setDate(2, Date.valueOf(today));
            ResultSet todayRs = todayStmt.executeQuery();
            
            String timeIn = "";
            String timeOut = "";
            String todayStatus = "PRESENT";
            int lateBy = 0;
            int earlyOut = 0;
            String totalHours = "-";
            
            if (todayRs.next()) {
                Time tin = todayRs.getTime("time_in");
                Time tout = todayRs.getTime("time_out");
                timeIn = tin != null ? tin.toString() : "";
                timeOut = tout != null ? tout.toString() : "";
                
                // Try to get new columns if they exist
                try {
                    todayStatus = todayRs.getString("status") != null ? todayRs.getString("status") : "PRESENT";
                    lateBy = todayRs.getInt("late_minutes");
                    earlyOut = todayRs.getInt("early_out_minutes");
                } catch (SQLException e) {
                    // Columns don't exist, use defaults
                    todayStatus = "PRESENT";
                    lateBy = 0;
                    earlyOut = 0;
                }
                
                // Calculate total hours if both times exist
                if (tin != null && tout != null) {
                    LocalTime inTime = tin.toLocalTime();
                    LocalTime outTime = tout.toLocalTime();
                    long hours = java.time.Duration.between(inTime, outTime).toHours();
                    long minutes = java.time.Duration.between(inTime, outTime).toMinutes() % 60;
                    totalHours = String.format("%d:%02d", hours, minutes);
                }
            }
            
            // Update status
            statusLabel.setText("✓ " + action + " - " + fullName);
            
            // Show profile with new UI design
            displayProfileNew(fullName, role, grade, section, classType, userCode, photoPath, timeIn, timeOut, totalDays, todayStatus, lateBy, earlyOut, totalHours);
            
        } catch (SQLException e) {
            e.printStackTrace();
            statusLabel.setText("ERROR: Database error!");
            statusLabel.setForeground(Color.RED);
        }
    }
    
    private void displayProfileNew(String name, String role, String grade, String section, String classType,
                                   String userCode, String photoPath, String timeIn, 
                                   String timeOut, int totalDays, String status, 
                                   int lateMinutes, int earlyOutMinutes, String totalHours) {
        profilePanel.removeAll();
        profilePanel.setLayout(new BorderLayout(5, 5));
        
        // Create main panel with border layout
        JPanel mainPanel = new JPanel(new GridLayout(1, 3, 10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Left Panel - Time IN
        JPanel leftPanel = createTimePanel("TIME IN", timeIn, !timeIn.isEmpty(), Color.GREEN.darker());
        
        // Center Panel - User Information with PHOTO
        JPanel centerPanel = createUserInfoPanel(name, role, grade, section, classType, userCode, photoPath, timeIn, timeOut, totalDays, status, lateMinutes, earlyOutMinutes, totalHours);
        
        // Right Panel - Time OUT
        JPanel rightPanel = createTimePanel("TIME OUT", timeOut, !timeOut.isEmpty(), Color.ORANGE.darker());
        
        mainPanel.add(leftPanel);
        mainPanel.add(centerPanel);
        mainPanel.add(rightPanel);
        
        profilePanel.add(mainPanel, BorderLayout.CENTER);
        
        profilePanel.revalidate();
        profilePanel.repaint();
    }
    
    private JPanel createTimePanel(String title, String time, boolean isLogged, Color color) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(color, 3),
            title,
            javax.swing.border.TitledBorder.CENTER,
            javax.swing.border.TitledBorder.TOP,
            new Font("Arial", Font.BOLD, 16),
            color
        ));
        
        // Time label
        JLabel timeLabel = new JLabel(time.isEmpty() ? "--:--:--" : formatTimeForDisplay(time));
        timeLabel.setFont(new Font("Monospaced", Font.BOLD, 28));
        timeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        // Status label
        JLabel statusLabel = new JLabel(isLogged ? "✓ Logged" : "Pending");
        statusLabel.setFont(new Font("Arial", Font.BOLD, 18));
        statusLabel.setForeground(isLogged ? Color.GREEN.darker() : Color.GRAY);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        // Add vertical glue for centering
        panel.add(Box.createVerticalGlue());
        panel.add(timeLabel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(statusLabel);
        panel.add(Box.createVerticalGlue());
        
        return panel;
    }
    
    private JPanel createUserInfoPanel(String name, String role, String grade, String section, String classType,
                                       String userCode, String photoPath, String timeIn, 
                                       String timeOut, int totalDays, String status, 
                                       int lateMinutes, int earlyOutMinutes, String totalHours) {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.BLUE, 3),
            "USER INFORMATION",
            javax.swing.border.TitledBorder.CENTER,
            javax.swing.border.TitledBorder.TOP,
            new Font("Arial", Font.BOLD, 16),
            Color.BLUE
        ));
        
        // Create center content panel
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        
        // Create top panel for photo
        JPanel photoPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        
        // Load and display actual photo
        JLabel photoLabel = new JLabel();
        photoLabel.setPreferredSize(new Dimension(140, 140));
        photoLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
        photoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        if (photoPath != null && new File(photoPath).exists()) {
            ImageIcon icon = new ImageIcon(photoPath);
            Image img = icon.getImage().getScaledInstance(130, 130, Image.SCALE_SMOOTH);
            photoLabel.setIcon(new ImageIcon(img));
        } else {
            // Default icon if no photo
            photoLabel.setIcon(new ImageIcon());
            photoLabel.setText("No Photo");
            photoLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        }
        
        photoPanel.add(photoLabel);
        contentPanel.add(photoPanel);
        contentPanel.add(Box.createVerticalStrut(20));
        
        // Role label
        JLabel roleLabel = new JLabel(role.toUpperCase() + " (" + classType.toUpperCase() + ")");
        roleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        roleLabel.setForeground(Color.DARK_GRAY);
        roleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        contentPanel.add(roleLabel);
        contentPanel.add(Box.createVerticalStrut(25));
        
        // Info panel
        JPanel infoPanel = new JPanel(new GridLayout(0, 1, 5, 8));
        infoPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        // Name
        addInfoRow(infoPanel, "Name   :", name);
        
        // ID
        if (!userCode.isEmpty()) {
            addInfoRow(infoPanel, "ID     :", userCode);
        }
        
        // Status with color coding
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JLabel statusLabel = new JLabel("Status :");
        statusLabel.setFont(new Font("Monospaced", Font.BOLD, 14));
        statusLabel.setPreferredSize(new Dimension(150, 20));
        
        JLabel statusValue = new JLabel(status);
        statusValue.setFont(new Font("Arial", Font.BOLD, 14));
        
        // Color code the status
        if (status.equals("LATE")) {
            statusValue.setForeground(Color.ORANGE.darker());
        } else if (status.equals("PRESENT")) {
            statusValue.setForeground(Color.GREEN.darker());
        } else if (status.equals("ABSENT")) {
            statusValue.setForeground(Color.RED);
        } else {
            statusValue.setForeground(Color.BLUE);
        }
        
        statusPanel.add(statusLabel);
        statusPanel.add(statusValue);
        infoPanel.add(statusPanel);
        
        // Today's attendance
        String todayStatus = "IN " + (!timeIn.isEmpty() ? "✓" : "✗") + " | OUT " + (!timeOut.isEmpty() ? "✓" : "✗");
        addInfoRow(infoPanel, "Today  :", todayStatus);
        
        // Total days
        addInfoRow(infoPanel, "Total Days Present :", String.valueOf(totalDays));
        
        // Grade and Section for students
        if ("student".equals(role) && grade != null && !grade.isEmpty()) {
            addInfoRow(infoPanel, "Grade/Section :", "Grade " + grade + " - " + section);
        }
        
        // Late minutes if any
        if (lateMinutes > 0) {
            addInfoRow(infoPanel, "Late By     :", lateMinutes + " mins");
        }
        
        contentPanel.add(infoPanel);
        contentPanel.add(Box.createVerticalGlue());
        
        panel.add(contentPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    private void addInfoRow(JPanel panel, String label, String value) {
        JPanel rowPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JLabel labelLbl = new JLabel(label);
        labelLbl.setFont(new Font("Monospaced", Font.BOLD, 14));
        labelLbl.setPreferredSize(new Dimension(150, 20));
        
        JLabel valueLbl = new JLabel(value);
        valueLbl.setFont(new Font("Arial", Font.PLAIN, 14));
        
        rowPanel.add(labelLbl);
        rowPanel.add(valueLbl);
        panel.add(rowPanel);
    }
    
    private String formatTimeForDisplay(String time) {
        try {
            LocalTime lt = LocalTime.parse(time);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
            return lt.format(formatter);
        } catch (Exception e) {
            return time;
        }
    }
    
    private void sendEmailNotification(String recipientEmail, String studentName, String action, String time) {
        // Run in background thread to avoid blocking UI
        new Thread(() -> {
            try {
                Properties props = new Properties();
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.port", "587");
                props.put("mail.smtp.ssl.trust", "smtp.gmail.com");
                props.put("mail.smtp.ssl.protocols", "TLSv1.2");
                
                Session session = Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(EMAIL_FROM, EMAIL_PASSWORD);
                    }
                });
                
                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(EMAIL_FROM));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
                
                if (action.equals("arrived")) {
                    message.setSubject("Your child has arrived at SNLC");
                    message.setText("Dear Parent/Guardian,\n\n" +
                        "Thank you for entrusting your child with us! Your Child has arrived at SNLC.\n\n" +
                        "Student: " + studentName + "\n" +
                        "Time: " + time + "\n\n" +
                        "Thank you,\n" +
                        "SNLC Attendance System");
                } else {
                    message.setSubject("Your child has left SNLC");
                    message.setText("Dear Parent/Guardian,\n\n" +
                        "We would like to inform you that your child has left SNLC. " +
                        "Please check their diary for Assignments and Quizzes. Thank you for your trust!\n\n" +
                        "Student: " + studentName + "\n" +
                        "Time: " + time + "\n\n" +
                        "Thank you,\n" +
                        "SNLC Attendance System");
                }
                
                Transport.send(message);
                System.out.println("✓ Email notification sent to: " + recipientEmail);
                
            } catch (Exception e) {
                System.err.println("✗ Failed to send email: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }
    
    private void displayProfile(String name, String role, String grade, String section, 
                                String photoPath, String timeIn, String timeOut, int totalDays) {
        // Old method kept for compatibility
        displayProfileNew(name, role, grade, section, "morning", "", photoPath, timeIn, timeOut, totalDays, "PRESENT", 0, 0, "-");
    }
    
    private void openAdminPanel() {
        AdminPanel adminPanel = new AdminPanel(this);
        adminPanel.setVisible(true);
        statusLabel.setText("Admin Panel Opened");
        statusLabel.setForeground(Color.BLUE);
    }
    
    private String maskUid(String uid) {
        if (uid.length() <= 4) return uid;
        return "****" + uid.substring(uid.length() - 4);
    }
    
    // ======================== ADMIN PANEL CLASS ========================
    
    class AdminPanel extends JFrame {
        private JTable userTable;
        private DefaultTableModel tableModel;
        private JTextField searchField;
        private RFIDAttendanceSystem parent;
        
        public AdminPanel(RFIDAttendanceSystem parent) {
            this.parent = parent;
            setTitle("Admin Panel - User Management");
            setSize(1100, 650);
            setLocationRelativeTo(parent);
            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            
            setupAdminUI();
            loadUsers();
        }
        
        private void setupAdminUI() {
            setLayout(new BorderLayout(10, 10));
            
            // Top panel with search and buttons
            JPanel topPanel = new JPanel(new BorderLayout(10, 10));
            topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            
            // Search panel
            JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            searchPanel.add(new JLabel("Search:"));
            searchField = new JTextField(20);
            searchField.addKeyListener(new KeyAdapter() {
                public void keyReleased(KeyEvent e) {
                    searchUsers();
                }
            });
            searchPanel.add(searchField);
            topPanel.add(searchPanel, BorderLayout.WEST);
            
            // Button panel
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            
            JButton addButton = new JButton(" Add User");
            addButton.addActionListener(e -> openAddUserForm());
            buttonPanel.add(addButton);
            
            JButton editButton = new JButton(" Edit User");
            editButton.addActionListener(e -> editSelectedUser());
            buttonPanel.add(editButton);
            
            JButton removeButton = new JButton(" Remove User");
            removeButton.addActionListener(e -> removeSelectedUser());
            buttonPanel.add(removeButton);
            
            JButton attendanceLogButton = new JButton(" Attendance Log");
            attendanceLogButton.addActionListener(e -> openAttendanceLog());
            buttonPanel.add(attendanceLogButton);
            
            JButton refreshButton = new JButton(" Refresh");
            refreshButton.addActionListener(e -> loadUsers());
            buttonPanel.add(refreshButton);
            
            topPanel.add(buttonPanel, BorderLayout.EAST);
            add(topPanel, BorderLayout.NORTH);
            
            // Table
            String[] columns = {"ID", "Student ID", "Full Name", "Role", "Class Type", "Status", "Grade", "Section", "Birthday", "Parent Email"};
            tableModel = new DefaultTableModel(columns, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            userTable = new JTable(tableModel);
            userTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            userTable.setFont(new Font("Arial", Font.PLAIN, 13));
            userTable.setRowHeight(25);
            userTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 13));
            userTable.getColumnModel().getColumn(1).setPreferredWidth(120);
            userTable.getColumnModel().getColumn(7).setPreferredWidth(100);
            userTable.getColumnModel().getColumn(9).setPreferredWidth(180);
            
            add(new JScrollPane(userTable), BorderLayout.CENTER);
        }
        
        private void loadUsers() {
            tableModel.setRowCount(0);
            
            try (Connection conn = DriverManager.getConnection(DB_URL + "?allowPublicKeyRetrieval=true&useSSL=false", DB_USER, DB_PASSWORD)) {
                // Fixed query: Use COALESCE to handle NULL values properly
                String sql = "SELECT u.user_id, u.rfid_uid, u.full_name, u.role, u.status, " +
                           "COALESCE(s.grade, '-') as grade, " +
                           "COALESCE(s.section, '-') as section, " +
                           "COALESCE(s.class_type, '-') as class_type, " +
                           "u.birthday, u.parent_email " +
                           "FROM users u " +
                           "LEFT JOIN students s ON u.user_id = s.user_id " +
                           "ORDER BY u.user_id DESC";
                
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql);
                
                while (rs.next()) {
                    Object[] row = {
                        rs.getInt("user_id"),
                        maskUid(rs.getString("rfid_uid")),
                        rs.getString("full_name"),
                        rs.getString("role"),
                        rs.getString("class_type"),
                        rs.getString("status"),
                        rs.getString("grade"),
                        rs.getString("section"),
                        rs.getDate("birthday") != null ? rs.getDate("birthday").toString() : "-",
                        rs.getString("parent_email") != null ? rs.getString("parent_email") : "-"
                    };
                    tableModel.addRow(row);
                }
            } catch (SQLException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error loading users: " + e.getMessage());
            }
        }
        
        private void searchUsers() {
            String searchText = searchField.getText().toLowerCase();
            tableModel.setRowCount(0);
            
            try (Connection conn = DriverManager.getConnection(DB_URL + "?allowPublicKeyRetrieval=true&useSSL=false", DB_USER, DB_PASSWORD)) {
                // Fixed query: Use COALESCE to handle NULL values
                String sql = "SELECT u.user_id, u.rfid_uid, u.full_name, u.role, u.status, " +
                           "COALESCE(s.grade, '-') as grade, " +
                           "COALESCE(s.section, '-') as section, " +
                           "COALESCE(s.class_type, '-') as class_type, " +
                           "u.birthday, u.parent_email FROM users u " +
                           "LEFT JOIN students s ON u.user_id = s.user_id " +
                           "WHERE LOWER(u.full_name) LIKE ? OR LOWER(u.rfid_uid) LIKE ? " +
                           "ORDER BY u.user_id DESC";
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setString(1, "%" + searchText + "%");
                stmt.setString(2, "%" + searchText + "%");
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    Object[] row = {
                        rs.getInt("user_id"),
                        maskUid(rs.getString("rfid_uid")),
                        rs.getString("full_name"),
                        rs.getString("role"),
                        rs.getString("class_type"),
                        rs.getString("status"),
                        rs.getString("grade"),
                        rs.getString("section"),
                        rs.getDate("birthday") != null ? rs.getDate("birthday").toString() : "-",
                        rs.getString("parent_email") != null ? rs.getString("parent_email") : "-"
                    };
                    tableModel.addRow(row);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        
        private void openAddUserForm() {
            AddUserForm form = new AddUserForm(this);
            form.setVisible(true);
        }
        
        private void editSelectedUser() {
            int selectedRow = userTable.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(this, "Please select a user to edit.");
                return;
            }
            
            int userId = (int) tableModel.getValueAt(selectedRow, 0);
            EditUserForm form = new EditUserForm(this, userId);
            form.setVisible(true);
        }
        
        private void removeSelectedUser() {
            int selectedRow = userTable.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(this, "Please select a user to remove.");
                return;
            }
            
            int userId = (int) tableModel.getValueAt(selectedRow, 0);
            String userName = (String) tableModel.getValueAt(selectedRow, 2);
            
            int confirm = JOptionPane.showConfirmDialog(this,
                "Remove user: " + userName + "?\n\nChoose:\nYES = Soft delete (set inactive)\nNO = Cancel\nCANCEL = Hard delete (permanent)",
                "Confirm Removal",
                JOptionPane.YES_NO_CANCEL_OPTION);
            
            if (confirm == JOptionPane.NO_OPTION) {
                return;
            }
            
            try (Connection conn = DriverManager.getConnection(DB_URL + "?allowPublicKeyRetrieval=true&useSSL=false", DB_USER, DB_PASSWORD)) {
                if (confirm == JOptionPane.YES_OPTION) {
                    // Soft delete
                    String sql = "UPDATE users SET status = 'inactive' WHERE user_id = ?";
                    PreparedStatement stmt = conn.prepareStatement(sql);
                    stmt.setInt(1, userId);
                    stmt.executeUpdate();
                    JOptionPane.showMessageDialog(this, "User set to inactive.");
                } else {
                    // Hard delete
                    String sql = "DELETE FROM users WHERE user_id = ?";
                    PreparedStatement stmt = conn.prepareStatement(sql);
                    stmt.setInt(1, userId);
                    stmt.executeUpdate();
                    JOptionPane.showMessageDialog(this, "User permanently deleted.");
                }
                loadUsers();
            } catch (SQLException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
            }
        }
        
        private void openAttendanceLog() {
            AttendanceLogDialog logDialog = new AttendanceLogDialog(this);
            logDialog.setVisible(true);
        }
    }
    
    // ======================== ATTENDANCE LOG DIALOG CLASS ========================
    
    class AttendanceLogDialog extends JDialog {
        private JTable logTable;
        private DefaultTableModel tableModel;
        private JComboBox<String> userCombo;
        private JTextField dateFromField;
        private JTextField dateToField;
        private JComboBox<String> filterCombo;
        private AdminPanel parent;
        private Timer refreshTimer;
        
        public AttendanceLogDialog(AdminPanel parent) {
            super(parent, "Attendance Log", true);
            this.parent = parent;
            setSize(1200, 600);
            setLocationRelativeTo(parent);
            setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            
            setupUI();
            loadUserList();
            loadAttendanceLog();
            
            // Start auto-refresh timer (every 30 seconds)
            refreshTimer = new Timer(30000, e -> loadAttendanceLog());
            refreshTimer.start();
            
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    if (refreshTimer != null) {
                        refreshTimer.stop();
                    }
                }
            });
        }
        
        private void setupUI() {
            setLayout(new BorderLayout(10, 10));
            
            // Filter panel
            JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
            
            // User filter
            filterPanel.add(new JLabel("Select User:"));
            userCombo = new JComboBox<>();
            userCombo.addItem("All Users");
            filterPanel.add(userCombo);
            
            // Date range
            LocalDate today = LocalDate.now();
            LocalDate firstDayOfMonth = today.withDayOfMonth(1);
            
            filterPanel.add(new JLabel("Date From:"));
            dateFromField = new JTextField(firstDayOfMonth.toString(), 10);
            dateFromField.setToolTipText("YYYY-MM-DD");
            filterPanel.add(dateFromField);
            
            filterPanel.add(new JLabel("To:"));
            dateToField = new JTextField(today.toString(), 10);
            dateToField.setToolTipText("YYYY-MM-DD");
            filterPanel.add(dateToField);
            
            // Status filter
            filterPanel.add(new JLabel("Filter:"));
            filterCombo = new JComboBox<>(new String[]{"All", "Present", "Absent", "Late", "Early Out", "Completed"});
            filterPanel.add(filterCombo);
            
            JButton filterButton = new JButton("Apply Filter");
            filterButton.addActionListener(e -> loadAttendanceLog());
            filterPanel.add(filterButton);
            
            JButton exportButton = new JButton("Export to CSV");
            exportButton.addActionListener(e -> exportToCSV());
            filterPanel.add(exportButton);
            
            JButton refreshButton = new JButton("Refresh Now");
            refreshButton.addActionListener(e -> loadAttendanceLog());
            filterPanel.add(refreshButton);
            
            add(filterPanel, BorderLayout.NORTH);
            
            // Table
            String[] columns = {"Date", "Name", "Role", "Class Type", "Grade/Section", "Time IN", "Time OUT", 
                                "Status", "Late By", "Early Out By", "Total Hours"};
            tableModel = new DefaultTableModel(columns, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            
            logTable = new JTable(tableModel);
            logTable.setFont(new Font("Arial", Font.PLAIN, 12));
            logTable.setRowHeight(25);
            logTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));
            logTable.setAutoCreateRowSorter(true);
            
            JScrollPane scrollPane = new JScrollPane(logTable);
            add(scrollPane, BorderLayout.CENTER);
            
            // Summary panel
            JPanel summaryPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            summaryPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
            add(summaryPanel, BorderLayout.SOUTH);
        }
        
        private void loadUserList() {
            try (Connection conn = DriverManager.getConnection(DB_URL + "?allowPublicKeyRetrieval=true&useSSL=false", DB_USER, DB_PASSWORD)) {
                String sql = "SELECT user_id, full_name, role FROM users WHERE role IN ('student', 'teacher') ORDER BY full_name";
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql);
                
                while (rs.next()) {
                    String displayName = rs.getString("full_name") + " (" + rs.getString("role") + ")";
                    userCombo.addItem(displayName + "|" + rs.getInt("user_id"));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        
        private void loadAttendanceLog() {
            tableModel.setRowCount(0);
            
            try (Connection conn = DriverManager.getConnection(DB_URL + "?allowPublicKeyRetrieval=true&useSSL=false", DB_USER, DB_PASSWORD)) {
                // Fixed query: Use COALESCE to handle NULL values
                StringBuilder sql = new StringBuilder();
                sql.append("SELECT a.date, u.full_name, u.role, ");
                sql.append("COALESCE(s.grade, '-') as grade, ");
                sql.append("COALESCE(s.section, '-') as section, ");
                sql.append("COALESCE(s.class_type, '-') as class_type, ");
                sql.append("a.time_in, a.time_out, ");
                sql.append("COALESCE(a.status, 'Present') as status, ");
                sql.append("a.late_minutes, a.early_out_minutes ");
                sql.append("FROM attendance a ");
                sql.append("JOIN users u ON a.user_id = u.user_id ");
                sql.append("LEFT JOIN students s ON u.user_id = s.user_id ");
                sql.append("WHERE a.date BETWEEN ? AND ? ");
                
                // Get selected user
                String selectedUser = (String) userCombo.getSelectedItem();
                int userId = 0;
                if (selectedUser != null && !selectedUser.equals("All Users")) {
                    String[] parts = selectedUser.split("\\|");
                    if (parts.length > 1) {
                        userId = Integer.parseInt(parts[1]);
                        sql.append("AND a.user_id = ? ");
                    }
                }
                
                sql.append("ORDER BY a.date DESC, u.full_name");
                
                PreparedStatement stmt = conn.prepareStatement(sql.toString());
                stmt.setDate(1, Date.valueOf(dateFromField.getText()));
                stmt.setDate(2, Date.valueOf(dateToField.getText()));
                
                if (userId > 0) {
                    stmt.setInt(3, userId);
                }
                
                ResultSet rs = stmt.executeQuery();
                
                int totalRecords = 0;
                int lateCount = 0;
                int absentCount = 0;
                int presentCount = 0;
                int completedCount = 0;
                
                while (rs.next()) {
                    totalRecords++;
                    Date date = rs.getDate("date");
                    String name = rs.getString("full_name");
                    String role = rs.getString("role");
                    String grade = rs.getString("grade");
                    String section = rs.getString("section");
                    String classType = rs.getString("class_type");
                    Time timeIn = rs.getTime("time_in");
                    Time timeOut = rs.getTime("time_out");
                    String status = rs.getString("status");
                    int lateMinutes = rs.getInt("late_minutes");
                    int earlyOutMinutes = rs.getInt("early_out_minutes");
                    
                    String lateBy = lateMinutes > 0 ? lateMinutes + " mins" : "-";
                    String earlyOutBy = earlyOutMinutes > 0 ? earlyOutMinutes + " mins" : "-";
                    String totalHours = "-";
                    
                    // Count statuses
                    switch (status) {
                        case "Absent":
                            absentCount++;
                            break;
                        case "Present":
                            presentCount++;
                            break;
                        case "Late":
                            lateCount++;
                            break;
                        case "Completed":
                            completedCount++;
                            break;
                    }
                    
                    // Calculate total hours if both times exist
                    if (timeIn != null && timeOut != null) {
                        LocalTime inTime = timeIn.toLocalTime();
                        LocalTime outTime = timeOut.toLocalTime();
                        long hours = java.time.Duration.between(inTime, outTime).toHours();
                        long minutes = java.time.Duration.between(inTime, outTime).toMinutes() % 60;
                        totalHours = String.format("%d:%02d", hours, minutes);
                    }
                    
                    String gradeSection = "-";
                    if (!grade.equals("-") && !section.equals("-")) {
                        gradeSection = "Grade " + grade + " - " + section;
                    }
                    
                    Object[] row = {
                        date.toString(),
                        name,
                        role,
                        classType,
                        gradeSection,
                        timeIn != null ? timeIn.toString() : "-",
                        timeOut != null ? timeOut.toString() : "-",
                        status,
                        lateBy,
                        earlyOutBy,
                        totalHours
                    };
                    
                    // Apply filter
                    String filter = (String) filterCombo.getSelectedItem();
                    if (filter.equals("All") || 
                        (filter.equals("Present") && status.equals("Present")) ||
                        (filter.equals("Absent") && status.equals("Absent")) ||
                        (filter.equals("Late") && status.equals("Late")) ||
                        (filter.equals("Early Out") && earlyOutMinutes > 0) ||
                        (filter.equals("Completed") && status.equals("Completed"))) {
                        tableModel.addRow(row);
                    }
                }
                
                // Update summary
                updateSummary(totalRecords, presentCount, absentCount, lateCount, completedCount);
                
            } catch (SQLException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error loading attendance log: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(this, "Invalid date format! Please use YYYY-MM-DD");
            }
        }
        
        private void updateSummary(int total, int present, int absent, int late, int completed) {
            JPanel summaryPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            summaryPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
            
            JLabel totalLabel = new JLabel("Total Records: " + total);
            totalLabel.setFont(new Font("Arial", Font.BOLD, 12));
            summaryPanel.add(totalLabel);
            
            JLabel presentLabel = new JLabel("  • Present: " + present);
            presentLabel.setFont(new Font("Arial", Font.PLAIN, 12));
            presentLabel.setForeground(Color.GREEN.darker());
            summaryPanel.add(presentLabel);
            
            JLabel absentLabel = new JLabel("  • Absent: " + absent);
            absentLabel.setFont(new Font("Arial", Font.PLAIN, 12));
            absentLabel.setForeground(Color.RED);
            summaryPanel.add(absentLabel);
            
            JLabel lateLabel = new JLabel("  • Late: " + late);
            lateLabel.setFont(new Font("Arial", Font.PLAIN, 12));
            lateLabel.setForeground(Color.ORANGE.darker());
            summaryPanel.add(lateLabel);
            
            JLabel completedLabel = new JLabel("  • Completed: " + completed);
            completedLabel.setFont(new Font("Arial", Font.PLAIN, 12));
            completedLabel.setForeground(Color.BLUE);
            summaryPanel.add(completedLabel);
            
            // Remove old summary and add new one
            Container contentPane = getContentPane();
            if (contentPane.getComponentCount() > 1) {
                contentPane.remove(1);
            }
            contentPane.add(summaryPanel, BorderLayout.SOUTH);
            revalidate();
            repaint();
        }
        
        private void exportToCSV() {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Save Attendance Log");
            fileChooser.setSelectedFile(new File("attendance_log_" + LocalDate.now() + ".csv"));
            
            int userSelection = fileChooser.showSaveDialog(this);
            if (userSelection == JFileChooser.APPROVE_OPTION) {
                File fileToSave = fileChooser.getSelectedFile();
                
                try {
                    StringBuilder csv = new StringBuilder();
                    
                    // Add headers
                    for (int i = 0; i < tableModel.getColumnCount(); i++) {
                        csv.append("\"").append(tableModel.getColumnName(i)).append("\"");
                        if (i < tableModel.getColumnCount() - 1) {
                            csv.append(",");
                        }
                    }
                    csv.append("\n");
                    
                    // Add data
                    for (int i = 0; i < tableModel.getRowCount(); i++) {
                        for (int j = 0; j < tableModel.getColumnCount(); j++) {
                            Object value = tableModel.getValueAt(i, j);
                            csv.append("\"").append(value != null ? value.toString().replace("\"", "\"\"") : "").append("\"");
                            if (j < tableModel.getColumnCount() - 1) {
                                csv.append(",");
                            }
                        }
                        csv.append("\n");
                    }
                    
                    Files.write(fileToSave.toPath(), csv.toString().getBytes());
                    JOptionPane.showMessageDialog(this, "Attendance log exported successfully!");
                    
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(this, "Error exporting to CSV: " + e.getMessage());
                }
            }
        }
    }
    
    // ======================== ADD USER FORM CLASS ========================
    
    class AddUserForm extends JDialog {
        private JTextField nameField;
        private JTextField rfidField;
        private JComboBox<String> roleCombo;
        private JTextField gradeField;
        private JTextField sectionField;
        private JComboBox<String> classTypeCombo;
        private JTextField parentEmailField;
        private JTextField birthdayField;
        private JLabel photoLabel;
        private String selectedPhotoPath = null;
        private AdminPanel parent;
        
        public AddUserForm(AdminPanel parent) {
            super(parent, "Add New User", true);
            this.parent = parent;
            setSize(450, 720);
            setLocationRelativeTo(parent);
            setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            
            setupFormUI();
        }
        
        private void setupFormUI() {
            setLayout(new BorderLayout(10, 10));
            
            JPanel formPanel = new JPanel(new GridBagLayout());
            formPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(5, 5, 5, 5);
            
            // Full Name
            gbc.gridx = 0; gbc.gridy = 0;
            formPanel.add(new JLabel("Full Name:"), gbc);
            gbc.gridx = 1;
            nameField = new JTextField(20);
            formPanel.add(nameField, gbc);
            
            // Student ID (RFID UID)
            gbc.gridx = 0; gbc.gridy = 1;
            formPanel.add(new JLabel("Student ID:"), gbc);
            gbc.gridx = 1;
            rfidField = new JTextField(20);
            formPanel.add(rfidField, gbc);
            
            // Role
            gbc.gridx = 0; gbc.gridy = 2;
            formPanel.add(new JLabel("Role:"), gbc);
            gbc.gridx = 1;
            roleCombo = new JComboBox<>(new String[]{"student", "teacher"});
            roleCombo.addActionListener(e -> toggleStudentFields());
            formPanel.add(roleCombo, gbc);
            
            // Class Type
            gbc.gridx = 0; gbc.gridy = 3;
            formPanel.add(new JLabel("Class Type:"), gbc);
            gbc.gridx = 1;
            classTypeCombo = new JComboBox<>(new String[]{"morning", "afternoon"});
            formPanel.add(classTypeCombo, gbc);
            
            // Grade
            gbc.gridx = 0; gbc.gridy = 4;
            formPanel.add(new JLabel("Grade:"), gbc);
            gbc.gridx = 1;
            gradeField = new JTextField(20);
            formPanel.add(gradeField, gbc);
            
            // Section
            gbc.gridx = 0; gbc.gridy = 5;
            formPanel.add(new JLabel("Section:"), gbc);
            gbc.gridx = 1;
            sectionField = new JTextField(20);
            formPanel.add(sectionField, gbc);
            
            // Birthday
            gbc.gridx = 0; gbc.gridy = 6;
            formPanel.add(new JLabel("Birthday (YYYY-MM-DD):"), gbc);
            gbc.gridx = 1;
            birthdayField = new JTextField(20);
            birthdayField.setToolTipText("Format: YYYY-MM-DD (e.g., 2010-05-15)");
            formPanel.add(birthdayField, gbc);
            
            // Parent Email
            gbc.gridx = 0; gbc.gridy = 7;
            formPanel.add(new JLabel("Parent Email:"), gbc);
            gbc.gridx = 1;
            parentEmailField = new JTextField(20);
            formPanel.add(parentEmailField, gbc);
            
            // Photo
            gbc.gridx = 0; gbc.gridy = 8;
            formPanel.add(new JLabel("Photo:"), gbc);
            gbc.gridx = 1;
            JPanel photoPanel = new JPanel(new BorderLayout(5, 5));
            photoLabel = new JLabel("No photo selected");
            photoLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
            photoLabel.setPreferredSize(new Dimension(100, 100));
            photoLabel.setHorizontalAlignment(SwingConstants.CENTER);
            photoPanel.add(photoLabel, BorderLayout.CENTER);
            
            JButton browseButton = new JButton("Browse...");
            browseButton.addActionListener(e -> selectPhoto());
            photoPanel.add(browseButton, BorderLayout.SOUTH);
            formPanel.add(photoPanel, gbc);
            
            add(formPanel, BorderLayout.CENTER);
            
            // Buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton saveButton = new JButton("💾 Save");
            saveButton.addActionListener(e -> saveUser());
            buttonPanel.add(saveButton);
            
            JButton cancelButton = new JButton("Cancel");
            cancelButton.addActionListener(e -> dispose());
            buttonPanel.add(cancelButton);
            
            add(buttonPanel, BorderLayout.SOUTH);
        }
        
        private void toggleStudentFields() {
            boolean isStudent = "student".equals(roleCombo.getSelectedItem());
            gradeField.setEnabled(isStudent);
            sectionField.setEnabled(isStudent);
            parentEmailField.setEnabled(isStudent);
            classTypeCombo.setEnabled(isStudent);
        }
        
        private void selectPhoto() {
            JFileChooser fileChooser = new JFileChooser();
            FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "Image files", "jpg", "jpeg", "png", "gif");
            fileChooser.setFileFilter(filter);
            
            int result = fileChooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                try {
                    // Copy to photos directory
                    String fileName = System.currentTimeMillis() + "_" + selectedFile.getName();
                    File destFile = new File(PHOTO_DIR + fileName);
                    Files.copy(selectedFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    
                    selectedPhotoPath = destFile.getPath();
                    
                    // Show preview
                    ImageIcon icon = new ImageIcon(selectedPhotoPath);
                    Image img = icon.getImage().getScaledInstance(100, 100, Image.SCALE_SMOOTH);
                    photoLabel.setIcon(new ImageIcon(img));
                    photoLabel.setText("");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(this, "Error copying photo: " + ex.getMessage());
                }
            }
        }
        
        private void saveUser() {
            String name = nameField.getText().trim();
            String rfidUid = rfidField.getText().trim();
            String role = (String) roleCombo.getSelectedItem();
            String classType = (String) classTypeCombo.getSelectedItem();
            String grade = gradeField.getText().trim();
            String section = sectionField.getText().trim();
            String parentEmail = parentEmailField.getText().trim();
            String birthday = birthdayField.getText().trim();
            
            if (name.isEmpty() || rfidUid.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Name and Student ID are required!");
                return;
            }
            
            if ("student".equals(role) && (grade.isEmpty() || section.isEmpty())) {
                JOptionPane.showMessageDialog(this, "Grade and Section are required for students!");
                return;
            }
            
            // Validate birthday format
            Date birthdayDate = null;
            if (!birthday.isEmpty()) {
                try {
                    birthdayDate = Date.valueOf(birthday);
                } catch (IllegalArgumentException e) {
                    JOptionPane.showMessageDialog(this, "Invalid birthday format! Use YYYY-MM-DD");
                    return;
                }
            }
            
            try (Connection conn = DriverManager.getConnection(DB_URL + "?allowPublicKeyRetrieval=true&useSSL=false", DB_USER, DB_PASSWORD)) {
                
                // Check if RFID already exists
                String checkSql = "SELECT * FROM users WHERE rfid_uid = ?";
                PreparedStatement checkStmt = conn.prepareStatement(checkSql);
                checkStmt.setString(1, rfidUid);
                ResultSet rs = checkStmt.executeQuery();
                
                if (rs.next()) {
                    JOptionPane.showMessageDialog(this, "This Student ID is already registered!");
                    return;
                }
                
                // Insert user
                String insertUserSql = "INSERT INTO users (rfid_uid, full_name, role, status, photo_path, parent_email, birthday) " +
                                     "VALUES (?, ?, ?, 'active', ?, ?, ?)";
                PreparedStatement userStmt = conn.prepareStatement(insertUserSql, Statement.RETURN_GENERATED_KEYS);
                userStmt.setString(1, rfidUid);
                userStmt.setString(2, name);
                userStmt.setString(3, role);
                userStmt.setString(4, selectedPhotoPath);
                userStmt.setString(5, parentEmail.isEmpty() ? null : parentEmail);
                userStmt.setDate(6, birthdayDate);
                userStmt.executeUpdate();
                
                // Get generated user ID
                ResultSet generatedKeys = userStmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    int userId = generatedKeys.getInt(1);
                    
                    // Insert student info if student
                    if ("student".equals(role)) {
                        String insertStudentSql = "INSERT INTO students (user_id, grade, section, class_type) VALUES (?, ?, ?, ?)";
                        PreparedStatement studentStmt = conn.prepareStatement(insertStudentSql);
                        studentStmt.setInt(1, userId);
                        studentStmt.setString(2, grade);
                        studentStmt.setString(3, section);
                        studentStmt.setString(4, classType);
                        studentStmt.executeUpdate();
                    }
                }
                
                JOptionPane.showMessageDialog(this, "User added successfully!");
                parent.loadUsers();
                dispose();
                
            } catch (SQLException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
            }
        }
    }
    
    // ======================== EDIT USER FORM CLASS ========================
    
    class EditUserForm extends JDialog {
        private JTextField nameField;
        private JTextField rfidField;
        private JComboBox<String> roleCombo;
        private JComboBox<String> statusCombo;
        private JComboBox<String> classTypeCombo;
        private JTextField gradeField;
        private JTextField sectionField;
        private JTextField parentEmailField;
        private JTextField birthdayField;
        private JLabel photoLabel;
        private String selectedPhotoPath = null;
        private AdminPanel parent;
        private int userId;
        
        public EditUserForm(AdminPanel parent, int userId) {
            super(parent, "Edit User", true);
            this.parent = parent;
            this.userId = userId;
            setSize(450, 770);
            setLocationRelativeTo(parent);
            setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            
            setupFormUI();
            loadUserData();
        }
        
        private void setupFormUI() {
            setLayout(new BorderLayout(10, 10));
            
            JPanel formPanel = new JPanel(new GridBagLayout());
            formPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(5, 5, 5, 5);
            
            // Full Name
            gbc.gridx = 0; gbc.gridy = 0;
            formPanel.add(new JLabel("Full Name:"), gbc);
            gbc.gridx = 1;
            nameField = new JTextField(20);
            formPanel.add(nameField, gbc);
            
            // Student ID (RFID UID) - Read only
            gbc.gridx = 0; gbc.gridy = 1;
            formPanel.add(new JLabel("Student ID:"), gbc);
            gbc.gridx = 1;
            rfidField = new JTextField(20);
            rfidField.setEditable(false);
            rfidField.setBackground(Color.LIGHT_GRAY);
            formPanel.add(rfidField, gbc);
            
            // Role
            gbc.gridx = 0; gbc.gridy = 2;
            formPanel.add(new JLabel("Role:"), gbc);
            gbc.gridx = 1;
            roleCombo = new JComboBox<>(new String[]{"student", "teacher"});
            roleCombo.addActionListener(e -> toggleStudentFields());
            formPanel.add(roleCombo, gbc);
            
            // Status
            gbc.gridx = 0; gbc.gridy = 3;
            formPanel.add(new JLabel("Status:"), gbc);
            gbc.gridx = 1;
            statusCombo = new JComboBox<>(new String[]{"active", "inactive"});
            formPanel.add(statusCombo, gbc);
            
            // Class Type
            gbc.gridx = 0; gbc.gridy = 4;
            formPanel.add(new JLabel("Class Type:"), gbc);
            gbc.gridx = 1;
            classTypeCombo = new JComboBox<>(new String[]{"morning", "afternoon"});
            formPanel.add(classTypeCombo, gbc);
            
            // Grade
            gbc.gridx = 0; gbc.gridy = 5;
            formPanel.add(new JLabel("Grade:"), gbc);
            gbc.gridx = 1;
            gradeField = new JTextField(20);
            formPanel.add(gradeField, gbc);
            
            // Section
            gbc.gridx = 0; gbc.gridy = 6;
            formPanel.add(new JLabel("Section:"), gbc);
            gbc.gridx = 1;
            sectionField = new JTextField(20);
            formPanel.add(sectionField, gbc);
            
            // Birthday
            gbc.gridx = 0; gbc.gridy = 7;
            formPanel.add(new JLabel("Birthday (YYYY-MM-DD):"), gbc);
            gbc.gridx = 1;
            birthdayField = new JTextField(20);
            birthdayField.setToolTipText("Format: YYYY-MM-DD (e.g., 2010-05-15)");
            formPanel.add(birthdayField, gbc);
            
            // Parent Email
            gbc.gridx = 0; gbc.gridy = 8;
            formPanel.add(new JLabel("Parent Email:"), gbc);
            gbc.gridx = 1;
            parentEmailField = new JTextField(20);
            formPanel.add(parentEmailField, gbc);
            
            // Photo
            gbc.gridx = 0; gbc.gridy = 9;
            formPanel.add(new JLabel("Photo:"), gbc);
            gbc.gridx = 1;
            JPanel photoPanel = new JPanel(new BorderLayout(5, 5));
            photoLabel = new JLabel("No photo");
            photoLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY));
            photoLabel.setPreferredSize(new Dimension(100, 100));
            photoLabel.setHorizontalAlignment(SwingConstants.CENTER);
            photoPanel.add(photoLabel, BorderLayout.CENTER);
            
            JButton browseButton = new JButton("Change Photo...");
            browseButton.addActionListener(e -> selectPhoto());
            photoPanel.add(browseButton, BorderLayout.SOUTH);
            formPanel.add(photoPanel, gbc);
            
            add(formPanel, BorderLayout.CENTER);
            
            // Buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton saveButton = new JButton("💾 Update");
            saveButton.addActionListener(e -> updateUser());
            buttonPanel.add(saveButton);
            
            JButton cancelButton = new JButton("Cancel");
            cancelButton.addActionListener(e -> dispose());
            buttonPanel.add(cancelButton);
            
            add(buttonPanel, BorderLayout.SOUTH);
        }
        
        private void loadUserData() {
            try (Connection conn = DriverManager.getConnection(DB_URL + "?allowPublicKeyRetrieval=true&useSSL=false", DB_USER, DB_PASSWORD)) {
                String sql = "SELECT u.*, s.grade, s.section FROM users u " +
                           "LEFT JOIN students s ON u.user_id = s.user_id " +
                           "WHERE u.user_id = ?";
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setInt(1, userId);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    nameField.setText(rs.getString("full_name"));
                    rfidField.setText(rs.getString("rfid_uid"));
                    roleCombo.setSelectedItem(rs.getString("role"));
                    statusCombo.setSelectedItem(rs.getString("status"));
                    
                    String grade = rs.getString("grade");
                    String section = rs.getString("section");
                    String parentEmail = rs.getString("parent_email");
                    Date birthday = rs.getDate("birthday");
                    
                    if (grade != null) gradeField.setText(grade);
                    if (section != null) sectionField.setText(section);
                    if (parentEmail != null) parentEmailField.setText(parentEmail);
                    if (birthday != null) birthdayField.setText(birthday.toString());
                    
                    selectedPhotoPath = rs.getString("photo_path");
                    if (selectedPhotoPath != null && new File(selectedPhotoPath).exists()) {
                        ImageIcon icon = new ImageIcon(selectedPhotoPath);
                        Image img = icon.getImage().getScaledInstance(100, 100, Image.SCALE_SMOOTH);
                        photoLabel.setIcon(new ImageIcon(img));
                        photoLabel.setText("");
                    }
                    
                    // Try to get class_type if column exists
                    try {
                        String classSql = "SELECT class_type FROM students WHERE user_id = ?";
                        PreparedStatement classStmt = conn.prepareStatement(classSql);
                        classStmt.setInt(1, userId);
                        ResultSet classRs = classStmt.executeQuery();
                        if (classRs.next()) {
                            String classType = classRs.getString("class_type");
                            if (classType != null) {
                                classTypeCombo.setSelectedItem(classType);
                            }
                        }
                    } catch (SQLException e) {
                        // Column doesn't exist, use default
                        classTypeCombo.setSelectedItem("morning");
                    }
                    
                    toggleStudentFields();
                }
            } catch (SQLException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error loading user data: " + e.getMessage());
            }
        }
        
        private void toggleStudentFields() {
            boolean isStudent = "student".equals(roleCombo.getSelectedItem());
            gradeField.setEnabled(isStudent);
            sectionField.setEnabled(isStudent);
            parentEmailField.setEnabled(isStudent);
            classTypeCombo.setEnabled(isStudent);
        }
        
        private void selectPhoto() {
            JFileChooser fileChooser = new JFileChooser();
            FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "Image files", "jpg", "jpeg", "png", "gif");
            fileChooser.setFileFilter(filter);
            
            int result = fileChooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                try {
                    // Copy to photos directory
                    String fileName = System.currentTimeMillis() + "_" + selectedFile.getName();
                    File destFile = new File(PHOTO_DIR + fileName);
                    Files.copy(selectedFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    
                    selectedPhotoPath = destFile.getPath();
                    
                    // Show preview
                    ImageIcon icon = new ImageIcon(selectedPhotoPath);
                    Image img = icon.getImage().getScaledInstance(100, 100, Image.SCALE_SMOOTH);
                    photoLabel.setIcon(new ImageIcon(img));
                    photoLabel.setText("");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(this, "Error copying photo: " + ex.getMessage());
                }
            }
        }
        
        private void updateUser() {
            String name = nameField.getText().trim();
            String role = (String) roleCombo.getSelectedItem();
            String status = (String) statusCombo.getSelectedItem();
            String classType = (String) classTypeCombo.getSelectedItem();
            String grade = gradeField.getText().trim();
            String section = sectionField.getText().trim();
            String parentEmail = parentEmailField.getText().trim();
            String birthday = birthdayField.getText().trim();
            
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Name is required!");
                return;
            }
            
            if ("student".equals(role) && (grade.isEmpty() || section.isEmpty())) {
                JOptionPane.showMessageDialog(this, "Grade and Section are required for students!");
                return;
            }
            
            // Validate birthday format
            Date birthdayDate = null;
            if (!birthday.isEmpty()) {
                try {
                    birthdayDate = Date.valueOf(birthday);
                } catch (IllegalArgumentException e) {
                    JOptionPane.showMessageDialog(this, "Invalid birthday format! Use YYYY-MM-DD");
                    return;
                }
            }
            
            try (Connection conn = DriverManager.getConnection(DB_URL + "?allowPublicKeyRetrieval=true&useSSL=false", DB_USER, DB_PASSWORD)) {
                
                // Update user
                String updateUserSql = "UPDATE users SET full_name = ?, role = ?, status = ?, photo_path = ?, parent_email = ?, birthday = ? WHERE user_id = ?";
                PreparedStatement userStmt = conn.prepareStatement(updateUserSql);
                userStmt.setString(1, name);
                userStmt.setString(2, role);
                userStmt.setString(3, status);
                userStmt.setString(4, selectedPhotoPath);
                userStmt.setString(5, parentEmail.isEmpty() ? null : parentEmail);
                userStmt.setDate(6, birthdayDate);
                userStmt.setInt(7, userId);
                userStmt.executeUpdate();
                
                // Update or insert student info if student
                if ("student".equals(role)) {
                    // Check if student record exists
                    String checkSql = "SELECT * FROM students WHERE user_id = ?";
                    PreparedStatement checkStmt = conn.prepareStatement(checkSql);
                    checkStmt.setInt(1, userId);
                    ResultSet rs = checkStmt.executeQuery();
                    
                    if (rs.next()) {
                        // Update existing - try with class_type first
                        try {
                            String updateStudentSql = "UPDATE students SET grade = ?, section = ?, class_type = ? WHERE user_id = ?";
                            PreparedStatement studentStmt = conn.prepareStatement(updateStudentSql);
                            studentStmt.setString(1, grade);
                            studentStmt.setString(2, section);
                            studentStmt.setString(3, classType);
                            studentStmt.setInt(4, userId);
                            studentStmt.executeUpdate();
                        } catch (SQLException e) {
                            // class_type column doesn't exist
                            String updateStudentSql = "UPDATE students SET grade = ?, section = ? WHERE user_id = ?";
                            PreparedStatement studentStmt = conn.prepareStatement(updateStudentSql);
                            studentStmt.setString(1, grade);
                            studentStmt.setString(2, section);
                            studentStmt.setInt(3, userId);
                            studentStmt.executeUpdate();
                        }
                    } else {
                        // Insert new - try with class_type first
                        try {
                            String insertStudentSql = "INSERT INTO students (user_id, grade, section, class_type) VALUES (?, ?, ?, ?)";
                            PreparedStatement studentStmt = conn.prepareStatement(insertStudentSql);
                            studentStmt.setInt(1, userId);
                            studentStmt.setString(2, grade);
                            studentStmt.setString(3, section);
                            studentStmt.setString(4, classType);
                            studentStmt.executeUpdate();
                        } catch (SQLException e) {
                            // class_type column doesn't exist
                            String insertStudentSql = "INSERT INTO students (user_id, grade, section) VALUES (?, ?, ?)";
                            PreparedStatement studentStmt = conn.prepareStatement(insertStudentSql);
                            studentStmt.setInt(1, userId);
                            studentStmt.setString(2, grade);
                            studentStmt.setString(3, section);
                            studentStmt.executeUpdate();
                        }
                    }
                } else {
                    // Delete student record if role changed to teacher
                    String deleteSql = "DELETE FROM students WHERE user_id = ?";
                    PreparedStatement deleteStmt = conn.prepareStatement(deleteSql);
                    deleteStmt.setInt(1, userId);
                    deleteStmt.executeUpdate();
                }
                
                JOptionPane.showMessageDialog(this, "User updated successfully!");
                parent.loadUsers();
                dispose();
                
            } catch (SQLException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
            }
        }
    }
    
    // ======================== MAIN METHOD ========================
    
    public static void main(String[] args) {
        // Set look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        SwingUtilities.invokeLater(() -> new RFIDAttendanceSystem());
    }
}
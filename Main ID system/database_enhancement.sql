-- =====================================================
-- RFID ATTENDANCE SYSTEM - ENHANCED DATABASE SCHEMA
-- =====================================================

USE rfid_attendance;

-- =====================================================
-- 1. AUDIT LOGGING TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS audit_logs (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT,
    action VARCHAR(100) NOT NULL,
    details TEXT,
    ip_address VARCHAR(50),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_timestamp (timestamp),
    INDEX idx_user_id (user_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- 2. NOTIFICATION QUEUE TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS notifications (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    recipient_email VARCHAR(100),
    recipient_phone VARCHAR(20),
    type VARCHAR(50) NOT NULL,
    subject VARCHAR(200),
    message TEXT NOT NULL,
    sent_at TIMESTAMP NULL,
    status VARCHAR(20) DEFAULT 'pending',
    retry_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_user_id (user_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- 3. ATTENDANCE EXCEPTIONS (Excuses, Leave Requests)
-- =====================================================
CREATE TABLE IF NOT EXISTS attendance_exceptions (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    exception_date DATE NOT NULL,
    reason VARCHAR(255) NOT NULL,
    exception_type VARCHAR(50) DEFAULT 'excuse',
    submitted_by INT,
    approved_by INT,
    status VARCHAR(20) DEFAULT 'pending',
    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP NULL,
    notes TEXT,
    INDEX idx_date (exception_date),
    INDEX idx_status (status),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (approved_by) REFERENCES users(user_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- 4. SYSTEM SETTINGS TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS system_settings (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value TEXT,
    description VARCHAR(255),
    updated_by INT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (updated_by) REFERENCES users(user_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- 5. SHIFT SCHEDULES TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS shifts (
    id INT PRIMARY KEY AUTO_INCREMENT,
    shift_name VARCHAR(50) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    late_threshold_minutes INT DEFAULT 15,
    early_departure_threshold_minutes INT DEFAULT 30,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- 6. HOLIDAYS CALENDAR TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS holidays (
    id INT PRIMARY KEY AUTO_INCREMENT,
    holiday_date DATE NOT NULL,
    holiday_name VARCHAR(100) NOT NULL,
    is_recurring BOOLEAN DEFAULT FALSE,
    holiday_type VARCHAR(50) DEFAULT 'public',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_holiday (holiday_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- 7. ATTENDANCE STATISTICS (Cached/Precomputed)
-- =====================================================
CREATE TABLE IF NOT EXISTS attendance_statistics (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    period_type VARCHAR(20) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_days INT DEFAULT 0,
    present_days INT DEFAULT 0,
    absent_days INT DEFAULT 0,
    late_days INT DEFAULT 0,
    early_departure_days INT DEFAULT 0,
    attendance_rate DECIMAL(5,2),
    computed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_stat (user_id, period_type, period_start, period_end),
    INDEX idx_period (period_type, period_start),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- 8. REPORT TEMPLATES TABLE
-- =====================================================
CREATE TABLE IF NOT EXISTS report_templates (
    id INT PRIMARY KEY AUTO_INCREMENT,
    template_name VARCHAR(100) NOT NULL,
    template_type VARCHAR(50) NOT NULL,
    template_content TEXT,
    created_by INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    FOREIGN KEY (created_by) REFERENCES users(user_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- 9. USER SESSIONS (For tracking admin logins)
-- =====================================================
CREATE TABLE IF NOT EXISTS user_sessions (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    session_token VARCHAR(255) UNIQUE NOT NULL,
    ip_address VARCHAR(50),
    user_agent VARCHAR(255),
    login_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_activity TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    logout_at TIMESTAMP NULL,
    is_active BOOLEAN DEFAULT TRUE,
    INDEX idx_token (session_token),
    INDEX idx_active (is_active, last_activity),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- 10. LOGIN ATTEMPTS (Security tracking)
-- =====================================================
CREATE TABLE IF NOT EXISTS login_attempts (
    id INT PRIMARY KEY AUTO_INCREMENT,
    rfid_uid VARCHAR(50),
    ip_address VARCHAR(50),
    attempt_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    success BOOLEAN DEFAULT FALSE,
    failure_reason VARCHAR(100),
    INDEX idx_rfid (rfid_uid, attempt_time),
    INDEX idx_ip (ip_address, attempt_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================
-- ENHANCE EXISTING TABLES
-- =====================================================

-- Add new columns to users table if they don't exist
ALTER TABLE users 
    ADD COLUMN IF NOT EXISTS phone VARCHAR(20) AFTER parent_email,
    ADD COLUMN IF NOT EXISTS address VARCHAR(255) AFTER phone,
    ADD COLUMN IF NOT EXISTS emergency_contact VARCHAR(100) AFTER address,
    ADD COLUMN IF NOT EXISTS emergency_phone VARCHAR(20) AFTER emergency_contact,
    ADD COLUMN IF NOT EXISTS blood_type VARCHAR(5) AFTER emergency_phone,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP AFTER birthday,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at,
    ADD COLUMN IF NOT EXISTS last_seen TIMESTAMP NULL AFTER updated_at;

-- Add indexes for better performance
ALTER TABLE users
    ADD INDEX IF NOT EXISTS idx_role (role),
    ADD INDEX IF NOT EXISTS idx_status (status),
    ADD INDEX IF NOT EXISTS idx_name (full_name);

-- Enhance attendance table
ALTER TABLE attendance
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'present' AFTER time_out,
    ADD COLUMN IF NOT EXISTS is_late BOOLEAN DEFAULT FALSE AFTER status,
    ADD COLUMN IF NOT EXISTS is_early_departure BOOLEAN DEFAULT FALSE AFTER is_late,
    ADD COLUMN IF NOT EXISTS notes TEXT AFTER is_early_departure,
    ADD COLUMN IF NOT EXISTS shift_id INT AFTER notes,
    ADD INDEX IF NOT EXISTS idx_date (date),
    ADD INDEX IF NOT EXISTS idx_status (status);

-- Enhance students table
ALTER TABLE students
    ADD COLUMN IF NOT EXISTS guardian_name VARCHAR(100) AFTER section,
    ADD COLUMN IF NOT EXISTS guardian_phone VARCHAR(20) AFTER guardian_name,
    ADD COLUMN IF NOT EXISTS guardian_email VARCHAR(100) AFTER guardian_phone,
    ADD COLUMN IF NOT EXISTS enrollment_date DATE AFTER guardian_email,
    ADD COLUMN IF NOT EXISTS graduation_year YEAR AFTER enrollment_date;

-- =====================================================
-- INSERT DEFAULT SETTINGS
-- =====================================================

INSERT IGNORE INTO system_settings (setting_key, setting_value, description) VALUES
('system.version', '2.0.0', 'System version number'),
('attendance.late_threshold', '15', 'Minutes after start time considered late'),
('attendance.early_departure_threshold', '30', 'Minutes before end time considered early'),
('notification.email_enabled', 'true', 'Enable email notifications'),
('notification.sms_enabled', 'false', 'Enable SMS notifications'),
('theme.dark_mode', 'false', 'Enable dark mode'),
('backup.auto_enabled', 'true', 'Enable automatic backups'),
('backup.time', '02:00', 'Time for automatic backup'),
('security.session_timeout', '30', 'Session timeout in minutes'),
('security.max_login_attempts', '5', 'Maximum failed login attempts');

-- Insert default shift
INSERT IGNORE INTO shifts (id, shift_name, start_time, end_time, late_threshold_minutes) VALUES
(1, 'Regular Day', '08:00:00', '17:00:00', 15);

-- =====================================================
-- CREATE VIEWS FOR COMMON QUERIES
-- =====================================================

-- Daily attendance summary
CREATE OR REPLACE VIEW v_daily_attendance_summary AS
SELECT 
    a.date,
    COUNT(DISTINCT a.user_id) as total_checked_in,
    SUM(CASE WHEN a.time_in IS NOT NULL THEN 1 ELSE 0 END) as total_time_in,
    SUM(CASE WHEN a.time_out IS NOT NULL THEN 1 ELSE 0 END) as total_time_out,
    SUM(CASE WHEN a.is_late = TRUE THEN 1 ELSE 0 END) as total_late,
    (SELECT COUNT(*) FROM users WHERE role IN ('student', 'teacher') AND status = 'active') as total_active_users
FROM attendance a
GROUP BY a.date
ORDER BY a.date DESC;

-- Student attendance overview
CREATE OR REPLACE VIEW v_student_attendance_overview AS
SELECT 
    u.user_id,
    u.full_name,
    u.rfid_uid,
    s.grade,
    s.section,
    COUNT(DISTINCT a.date) as days_present,
    SUM(CASE WHEN a.is_late = TRUE THEN 1 ELSE 0 END) as days_late,
    u.last_seen,
    u.status
FROM users u
LEFT JOIN students s ON u.user_id = s.user_id
LEFT JOIN attendance a ON u.user_id = a.user_id
WHERE u.role = 'student'
GROUP BY u.user_id, u.full_name, u.rfid_uid, s.grade, s.section, u.last_seen, u.status;

-- =====================================================
-- STORED PROCEDURES
-- =====================================================

DELIMITER //

-- Procedure to get attendance statistics
CREATE PROCEDURE IF NOT EXISTS sp_get_attendance_stats(
    IN p_user_id INT,
    IN p_start_date DATE,
    IN p_end_date DATE
)
BEGIN
    SELECT 
        COUNT(*) as total_days,
        SUM(CASE WHEN time_in IS NOT NULL THEN 1 ELSE 0 END) as present_days,
        SUM(CASE WHEN is_late = TRUE THEN 1 ELSE 0 END) as late_days,
        ROUND((SUM(CASE WHEN time_in IS NOT NULL THEN 1 ELSE 0 END) / COUNT(*)) * 100, 2) as attendance_rate
    FROM attendance
    WHERE user_id = p_user_id
    AND date BETWEEN p_start_date AND p_end_date;
END //

-- Procedure to mark attendance
CREATE PROCEDURE IF NOT EXISTS sp_mark_attendance(
    IN p_user_id INT,
    IN p_action VARCHAR(10)
)
BEGIN
    DECLARE v_date DATE DEFAULT CURDATE();
    DECLARE v_time TIME DEFAULT CURTIME();
    DECLARE v_is_late BOOLEAN DEFAULT FALSE;
    DECLARE v_shift_start TIME;
    
    -- Get shift start time
    SELECT start_time INTO v_shift_start FROM shifts WHERE is_active = TRUE LIMIT 1;
    
    -- Check if late
    IF p_action = 'IN' AND v_time > ADDTIME(v_shift_start, '00:15:00') THEN
        SET v_is_late = TRUE;
    END IF;
    
    -- Insert or update attendance
    INSERT INTO attendance (user_id, date, time_in, is_late)
    VALUES (p_user_id, v_date, v_time, v_is_late)
    ON DUPLICATE KEY UPDATE
        time_out = IF(p_action = 'OUT', v_time, time_out),
        is_late = v_is_late;
    
    -- Update last seen
    UPDATE users SET last_seen = NOW() WHERE user_id = p_user_id;
END //

DELIMITER ;

-- =====================================================
-- TRIGGERS FOR AUDIT LOGGING
-- =====================================================

DELIMITER //

CREATE TRIGGER IF NOT EXISTS tr_users_after_insert
AFTER INSERT ON users
FOR EACH ROW
BEGIN
    INSERT INTO audit_logs (user_id, action, details)
    VALUES (NEW.user_id, 'USER_CREATED', CONCAT('New user created: ', NEW.full_name));
END //

CREATE TRIGGER IF NOT EXISTS tr_users_after_update
AFTER UPDATE ON users
FOR EACH ROW
BEGIN
    INSERT INTO audit_logs (user_id, action, details)
    VALUES (NEW.user_id, 'USER_UPDATED', CONCAT('User updated: ', NEW.full_name));
END //

CREATE TRIGGER IF NOT EXISTS tr_users_before_delete
BEFORE DELETE ON users
FOR EACH ROW
BEGIN
    INSERT INTO audit_logs (user_id, action, details)
    VALUES (OLD.user_id, 'USER_DELETED', CONCAT('User deleted: ', OLD.full_name));
END //

DELIMITER ;

-- =====================================================
-- OPTIMIZE TABLES
-- =====================================================

OPTIMIZE TABLE users;
OPTIMIZE TABLE students;
OPTIMIZE TABLE attendance;

-- =====================================================
-- GRANT PERMISSIONS (if needed)
-- =====================================================

-- GRANT ALL PRIVILEGES ON rfid_attendance.* TO 'root'@'localhost';
-- FLUSH PRIVILEGES;

-- =====================================================
-- DATABASE ENHANCEMENT COMPLETE
-- =====================================================

SELECT 'Enhanced database schema created successfully!' as Status;
SELECT COUNT(*) as 'Total Tables' FROM information_schema.tables 
WHERE table_schema = 'rfid_attendance';

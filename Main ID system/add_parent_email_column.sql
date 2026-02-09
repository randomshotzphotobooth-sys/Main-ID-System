-- Add parent_email column to users table
-- Run this in MySQL Workbench or command line

USE rfid_attendance;

-- Add the parent_email column
ALTER TABLE users ADD COLUMN parent_email VARCHAR(100);

-- Verify the column was added
DESCRIBE users;

-- Optional: Set some test data
-- UPDATE users SET parent_email = 'parent@example.com' WHERE user_id = 1;

SELECT 'Column added successfully!' as Status;

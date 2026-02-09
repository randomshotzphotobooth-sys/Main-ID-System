import java.io.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * ReportGenerator - Generates various attendance reports
 * Supports CSV, HTML, and TXT formats
 */
public class ReportGenerator {
    
    private Connection conn;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    
    public ReportGenerator(Connection conn) {
        this.conn = conn;
    }
    
    /**
     * Generate daily attendance report
     */
    public void generateDailyReport(LocalDate date, String format, String outputPath) throws Exception {
        String sql = "SELECT u.full_name, u.rfid_uid, s.grade, s.section, " +
                     "a.time_in, a.time_out, a.is_late, a.status " +
                     "FROM users u " +
                     "LEFT JOIN students s ON u.user_id = s.user_id " +
                     "LEFT JOIN attendance a ON u.user_id = a.user_id AND a.date = ? " +
                     "WHERE u.role IN ('student', 'teacher') AND u.status = 'active' " +
                     "ORDER BY s.grade, s.section, u.full_name";
        
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setString(1, date.format(DATE_FORMAT));
        ResultSet rs = stmt.executeQuery();
        
        switch (format.toLowerCase()) {
            case "csv":
                generateCSVReport(rs, outputPath, "Daily Attendance - " + date.format(DISPLAY_FORMAT));
                break;
            case "html":
                generateHTMLDailyReport(rs, outputPath, date);
                break;
            case "txt":
                generateTextReport(rs, outputPath, "Daily Attendance - " + date.format(DISPLAY_FORMAT));
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
        
        rs.close();
        stmt.close();
    }
    
    /**
     * Generate monthly attendance summary
     */
    public void generateMonthlyReport(int year, int month, String format, String outputPath) throws Exception {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        
        String sql = "SELECT u.user_id, u.full_name, s.grade, s.section, " +
                     "COUNT(DISTINCT a.date) as days_present, " +
                     "SUM(CASE WHEN a.is_late = TRUE THEN 1 ELSE 0 END) as days_late, " +
                     "ROUND((COUNT(DISTINCT a.date) / ?) * 100, 2) as attendance_rate " +
                     "FROM users u " +
                     "LEFT JOIN students s ON u.user_id = s.user_id " +
                     "LEFT JOIN attendance a ON u.user_id = a.user_id " +
                     "    AND a.date BETWEEN ? AND ? " +
                     "WHERE u.role = 'student' AND u.status = 'active' " +
                     "GROUP BY u.user_id, u.full_name, s.grade, s.section " +
                     "ORDER BY s.grade, s.section, u.full_name";
        
        int workingDays = calculateWorkingDays(startDate, endDate);
        
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setInt(1, workingDays);
        stmt.setString(2, startDate.format(DATE_FORMAT));
        stmt.setString(3, endDate.format(DATE_FORMAT));
        ResultSet rs = stmt.executeQuery();
        
        switch (format.toLowerCase()) {
            case "csv":
                generateMonthlyCSVReport(rs, outputPath, startDate, workingDays);
                break;
            case "html":
                generateHTMLMonthlyReport(rs, outputPath, startDate, workingDays);
                break;
            default:
                generateTextReport(rs, outputPath, "Monthly Report - " + startDate.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
        }
        
        rs.close();
        stmt.close();
    }
    
    /**
     * Generate student individual report
     */
    public void generateStudentReport(int userId, LocalDate startDate, LocalDate endDate, String outputPath) throws Exception {
        String sql = "SELECT a.date, a.time_in, a.time_out, a.is_late, a.status " +
                     "FROM attendance a " +
                     "WHERE a.user_id = ? AND a.date BETWEEN ? AND ? " +
                     "ORDER BY a.date DESC";
        
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setInt(1, userId);
        stmt.setString(2, startDate.format(DATE_FORMAT));
        stmt.setString(3, endDate.format(DATE_FORMAT));
        ResultSet rs = stmt.executeQuery();
        
        // Get student info
        String studentInfo = getStudentInfo(userId);
        
        StringBuilder report = new StringBuilder();
        report.append("=== STUDENT ATTENDANCE REPORT ===\n\n");
        report.append(studentInfo).append("\n");
        report.append("Period: ").append(startDate.format(DISPLAY_FORMAT))
              .append(" to ").append(endDate.format(DISPLAY_FORMAT)).append("\n");
        report.append("=" .repeat(80)).append("\n\n");
        
        report.append(String.format("%-15s %-15s %-15s %-10s %-10s\n", 
                      "Date", "Time In", "Time Out", "Late", "Status"));
        report.append("-".repeat(80)).append("\n");
        
        int totalDays = 0;
        int lateDays = 0;
        
        while (rs.next()) {
            totalDays++;
            boolean isLate = rs.getBoolean("is_late");
            if (isLate) lateDays++;
            
            report.append(String.format("%-15s %-15s %-15s %-10s %-10s\n",
                rs.getString("date"),
                rs.getString("time_in") != null ? rs.getString("time_in") : "N/A",
                rs.getString("time_out") != null ? rs.getString("time_out") : "N/A",
                isLate ? "Yes" : "No",
                rs.getString("status")));
        }
        
        report.append("\n").append("=".repeat(80)).append("\n");
        report.append(String.format("Total Days Present: %d\n", totalDays));
        report.append(String.format("Days Late: %d\n", lateDays));
        
        int workingDays = calculateWorkingDays(startDate, endDate);
        double attendanceRate = workingDays > 0 ? (totalDays * 100.0 / workingDays) : 0;
        report.append(String.format("Attendance Rate: %.2f%%\n", attendanceRate));
        
        // Write to file
        try (PrintWriter writer = new PrintWriter(outputPath)) {
            writer.print(report.toString());
        }
        
        rs.close();
        stmt.close();
        
        System.out.println("✓ Student report generated: " + outputPath);
    }
    
    /**
     * Generate CSV format report
     */
    private void generateCSVReport(ResultSet rs, String outputPath, String title) throws Exception {
        try (PrintWriter writer = new PrintWriter(outputPath)) {
            writer.println("# " + title);
            writer.println();
            
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            
            // Write header
            for (int i = 1; i <= columnCount; i++) {
                writer.print(metaData.getColumnLabel(i));
                if (i < columnCount) writer.print(",");
            }
            writer.println();
            
            // Write data
            while (rs.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    String value = rs.getString(i);
                    writer.print(value != null ? value : "N/A");
                    if (i < columnCount) writer.print(",");
                }
                writer.println();
            }
        }
        
        System.out.println("✓ CSV report generated: " + outputPath);
    }
    
    /**
     * Generate monthly CSV report
     */
    private void generateMonthlyCSVReport(ResultSet rs, String outputPath, LocalDate month, int workingDays) throws Exception {
        try (PrintWriter writer = new PrintWriter(outputPath)) {
            writer.println("# Monthly Attendance Summary - " + month.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
            writer.println("# Total Working Days: " + workingDays);
            writer.println();
            
            writer.println("Full Name,Grade,Section,Days Present,Days Late,Attendance Rate (%)");
            
            while (rs.next()) {
                writer.printf("%s,%s,%s,%d,%d,%.2f%%\n",
                    rs.getString("full_name"),
                    rs.getString("grade") != null ? rs.getString("grade") : "N/A",
                    rs.getString("section") != null ? rs.getString("section") : "N/A",
                    rs.getInt("days_present"),
                    rs.getInt("days_late"),
                    rs.getDouble("attendance_rate"));
            }
        }
        
        System.out.println("✓ Monthly CSV report generated: " + outputPath);
    }
    
    /**
     * Generate HTML daily report
     */
    private void generateHTMLDailyReport(ResultSet rs, String outputPath, LocalDate date) throws Exception {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html>\n<head>\n");
        html.append("<meta charset='UTF-8'>\n");
        html.append("<title>Daily Attendance Report - ").append(date.format(DISPLAY_FORMAT)).append("</title>\n");
        html.append("<style>\n");
        html.append("body { font-family: Arial, sans-serif; margin: 20px; }\n");
        html.append("h1 { color: #2c3e50; }\n");
        html.append("table { border-collapse: collapse; width: 100%; margin-top: 20px; }\n");
        html.append("th { background-color: #3498db; color: white; padding: 12px; text-align: left; }\n");
        html.append("td { border: 1px solid #ddd; padding: 10px; }\n");
        html.append("tr:nth-child(even) { background-color: #f2f2f2; }\n");
        html.append(".present { color: green; font-weight: bold; }\n");
        html.append(".absent { color: red; font-weight: bold; }\n");
        html.append(".late { color: orange; }\n");
        html.append(".summary { margin-top: 20px; padding: 15px; background-color: #ecf0f1; border-radius: 5px; }\n");
        html.append("</style>\n</head>\n<body>\n");
        
        html.append("<h1>📊 Daily Attendance Report</h1>\n");
        html.append("<p><strong>Date:</strong> ").append(date.format(DISPLAY_FORMAT)).append("</p>\n");
        
        html.append("<table>\n<thead>\n<tr>\n");
        html.append("<th>Full Name</th><th>Grade</th><th>Section</th>");
        html.append("<th>Time In</th><th>Time Out</th><th>Status</th>\n");
        html.append("</tr>\n</thead>\n<tbody>\n");
        
        int totalPresent = 0;
        int totalAbsent = 0;
        int totalLate = 0;
        
        while (rs.next()) {
            String timeIn = rs.getString("time_in");
            boolean isLate = rs.getBoolean("is_late");
            String status = timeIn != null ? "Present" : "Absent";
            
            if (timeIn != null) {
                totalPresent++;
                if (isLate) totalLate++;
            } else {
                totalAbsent++;
            }
            
            html.append("<tr>\n");
            html.append("<td>").append(rs.getString("full_name")).append("</td>");
            html.append("<td>").append(rs.getString("grade") != null ? rs.getString("grade") : "N/A").append("</td>");
            html.append("<td>").append(rs.getString("section") != null ? rs.getString("section") : "N/A").append("</td>");
            html.append("<td").append(isLate ? " class='late'" : "").append(">");
            html.append(timeIn != null ? timeIn : "N/A").append("</td>");
            html.append("<td>").append(rs.getString("time_out") != null ? rs.getString("time_out") : "N/A").append("</td>");
            html.append("<td class='").append(timeIn != null ? "present" : "absent").append("'>").append(status).append("</td>");
            html.append("</tr>\n");
        }
        
        html.append("</tbody>\n</table>\n");
        
        // Summary
        html.append("<div class='summary'>\n");
        html.append("<h3>📈 Summary</h3>\n");
        html.append("<p><strong>Total Present:</strong> ").append(totalPresent).append("</p>\n");
        html.append("<p><strong>Total Absent:</strong> ").append(totalAbsent).append("</p>\n");
        html.append("<p><strong>Late Arrivals:</strong> ").append(totalLate).append("</p>\n");
        
        int total = totalPresent + totalAbsent;
        double attendanceRate = total > 0 ? (totalPresent * 100.0 / total) : 0;
        html.append("<p><strong>Attendance Rate:</strong> ").append(String.format("%.2f%%", attendanceRate)).append("</p>\n");
        html.append("</div>\n");
        
        html.append("<p style='margin-top: 30px; color: #7f8c8d; font-size: 12px;'>");
        html.append("Generated on ").append(LocalDate.now().format(DISPLAY_FORMAT)).append("</p>\n");
        html.append("</body>\n</html>");
        
        try (PrintWriter writer = new PrintWriter(outputPath)) {
            writer.print(html.toString());
        }
        
        System.out.println("✓ HTML report generated: " + outputPath);
    }
    
    /**
     * Generate HTML monthly report
     */
    private void generateHTMLMonthlyReport(ResultSet rs, String outputPath, LocalDate month, int workingDays) throws Exception {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html>\n<head>\n");
        html.append("<meta charset='UTF-8'>\n");
        html.append("<title>Monthly Attendance Report - ").append(month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))).append("</title>\n");
        html.append("<style>\n");
        html.append("body { font-family: Arial, sans-serif; margin: 20px; }\n");
        html.append("h1 { color: #2c3e50; }\n");
        html.append("table { border-collapse: collapse; width: 100%; margin-top: 20px; }\n");
        html.append("th { background-color: #2ecc71; color: white; padding: 12px; text-align: left; }\n");
        html.append("td { border: 1px solid #ddd; padding: 10px; }\n");
        html.append("tr:nth-child(even) { background-color: #f2f2f2; }\n");
        html.append(".good { color: green; font-weight: bold; }\n");
        html.append(".warning { color: orange; font-weight: bold; }\n");
        html.append(".poor { color: red; font-weight: bold; }\n");
        html.append("</style>\n</head>\n<body>\n");
        
        html.append("<h1>📊 Monthly Attendance Summary</h1>\n");
        html.append("<p><strong>Month:</strong> ").append(month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))).append("</p>\n");
        html.append("<p><strong>Total Working Days:</strong> ").append(workingDays).append("</p>\n");
        
        html.append("<table>\n<thead>\n<tr>\n");
        html.append("<th>Full Name</th><th>Grade</th><th>Section</th>");
        html.append("<th>Days Present</th><th>Days Late</th><th>Attendance Rate</th>\n");
        html.append("</tr>\n</thead>\n<tbody>\n");
        
        while (rs.next()) {
            double rate = rs.getDouble("attendance_rate");
            String rateClass = rate >= 90 ? "good" : (rate >= 75 ? "warning" : "poor");
            
            html.append("<tr>\n");
            html.append("<td>").append(rs.getString("full_name")).append("</td>");
            html.append("<td>").append(rs.getString("grade") != null ? rs.getString("grade") : "N/A").append("</td>");
            html.append("<td>").append(rs.getString("section") != null ? rs.getString("section") : "N/A").append("</td>");
            html.append("<td>").append(rs.getInt("days_present")).append("/").append(workingDays).append("</td>");
            html.append("<td>").append(rs.getInt("days_late")).append("</td>");
            html.append("<td class='").append(rateClass).append("'>").append(String.format("%.2f%%", rate)).append("</td>");
            html.append("</tr>\n");
        }
        
        html.append("</tbody>\n</table>\n");
        html.append("<p style='margin-top: 30px; color: #7f8c8d; font-size: 12px;'>");
        html.append("Generated on ").append(LocalDate.now().format(DISPLAY_FORMAT)).append("</p>\n");
        html.append("</body>\n</html>");
        
        try (PrintWriter writer = new PrintWriter(outputPath)) {
            writer.print(html.toString());
        }
        
        System.out.println("✓ HTML monthly report generated: " + outputPath);
    }
    
    /**
     * Generate plain text report
     */
    private void generateTextReport(ResultSet rs, String outputPath, String title) throws Exception {
        try (PrintWriter writer = new PrintWriter(outputPath)) {
            writer.println("=".repeat(80));
            writer.println(title);
            writer.println("=".repeat(80));
            writer.println();
            
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            
            while (rs.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    writer.printf("%-25s: %s\n", metaData.getColumnLabel(i), 
                                rs.getString(i) != null ? rs.getString(i) : "N/A");
                }
                writer.println("-".repeat(80));
            }
        }
        
        System.out.println("✓ Text report generated: " + outputPath);
    }
    
    /**
     * Helper: Get student information
     */
    private String getStudentInfo(int userId) throws SQLException {
        String sql = "SELECT u.full_name, u.rfid_uid, s.grade, s.section, u.parent_email " +
                     "FROM users u LEFT JOIN students s ON u.user_id = s.user_id WHERE u.user_id = ?";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setInt(1, userId);
        ResultSet rs = stmt.executeQuery();
        
        StringBuilder info = new StringBuilder();
        if (rs.next()) {
            info.append("Student Name: ").append(rs.getString("full_name")).append("\n");
            info.append("Student ID: ").append(rs.getString("rfid_uid")).append("\n");
            info.append("Grade: ").append(rs.getString("grade") != null ? rs.getString("grade") : "N/A").append("\n");
            info.append("Section: ").append(rs.getString("section") != null ? rs.getString("section") : "N/A").append("\n");
            info.append("Parent Email: ").append(rs.getString("parent_email") != null ? rs.getString("parent_email") : "N/A");
        }
        
        rs.close();
        stmt.close();
        return info.toString();
    }
    
    /**
     * Helper: Calculate working days (excluding weekends)
     */
    private int calculateWorkingDays(LocalDate startDate, LocalDate endDate) {
        int workingDays = 0;
        LocalDate current = startDate;
        
        while (!current.isAfter(endDate)) {
            // Exclude Saturday and Sunday
            if (current.getDayOfWeek().getValue() < 6) {
                workingDays++;
            }
            current = current.plusDays(1);
        }
        
        return workingDays;
    }
}

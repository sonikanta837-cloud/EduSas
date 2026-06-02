package com.emp.management.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendPasswordResetEmail(String to, String resetUrl) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("Password Reset Request - EmpSAS");
            message.setText(
                "Hello,\n\n" +
                "You requested a password reset for your EmpSAS account.\n\n" +
                "Click the link below to reset your password (valid for 1 hour):\n" +
                resetUrl + "\n\n" +
                "If you did not request this, please ignore this email.\n\n" +
                "EmpSAS Team"
            );
            mailSender.send(message);
            log.info("Password reset email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send reset email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send reset email");
        }
    }

    public void sendLeaveRequestEmail(String to, String[] cc, String employeeEmail,
                                      String employeeName, String leaveType,
                                      LocalDate startDate, LocalDate endDate,
                                      int totalDays, String reason) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            if (cc != null && cc.length > 0) message.setCc(cc);
            message.setSubject("Leave Request - " + employeeName);
            message.setText(
                "New Leave Request\n\n" +
                "Employee : " + employeeName + " (" + employeeEmail + ")\n" +
                "Leave Type: " + leaveType + "\n" +
                "From      : " + startDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) + "\n" +
                "To        : " + endDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) + "\n" +
                "Days      : " + totalDays + "\n" +
                "Reason    : " + (reason != null ? reason : "-") + "\n\n" +
                "Please log in to EmpSAS to approve or reject this request.\n\n" +
                "EmpSAS Team"
            );
            mailSender.send(message);
            log.info("Leave request email sent to {} (cc: {}) for employee {}", to, Arrays.toString(cc), employeeName);
        } catch (Exception e) {
            log.error("Failed to send leave request email to {}: {}", to, e.getMessage());
        }
    }

    public void sendLeaveDecisionEmail(String to, String[] cc, String employeeName,
                                       String status, String leaveType,
                                       LocalDate startDate, LocalDate endDate,
                                       String managerName, String comment) {
        try {
            boolean approved = "APPROVED".equalsIgnoreCase(status);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            if (cc != null && cc.length > 0) message.setCc(cc);
            message.setSubject("Leave Request " + (approved ? "Approved" : "Rejected") + " - EmpSAS");
            message.setText(
                "Leave Request Update\n\n" +
                "Dear " + employeeName + ",\n\n" +
                "Your leave request has been " + status + ".\n\n" +
                "Leave Type: " + leaveType + "\n" +
                "From      : " + startDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) + "\n" +
                "To        : " + endDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) + "\n" +
                "Reviewed by: " + managerName + "\n" +
                (comment != null && !comment.isBlank() ? "Comment   : " + comment + "\n" : "") +
                "\nEmpSAS Team"
            );
            mailSender.send(message);
            log.info("Leave decision email sent to {} (cc: {}) — status: {}", to, Arrays.toString(cc), status);
        } catch (Exception e) {
            log.error("Failed to send leave decision email to {}: {}", to, e.getMessage());
        }
    }

    public void sendCourseAssignmentEmail(String to, String employeeName,
                                          String courseName, String assignedBy,
                                          LocalDate assignmentDate, String courseLink) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("New Course Assigned: " + courseName + " – EmpSAS");
            message.setText(
                "Dear " + employeeName + ",\n\n" +
                "A new course has been assigned to you. Please find the details below.\n\n" +
                "Course Details\n" +
                "─────────────────────────────────────\n" +
                "  Course Name     : " + courseName + "\n" +
                "  Assigned By     : " + assignedBy + "\n" +
                "  Assignment Date : " + assignmentDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) + "\n" +
                "  Course Link     : " + courseLink + "\n\n" +
                "Please log in to EmpSAS and complete this course at your earliest convenience.\n\n" +
                "EmpSAS Team"
            );
            mailSender.send(message);
            log.info("Course assignment email sent to {} for course '{}'", to, courseName);
        } catch (Exception e) {
            log.error("Failed to send course assignment email to {} for course '{}': {}", to, courseName, e.getMessage());
        }
    }

    public void sendMissingTimesheetManagerAlert(String to, String[] cc,
                                                 String employeeName, String employeeCode,
                                                 String department, String employeeEmail,
                                                 LocalDate missingDate) {
        try {
            String dateStr = missingDate.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy"));
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            if (cc != null && cc.length > 0) message.setCc(cc);
            message.setSubject("Missing Timesheet Alert – " + employeeName
                    + " – " + missingDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
            message.setText(
                "Missing Timesheet Notification\n\n" +
                "The following employee has not submitted their timesheet for " + dateStr + ".\n\n" +
                "Employee Details\n" +
                "─────────────────────────────────────\n" +
                "  Name          : " + employeeName + "\n" +
                "  Employee ID   : " + (employeeCode != null ? employeeCode : "—") + "\n" +
                "  Department    : " + (department != null ? department : "—") + "\n" +
                "  Email         : " + employeeEmail + "\n\n" +
                "Timesheet Details\n" +
                "─────────────────────────────────────\n" +
                "  Missing Date  : " + dateStr + "\n" +
                "  Status        : Not Submitted\n\n" +
                "Please follow up with the employee to ensure the timesheet is submitted at the earliest.\n\n" +
                "EmpSAS Team"
            );
            mailSender.send(message);
            log.info("Missing timesheet alert sent to {} (cc: {}) for employee {} on {}",
                    to, Arrays.toString(cc), employeeName, missingDate);
        } catch (Exception e) {
            log.error("Failed to send missing timesheet alert for {} on {}: {}", employeeName, missingDate, e.getMessage());
        }
    }

    public void sendTimesheetMissingReminder(String employeeEmail, String employeeName,
                                              String managerEmail, LocalDate missingDate) {
        try {
            String dateStr = missingDate.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy"));
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(employeeEmail);
            if (managerEmail != null && !managerEmail.isBlank()) {
                message.setCc(managerEmail);
            }
            message.setSubject("Reminder: Please Fill Your Timesheet — " + dateStr);
            message.setText(
                "Hi " + employeeName + ",\n\n" +
                "This is a friendly reminder that your timesheet for " + dateStr + " appears to be incomplete.\n\n" +
                "No project entries were recorded for that day. Please log in to EmpSAS and add your project work details as soon as possible.\n\n" +
                "Steps to update your timesheet:\n" +
                "  1. Log in to EmpSAS\n" +
                "  2. Navigate to Timesheets\n" +
                "  3. Select " + dateStr + "\n" +
                "  4. Click 'Add Row' and fill in your project and hours\n\n" +
                "If you were on leave or had a valid reason for not logging hours, please disregard this message.\n\n" +
                "Regards,\n" +
                "EmpSAS Team"
            );
            mailSender.send(message);
            log.info("Timesheet reminder sent to {} for date {}", employeeEmail, missingDate);
        } catch (Exception e) {
            log.error("Failed to send timesheet reminder to {}: {}", employeeEmail, e.getMessage());
        }
    }

    public void sendAttendanceAuditAlert(String to, String[] cc,
                                         String employeeName, String employeeCode,
                                         String department, String employeeEmail,
                                         LocalDate date, double workedHours,
                                         double requiredHours, double deficitHours) {
        try {
            String dateStr = date.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy"));
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            if (cc != null && cc.length > 0) message.setCc(cc);
            message.setSubject("Attendance Audit Alert – " + employeeName
                    + " – " + date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
            message.setText(
                "Attendance Audit Notification\n\n" +
                "The following employee worked below the required hours on " + dateStr + ".\n\n" +
                "Employee Details\n" +
                "─────────────────────────────────────\n" +
                "  Name          : " + employeeName + "\n" +
                "  Employee Code : " + (employeeCode != null ? employeeCode : "—") + "\n" +
                "  Department    : " + (department != null ? department : "—") + "\n" +
                "  Email         : " + employeeEmail + "\n\n" +
                "Attendance Summary\n" +
                "─────────────────────────────────────\n" +
                "  Date           : " + dateStr + "\n" +
                String.format("  Worked Hours   : %.2f hrs%n", workedHours) +
                String.format("  Required Hours : %.2f hrs%n", requiredHours) +
                String.format("  Deficit        : %.2f hrs%n%n", deficitHours) +
                "Please review and take appropriate action if required.\n\n" +
                "EmpSAS Team"
            );
            mailSender.send(message);
            log.info("Attendance audit alert sent to {} (cc: {}) for {} on {}",
                    to, Arrays.toString(cc), employeeName, date);
        } catch (Exception e) {
            log.error("Failed to send attendance audit alert for {} on {}: {}", employeeName, date, e.getMessage());
        }
    }

    public void sendUnderhoursAlert(String[] to, String[] cc, String employeeName,
                                    LocalDate date, double hours) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setCc(cc);
            message.setSubject("Under Hours Alert – " + employeeName
                    + " – " + date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
            message.setText(
                "Under Hours Alert\n\n" +
                "Employee : " + employeeName + "\n" +
                "Date     : " + date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) + "\n" +
                "Hours    : " + String.format("%.1f", hours) + " / 8.0 hrs\n\n" +
                "This employee's total working hours were below the required 8 hours.\n\n" +
                "EmpSAS Team"
            );
            mailSender.send(message);
            log.info("Underhours alert sent to {} for employee {}", Arrays.toString(to), employeeName);
        } catch (Exception e) {
            log.error("Failed to send underhours alert for {}: {}", employeeName, e.getMessage());
        }
    }
}

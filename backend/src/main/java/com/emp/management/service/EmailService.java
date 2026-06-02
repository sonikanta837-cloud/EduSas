package com.emp.management.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    // ── Shared HTML helpers ───────────────────────────────────────────────────

    private String wrap(String icon, String title, String body) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'/>" +
            "<meta name='viewport' content='width=device-width,initial-scale=1'/></head>" +
            "<body style='margin:0;padding:0;background:#f0f2f5;" +
                         "font-family:Arial,Helvetica,sans-serif;'>" +
            "<table width='100%' cellpadding='0' cellspacing='0' " +
                   "style='background:#f0f2f5;padding:30px 0;'>" +
            "<tr><td align='center'>" +
            "<table width='580' cellpadding='0' cellspacing='0' " +
                   "style='background:#ffffff;border-radius:10px;overflow:hidden;" +
                          "box-shadow:0 4px 16px rgba(0,0,0,0.12);max-width:580px;'>" +
            // Header
            "<tr><td style='background:#1a2847;padding:22px 32px;'>" +
            "<p style='color:#ffffff;margin:0;font-size:20px;font-weight:bold;'>" +
            icon + "&nbsp;" + title + "</p></td></tr>" +
            // Body
            "<tr><td style='padding:28px 32px;color:#333333;font-size:14px;line-height:1.7;'>" +
            body +
            "</td></tr>" +
            // Footer
            "<tr><td style='background:#f8f9fa;padding:14px 32px;" +
                           "border-top:1px solid #e8e8e8;'>" +
            "<p style='color:#aaaaaa;font-size:12px;margin:0;'>" +
            "EmpSAS &mdash; Employee Management System</p>" +
            "</td></tr>" +
            "</table></td></tr></table></body></html>";
    }

    private String row(String label, String value) {
        return "<tr>" +
            "<td style='background:#f4f6f9;border:1px solid #dde3ec;" +
                       "padding:10px 14px;font-weight:bold;color:#4a5568;width:38%;'>" +
            label + "</td>" +
            "<td style='border:1px solid #dde3ec;padding:10px 14px;color:#333;'>" +
            (value != null && !value.isBlank() ? value : "&mdash;") +
            "</td></tr>";
    }

    private String tableOpen() {
        return "<table width='100%' cellpadding='0' cellspacing='0' " +
               "style='border-collapse:collapse;margin:18px 0;font-size:14px;'>";
    }

    private String tableClose() { return "</table>"; }

    private String note(String text) {
        return "<p style='color:#888888;font-size:13px;margin-top:20px;'>" + text + "</p>";
    }

    private void send(String to, String[] cc, String subject, String html) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, false, "UTF-8");
            h.setTo(to);
            if (cc != null && cc.length > 0) h.setCc(cc);
            h.setSubject(subject);
            h.setText(html, true);
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Email send failed", e);
        }
    }

    private void sendMulti(String[] to, String[] cc, String subject, String html) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, false, "UTF-8");
            h.setTo(to);
            if (cc != null && cc.length > 0) h.setCc(cc);
            h.setSubject(subject);
            h.setText(html, true);
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", Arrays.toString(to), e.getMessage());
        }
    }

    // ── Password reset ────────────────────────────────────────────────────────

    public void sendPasswordResetEmail(String to, String resetUrl) {
        String body =
            "<p>Hello,</p>" +
            "<p>You requested a password reset for your <strong>EmpSAS</strong> account.</p>" +
            "<p>Click the button below to reset your password. The link is valid for <strong>1 hour</strong>.</p>" +
            "<p style='margin:24px 0;'>" +
            "<a href='" + resetUrl + "' style='background:#1a2847;color:#ffffff;padding:12px 28px;" +
            "border-radius:6px;text-decoration:none;font-weight:bold;font-size:14px;'>" +
            "Reset Password</a></p>" +
            "<p style='font-size:12px;color:#999;'>If the button does not work, copy and paste this link:<br/>" +
            "<a href='" + resetUrl + "' style='color:#1a2847;'>" + resetUrl + "</a></p>" +
            note("If you did not request this, please ignore this email.");

        try {
            send(to, null, "Password Reset Request - EmpSAS",
                 wrap("&#128274;", "Password Reset Request", body));
            log.info("Password reset email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send reset email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send reset email");
        }
    }

    // ── Leave request (To: manager, CC: HR + Admin) ───────────────────────────

    public void sendLeaveRequestEmail(String to, String[] cc, String employeeEmail,
                                      String employeeName, String leaveType,
                                      LocalDate startDate, LocalDate endDate,
                                      int totalDays, String reason) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy");
        String body =
            "<p>Dear Manager,</p>" +
            "<p><strong>" + employeeName + "</strong> has submitted a leave request " +
            "and is awaiting your approval.</p>" +
            tableOpen() +
            row("Leave Type",  leaveType) +
            row("From",        startDate.format(fmt)) +
            row("To",          endDate.format(fmt)) +
            row("Total Days",  totalDays + (totalDays == 1 ? " day" : " days")) +
            row("Reason",      reason) +
            tableClose() +
            note("Please log in to the EmpSAS portal to approve or reject this request.");

        send(to, cc, "New Leave Request – " + employeeName,
             wrap("&#128203;", "New Leave Request", body));
        log.info("Leave request email sent to {} (cc: {}) for {}", to, Arrays.toString(cc), employeeName);
    }

    // ── Leave decision (To: employee, CC: HR + Admin) ─────────────────────────

    public void sendLeaveDecisionEmail(String to, String[] cc, String employeeName,
                                       String status, String leaveType,
                                       LocalDate startDate, LocalDate endDate,
                                       String managerName, String comment) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy");
        boolean approved = "APPROVED".equalsIgnoreCase(status);
        String statusBadge = approved
            ? "<span style='background:#dcfce7;color:#16a34a;padding:3px 10px;" +
              "border-radius:12px;font-weight:bold;font-size:13px;'>APPROVED</span>"
            : "<span style='background:#fee2e2;color:#dc2626;padding:3px 10px;" +
              "border-radius:12px;font-weight:bold;font-size:13px;'>REJECTED</span>";

        String body =
            "<p>Dear <strong>" + employeeName + "</strong>,</p>" +
            "<p>Your leave request has been reviewed. Current status: " + statusBadge + "</p>" +
            tableOpen() +
            row("Leave Type",   leaveType) +
            row("From",         startDate.format(fmt)) +
            row("To",           endDate.format(fmt)) +
            row("Reviewed By",  managerName) +
            row("Comment",      comment) +
            tableClose() +
            note("Please log in to EmpSAS to view your leave balance and history.");

        send(to, cc,
             "Leave Request " + (approved ? "Approved" : "Rejected") + " – EmpSAS",
             wrap(approved ? "&#9989;" : "&#10060;",
                  "Leave Request " + (approved ? "Approved" : "Rejected"), body));
        log.info("Leave decision email sent to {} (cc: {}) — {}", to, Arrays.toString(cc), status);
    }

    // ── Course assignment (To: employee) ──────────────────────────────────────

    public void sendCourseAssignmentEmail(String to, String employeeName,
                                          String courseName, String assignedBy,
                                          LocalDate assignmentDate, String courseLink) {
        String body =
            "<p>Dear <strong>" + employeeName + "</strong>,</p>" +
            "<p>A new course has been assigned to you. Please find the details below.</p>" +
            tableOpen() +
            row("Course Name",      courseName) +
            row("Assigned By",      assignedBy) +
            row("Assignment Date",  assignmentDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))) +
            tableClose() +
            "<p style='margin:22px 0;'>" +
            "<a href='" + courseLink + "' style='background:#1a2847;color:#ffffff;padding:12px 28px;" +
            "border-radius:6px;text-decoration:none;font-weight:bold;font-size:14px;'>" +
            "Access Course</a></p>" +
            note("Please complete this course at your earliest convenience.");

        send(to, null, "New Course Assigned: " + courseName + " – EmpSAS",
             wrap("&#127979;", "New Course Assigned", body));
        log.info("Course assignment email sent to {} for course '{}'", to, courseName);
    }

    // ── Attendance audit (To: manager, CC: HR + Admin) ────────────────────────

    public void sendAttendanceAuditAlert(String to, String[] cc,
                                         String employeeName, String employeeCode,
                                         String department, String employeeEmail,
                                         LocalDate date, double workedHours,
                                         double requiredHours, double deficitHours) {
        String dateStr = date.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy"));
        String body =
            "<p>Dear Manager,</p>" +
            "<p>The following employee worked below the required hours on <strong>" + dateStr + "</strong>.</p>" +
            "<p style='font-size:13px;font-weight:bold;color:#4a5568;margin:18px 0 6px;'>Employee Details</p>" +
            tableOpen() +
            row("Name",          employeeName) +
            row("Employee ID",   employeeCode) +
            row("Department",    department) +
            row("Email",         employeeEmail) +
            tableClose() +
            "<p style='font-size:13px;font-weight:bold;color:#4a5568;margin:18px 0 6px;'>Attendance Summary</p>" +
            tableOpen() +
            row("Date",            dateStr) +
            row("Worked Hours",    String.format("%.2f hrs", workedHours)) +
            row("Required Hours",  String.format("%.2f hrs", requiredHours)) +
            row("Deficit",         "<span style='color:#dc2626;font-weight:bold;'>" +
                                   String.format("%.2f hrs", deficitHours) + "</span>") +
            tableClose() +
            note("Please review and take appropriate action if required.");

        sendMulti(new String[]{to}, cc,
                  "Attendance Audit Alert – " + employeeName +
                  " – " + date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                  wrap("&#9888;&#65039;", "Attendance Audit Alert", body));
        log.info("Attendance audit alert sent to {} (cc: {}) for {} on {}", to, Arrays.toString(cc), employeeName, date);
    }

    // ── Missing timesheet (To: manager, CC: HR + Admin) ───────────────────────

    public void sendMissingTimesheetManagerAlert(String to, String[] cc,
                                                 String employeeName, String employeeCode,
                                                 String department, String employeeEmail,
                                                 LocalDate missingDate) {
        String dateStr = missingDate.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy"));
        String body =
            "<p>Dear Manager,</p>" +
            "<p>The following employee has not submitted their timesheet for " +
            "<strong>" + dateStr + "</strong>.</p>" +
            "<p style='font-size:13px;font-weight:bold;color:#4a5568;margin:18px 0 6px;'>Employee Details</p>" +
            tableOpen() +
            row("Name",        employeeName) +
            row("Employee ID", employeeCode) +
            row("Department",  department) +
            row("Email",       employeeEmail) +
            tableClose() +
            "<p style='font-size:13px;font-weight:bold;color:#4a5568;margin:18px 0 6px;'>Timesheet Details</p>" +
            tableOpen() +
            row("Missing Date", dateStr) +
            row("Status", "<span style='background:#fee2e2;color:#dc2626;padding:3px 10px;" +
                          "border-radius:12px;font-weight:bold;font-size:13px;'>Not Submitted</span>") +
            tableClose() +
            note("Please follow up with the employee to ensure the timesheet is submitted at the earliest.");

        sendMulti(new String[]{to}, cc,
                  "Missing Timesheet Alert – " + employeeName +
                  " – " + missingDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                  wrap("&#128197;", "Missing Timesheet Alert", body));
        log.info("Missing timesheet alert sent to {} (cc: {}) for {} on {}", to, Arrays.toString(cc), employeeName, missingDate);
    }

    // ── Timesheet reminder (To: employee) ─────────────────────────────────────

    public void sendTimesheetMissingReminder(String employeeEmail, String employeeName,
                                              String managerEmail, LocalDate missingDate) {
        String dateStr = missingDate.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy"));
        String body =
            "<p>Hi <strong>" + employeeName + "</strong>,</p>" +
            "<p>This is a friendly reminder that your timesheet for " +
            "<strong>" + dateStr + "</strong> appears to be incomplete.</p>" +
            tableOpen() +
            row("Missing Date", dateStr) +
            row("Status", "<span style='background:#fee2e2;color:#dc2626;padding:3px 10px;" +
                          "border-radius:12px;font-weight:bold;font-size:13px;'>Not Submitted</span>") +
            tableClose() +
            "<p style='font-size:14px;'>Steps to update your timesheet:</p>" +
            "<ol style='font-size:14px;line-height:1.8;'>" +
            "<li>Log in to EmpSAS</li>" +
            "<li>Navigate to <strong>Timesheets</strong></li>" +
            "<li>Select <strong>" + dateStr + "</strong></li>" +
            "<li>Click <strong>Add Row</strong> and fill in your project and hours</li>" +
            "</ol>" +
            note("If you were on leave or had a valid reason, please disregard this message.");

        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, false, "UTF-8");
            h.setTo(employeeEmail);
            if (managerEmail != null && !managerEmail.isBlank()) h.setCc(managerEmail);
            h.setSubject("Reminder: Please Fill Your Timesheet — " + dateStr);
            h.setText(wrap("&#128197;", "Timesheet Reminder", body), true);
            mailSender.send(msg);
            log.info("Timesheet reminder sent to {} for {}", employeeEmail, missingDate);
        } catch (Exception e) {
            log.error("Failed to send timesheet reminder to {}: {}", employeeEmail, e.getMessage());
        }
    }

    // ── Under-hours alert ─────────────────────────────────────────────────────

    public void sendUnderhoursAlert(String[] to, String[] cc, String employeeName,
                                    LocalDate date, double hours) {
        String dateStr = date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
        String body =
            "<p>Dear Manager,</p>" +
            "<p>This is an under-hours alert for <strong>" + employeeName + "</strong>.</p>" +
            tableOpen() +
            row("Employee",       employeeName) +
            row("Date",           dateStr) +
            row("Hours Worked",   String.format("%.1f hrs", hours)) +
            row("Required Hours", "8.0 hrs") +
            row("Deficit",        "<span style='color:#dc2626;font-weight:bold;'>" +
                                  String.format("%.1f hrs", 8.0 - hours) + "</span>") +
            tableClose() +
            note("This employee's total working hours were below the required 8 hours.");

        sendMulti(to, cc,
                  "Under Hours Alert – " + employeeName + " – " + dateStr,
                  wrap("&#9202;", "Under Hours Alert", body));
        log.info("Underhours alert sent to {} for employee {}", Arrays.toString(to), employeeName);
    }
}

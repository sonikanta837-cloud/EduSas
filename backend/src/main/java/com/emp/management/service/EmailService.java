package com.emp.management.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

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
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            log.error("SMTP send failed to {} — {}: {}", to, root.getClass().getSimpleName(), root.getMessage());
            throw new RuntimeException("Email send failed: " + root.getMessage(), e);
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
            throw new RuntimeException("Failed to send reset email: " + e.getMessage(), e);
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

    // ── Break time alert (To: manager, CC: HR) ───────────────────────────────

    public void sendBreakTimeAlertEmail(String to, String[] cc,
                                        String employeeName, String employeeCode,
                                        String department, String employeeEmail,
                                        LocalDate date, LocalTime firstLogin, LocalTime lastLogout,
                                        long breakMinutes, long thresholdMinutes) {
        String breakStr     = String.format("%d hr %02d min", breakMinutes / 60, breakMinutes % 60);
        String thresholdStr = String.format("%d hr %02d min", thresholdMinutes / 60, thresholdMinutes % 60);
        String dateStr      = date.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy"));
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("hh:mm a");

        String body =
                "<p>Dear Manager,</p>" +
                        "<p>The following employee's break time on <strong>" + dateStr +
                        "</strong> has exceeded the configured threshold.</p>" +
                        "<p style='font-size:13px;font-weight:bold;color:#4a5568;margin:18px 0 6px;'>Employee Details</p>" +
                        tableOpen() +
                        row("Name",              employeeName) +
                        row("Employee ID",       employeeCode) +
                        row("Department",        department) +
                        row("Email",             employeeEmail) +
                        tableClose() +
                        "<p style='font-size:13px;font-weight:bold;color:#4a5568;margin:18px 0 6px;'>Break Time Summary</p>" +
                        tableOpen() +
                        row("Date",              dateStr) +
                        row("First Login",       firstLogin  != null ? firstLogin.format(timeFmt)  : "&mdash;") +
                        row("Last Logout",       lastLogout  != null ? lastLogout.format(timeFmt)  : "&mdash;") +
                        row("Break Taken",       "<span style='color:#dc2626;font-weight:bold;'>" + breakStr + "</span>") +
                        row("Allowed Threshold", thresholdStr) +
                        tableClose() +
                        note("Please review the employee's attendance sessions for this date if required.");

        sendMulti(new String[]{to}, cc,
                "Excess Break Time Alert – " + employeeName +
                        " – " + date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                wrap("&#9202;", "Excess Break Time Alert", body));
        log.info("Break time alert sent to {} (cc: {}) for {} on {}", to, Arrays.toString(cc), employeeName, date);
    }

    // ── Correction: employee submission confirmation ───────────────────────────

    public void sendCorrectionSubmittedToEmployee(String to, String employeeName,
                                                  LocalDate workDate, LocalTime loginTime,
                                                  LocalTime requestedLogout, String reason) {
        DateTimeFormatter fmt  = DateTimeFormatter.ofPattern("dd MMM yyyy");
        DateTimeFormatter tfmt = DateTimeFormatter.ofPattern("hh:mm a");
        String body =
                "<p>Dear <strong>" + employeeName + "</strong>,</p>" +
                        "<p>Your timesheet correction request has been submitted successfully and is awaiting your manager's approval.</p>" +
                        tableOpen() +
                        row("Date",             workDate.format(fmt)) +
                        row("Login Time",       loginTime != null ? loginTime.format(tfmt) : "—") +
                        row("Requested Logout", requestedLogout != null ? requestedLogout.format(tfmt) : "—") +
                        row("Reason",           reason) +
                        row("Status",           "<span style='background:#fff7ed;color:#c2410c;padding:3px 10px;border-radius:12px;font-weight:bold;font-size:13px;'>Pending Approval</span>") +
                        tableClose() +
                        note("You will be notified once your manager reviews the request.");
        send(to, null, "Correction Request Submitted – " + workDate.format(fmt),
                wrap("&#128221;", "Correction Request Submitted", body));
        log.info("Correction submission confirmation sent to {}", to);
    }

    // ── Correction: manager approval request ──────────────────────────────────

    public void sendCorrectionToManager(String to, String[] cc,
                                        String employeeName, String employeeCode,
                                        String department, LocalDate workDate,
                                        LocalTime loginTime, LocalTime requestedLogout,
                                        String reason, Long requestId) {
        DateTimeFormatter fmt  = DateTimeFormatter.ofPattern("dd MMM yyyy");
        DateTimeFormatter tfmt = DateTimeFormatter.ofPattern("hh:mm a");
        String body =
                "<p>Dear Manager,</p>" +
                        "<p><strong>" + employeeName + "</strong> has submitted a timesheet correction request and requires your approval.</p>" +
                        "<p style='font-size:13px;font-weight:bold;color:#4a5568;margin:18px 0 6px;'>Employee Details</p>" +
                        tableOpen() +
                        row("Name",        employeeName) +
                        row("Employee ID", employeeCode) +
                        row("Department",  department) +
                        tableClose() +
                        "<p style='font-size:13px;font-weight:bold;color:#4a5568;margin:18px 0 6px;'>Request Details</p>" +
                        tableOpen() +
                        row("Date",             workDate.format(fmt)) +
                        row("Login Time",       loginTime != null ? loginTime.format(tfmt) : "—") +
                        row("Requested Logout", requestedLogout != null ? requestedLogout.format(tfmt) : "—") +
                        row("Reason",           reason) +
                        row("Request ID",       "#" + requestId) +
                        tableClose() +
                        note("Please log in to EmpSAS and navigate to Timesheets to approve or reject this request.");
        sendMulti(new String[]{to}, cc, "Correction Request Pending Approval – " + employeeName
                        + " – " + workDate.format(fmt),
                wrap("&#128203;", "Timesheet Correction Request", body));
        log.info("Correction approval request sent to {} (cc: {}) for {}", to, Arrays.toString(cc), employeeName);
    }

    // ── Correction: approved notification to employee ─────────────────────────

    public void sendCorrectionApprovedToEmployee(String to, String employeeName,
                                                 LocalDate workDate, LocalTime approvedLogout,
                                                 String approverName, String comment) {
        DateTimeFormatter fmt  = DateTimeFormatter.ofPattern("dd MMM yyyy");
        DateTimeFormatter tfmt = DateTimeFormatter.ofPattern("hh:mm a");
        String body =
                "<p>Dear <strong>" + employeeName + "</strong>,</p>" +
                        "<p>Your timesheet correction request has been <strong style='color:#16a34a;'>Approved</strong>. Your attendance record has been updated.</p>" +
                        tableOpen() +
                        row("Date",            workDate.format(fmt)) +
                        row("Approved Logout", approvedLogout != null ? approvedLogout.format(tfmt) : "—") +
                        row("Approved By",     approverName) +
                        row("Comment",         comment) +
                        tableClose() +
                        note("Your attendance record has been updated in EmpSAS.");
        send(to, null, "Correction Request Approved – " + workDate.format(fmt),
                wrap("&#9989;", "Correction Request Approved", body));
        log.info("Correction approved email sent to {}", to);
    }

    // ── Correction: rejected notification to employee ─────────────────────────

    public void sendCorrectionRejectedToEmployee(String to, String employeeName,
                                                 LocalDate workDate, String rejectorName,
                                                 String comment) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy");
        String body =
                "<p>Dear <strong>" + employeeName + "</strong>,</p>" +
                        "<p>Your timesheet correction request for <strong>" + workDate.format(fmt) +
                        "</strong> has been <strong style='color:#dc2626;'>Rejected</strong>.</p>" +
                        tableOpen() +
                        row("Date",        workDate.format(fmt)) +
                        row("Rejected By", rejectorName) +
                        row("Reason",      comment) +
                        tableClose() +
                        note("Please contact your manager or HR if you have any questions.");
        send(to, null, "Correction Request Rejected – " + workDate.format(fmt),
                wrap("&#10060;", "Correction Request Rejected", body));
        log.info("Correction rejected email sent to {}", to);
    }

    // ── Correction: HR decision notification ─────────────────────────────────

    public void sendCorrectionDecisionToHr(String[] to, String decision,
                                           String employeeName, String employeeCode,
                                           LocalDate workDate, LocalTime approvedLogout,
                                           String resolverName, String comment) {
        boolean approved = "APPROVED".equalsIgnoreCase(decision);
        DateTimeFormatter fmt  = DateTimeFormatter.ofPattern("dd MMM yyyy");
        DateTimeFormatter tfmt = DateTimeFormatter.ofPattern("hh:mm a");
        String statusBadge = approved
                ? "<span style='background:#dcfce7;color:#16a34a;padding:3px 10px;border-radius:12px;font-weight:bold;font-size:13px;'>APPROVED</span>"
                : "<span style='background:#fee2e2;color:#dc2626;padding:3px 10px;border-radius:12px;font-weight:bold;font-size:13px;'>REJECTED</span>";
        String body =
                "<p>Dear HR,</p>" +
                        "<p>A timesheet correction request has been <strong>" + decision + "</strong>.</p>" +
                        tableOpen() +
                        row("Employee",     employeeName + " (" + employeeCode + ")") +
                        row("Date",         workDate.format(fmt)) +
                        row("Status",       statusBadge) +
                        (approved && approvedLogout != null ? row("Approved Logout", approvedLogout.format(tfmt)) : "") +
                        row("Resolved By",  resolverName) +
                        row("Comment",      comment) +
                        tableClose() +
                        note("This is an automated notification. No action is required unless a discrepancy is observed.");
        sendMulti(to, null,
                "Correction Request " + decision + " – " + employeeName + " – " + workDate.format(fmt),
                wrap(approved ? "&#9989;" : "&#10060;", "Correction Request " + decision, body));
        log.info("Correction HR notification sent to {} — {} for {}", Arrays.toString(to), decision, employeeName);
    }

    // ── Daily work report: individual (To: employee) ──────────────────────────

    public void sendIndividualWorkReportEmail(String to, String employeeName,
                                              LocalDate reportDate,
                                              LocalTime firstLogin, LocalTime lastLogout,
                                              Integer totalOfficeMinutes, Integer activeMinutes,
                                              Integer breakMinutes, Integer sessionCount) {
        DateTimeFormatter fmt  = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy");
        DateTimeFormatter tfmt = DateTimeFormatter.ofPattern("hh:mm a");
        String dateStr = reportDate.format(fmt);

        String body =
                "<p>Hi <strong>" + employeeName + "</strong>,</p>" +
                        "<p>Here is your daily work report for <strong>" + dateStr + "</strong>.</p>" +
                        tableOpen() +
                        row("Date",                 dateStr) +
                        row("First Login",          firstLogin  != null ? firstLogin.format(tfmt)  : "—") +
                        row("Last Logout",          lastLogout  != null ? lastLogout.format(tfmt)  : "—") +
                        row("Total Office Hours",   fmtMin(totalOfficeMinutes)) +
                        row("Active Working Hours", fmtMin(activeMinutes)) +
                        row("Break Duration",       "<span style='color:" + breakColor(breakMinutes) + ";font-weight:bold;'>"
                                + fmtMin(breakMinutes) + "</span>") +
                        row("Sessions",             sessionCount != null ? String.valueOf(sessionCount) : "—") +
                        tableClose() +
                        note("This report is automatically generated. Please contact HR if you notice any discrepancies.");

        try {
            send(to, null,
                    "Your Daily Work Report – " + reportDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                    wrap("&#128202;", "Daily Work Report", body));
            log.info("Individual work report sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send individual work report to {}: {}", to, e.getMessage());
        }
    }

    // ── Daily work report: consolidated (To: Admin) ───────────────────────────

    public void sendConsolidatedWorkReportEmail(String to, LocalDate reportDate,
                                                java.util.List<com.emp.management.dto.DailyWorkReportDTO> reports) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy");
        String dateStr = reportDate.format(fmt);

        // Summary stats
        double avgOffice  = reports.stream().filter(r -> r.getTotalOfficeMinutes() != null)
                .mapToInt(com.emp.management.dto.DailyWorkReportDTO::getTotalOfficeMinutes).average().orElse(0);
        double avgActive  = reports.stream().filter(r -> r.getActiveMinutes() != null)
                .mapToInt(com.emp.management.dto.DailyWorkReportDTO::getActiveMinutes).average().orElse(0);
        double avgBreak   = reports.stream().filter(r -> r.getBreakMinutes() != null)
                .mapToInt(com.emp.management.dto.DailyWorkReportDTO::getBreakMinutes).average().orElse(0);

        // Employee table
        StringBuilder empRows = new StringBuilder();
        empRows.append("<table width='100%' cellpadding='0' cellspacing='0' style='border-collapse:collapse;margin:18px 0;font-size:13px;'>")
                .append("<tr style='background:#1a2847;color:white;'>")
                .append("<th style='padding:10px 12px;text-align:left;'>Employee</th>")
                .append("<th style='padding:10px 12px;text-align:left;'>Dept</th>")
                .append("<th style='padding:10px 12px;text-align:center;'>First Login</th>")
                .append("<th style='padding:10px 12px;text-align:center;'>Last Logout</th>")
                .append("<th style='padding:10px 12px;text-align:center;'>Office Hours</th>")
                .append("<th style='padding:10px 12px;text-align:center;'>Active Hours</th>")
                .append("<th style='padding:10px 12px;text-align:center;'>Break</th>")
                .append("<th style='padding:10px 12px;text-align:center;'>Sessions</th>")
                .append("</tr>");

        DateTimeFormatter tfmt = DateTimeFormatter.ofPattern("HH:mm");
        for (int i = 0; i < reports.size(); i++) {
            com.emp.management.dto.DailyWorkReportDTO r = reports.get(i);
            String bg = i % 2 == 0 ? "#f8fafc" : "#ffffff";
            empRows.append("<tr style='background:").append(bg).append(";'>")
                    .append("<td style='padding:9px 12px;border-bottom:1px solid #e2e8f0;'><strong>")
                    .append(r.getEmployeeName()).append("</strong><br/><span style='font-size:11px;color:#94a3b8;'>")
                    .append(r.getEmployeeCode()).append("</span></td>")
                    .append("<td style='padding:9px 12px;border-bottom:1px solid #e2e8f0;color:#64748b;'>").append(r.getDepartment()).append("</td>")
                    .append("<td style='padding:9px 12px;border-bottom:1px solid #e2e8f0;text-align:center;color:#16a34a;font-weight:600;'>")
                    .append(r.getFirstLoginTime() != null ? r.getFirstLoginTime().format(tfmt) : "—").append("</td>")
                    .append("<td style='padding:9px 12px;border-bottom:1px solid #e2e8f0;text-align:center;color:#dc2626;font-weight:600;'>")
                    .append(r.getLastLogoutTime() != null ? r.getLastLogoutTime().format(tfmt) : "—").append("</td>")
                    .append("<td style='padding:9px 12px;border-bottom:1px solid #e2e8f0;text-align:center;'>")
                    .append(r.getTotalOfficeFormatted()).append("</td>")
                    .append("<td style='padding:9px 12px;border-bottom:1px solid #e2e8f0;text-align:center;'>")
                    .append(r.getActiveFormatted()).append("</td>")
                    .append("<td style='padding:9px 12px;border-bottom:1px solid #e2e8f0;text-align:center;font-weight:bold;color:")
                    .append(breakColor(r.getBreakMinutes())).append(";'>").append(r.getBreakFormatted()).append("</td>")
                    .append("<td style='padding:9px 12px;border-bottom:1px solid #e2e8f0;text-align:center;'>")
                    .append(r.getSessionCount()).append("</td>")
                    .append("</tr>");
        }
        empRows.append("</table>");

        String body =
                "<p>Dear Admin,</p>" +
                        "<p>Here is the consolidated daily work report for <strong>" + dateStr + "</strong>.</p>" +
                        "<p style='font-size:13px;font-weight:bold;color:#4a5568;margin:18px 0 6px;'>Summary</p>" +
                        tableOpen() +
                        row("Total Employees Reported", String.valueOf(reports.size())) +
                        row("Avg Office Hours",         fmtMin((int) Math.round(avgOffice))) +
                        row("Avg Active Hours",         fmtMin((int) Math.round(avgActive))) +
                        row("Avg Break Duration",       fmtMin((int) Math.round(avgBreak))) +
                        tableClose() +
                        "<p style='font-size:13px;font-weight:bold;color:#4a5568;margin:18px 0 6px;'>Employee-wise Report</p>" +
                        empRows +
                        note("This report is automatically generated every morning. Login to EmpSAS for detailed view and export.");

        try {
            send(to, null,
                    "Daily Work Report Summary – " + reportDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                    wrap("&#128202;", "Daily Work Report Summary – " + dateStr, body));
            log.info("Consolidated work report sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send consolidated work report to {}: {}", to, e.getMessage());
        }
    }

    // ── PIP created (To: employee, CC: manager/admin) ─────────────────────────

    public void sendPipCreatedEmail(String employeeEmail, String[] cc,
                                    String employeeName, String createdByName,
                                    String pipTitle, LocalDate startDate, LocalDate endDate,
                                    String reason, java.util.List<String> goalTitles) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy");
        StringBuilder goalsHtml = new StringBuilder();
        if (goalTitles != null && !goalTitles.isEmpty()) {
            goalsHtml.append("<p style='font-size:13px;font-weight:bold;color:#4a5568;margin:18px 0 6px;'>Goals</p>")
                    .append("<ul style='margin:0;padding-left:20px;'>");
            for (String g : goalTitles)
                goalsHtml.append("<li style='font-size:14px;color:#374151;margin:4px 0;'>").append(g).append("</li>");
            goalsHtml.append("</ul>");
        }
        String body =
                "<p>Dear <strong>" + employeeName + "</strong>,</p>" +
                        "<p>A <strong>Performance Improvement Plan (PIP)</strong> has been assigned to you by <strong>" +
                        createdByName + "</strong>. Please review the plan and work towards achieving the set goals.</p>" +
                        tableOpen() +
                        row("Plan Title",  pipTitle) +
                        row("Start Date",  startDate.format(fmt)) +
                        row("End Date",    endDate.format(fmt)) +
                        row("Reason",      reason) +
                        tableClose() +
                        goalsHtml +
                        note("Please log in to EmpSAS to view your full improvement plan, track progress, and communicate with your manager.");

        try {
            sendMulti(new String[]{employeeEmail}, cc,
                    "Performance Improvement Plan Assigned – EmpSAS",
                    wrap("&#128203;", "Performance Improvement Plan", body));
            log.info("PIP created email sent to {}", employeeEmail);
        } catch (Exception e) {
            log.error("Failed to send PIP created email to {}: {}", employeeEmail, e.getMessage());
        }
    }

    // ── PIP outcome (To: employee, CC: manager/admin) ─────────────────────────

    public void sendPipOutcomeEmail(String employeeEmail, String[] cc,
                                    String employeeName, String pipTitle,
                                    String outcome, LocalDate outcomeDate, String finalNotes) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy");
        String statusBadge;
        String icon;
        switch (outcome) {
            case "COMPLETED":
                statusBadge = "<span style='background:#dcfce7;color:#16a34a;padding:3px 10px;border-radius:12px;font-weight:bold;font-size:13px;'>COMPLETED</span>";
                icon = "&#9989;";
                break;
            case "EXTENDED":
                statusBadge = "<span style='background:#fff7ed;color:#c2410c;padding:3px 10px;border-radius:12px;font-weight:bold;font-size:13px;'>EXTENDED</span>";
                icon = "&#128336;";
                break;
            default:
                statusBadge = "<span style='background:#fee2e2;color:#dc2626;padding:3px 10px;border-radius:12px;font-weight:bold;font-size:13px;'>TERMINATED</span>";
                icon = "&#10060;";
        }
        String body =
                "<p>Dear <strong>" + employeeName + "</strong>,</p>" +
                        "<p>Your Performance Improvement Plan has been updated with a final decision.</p>" +
                        tableOpen() +
                        row("Plan Title",   pipTitle) +
                        row("Outcome",      statusBadge) +
                        row("Date",         outcomeDate != null ? outcomeDate.format(fmt) : "—") +
                        row("Final Notes",  finalNotes) +
                        tableClose() +
                        note("Please log in to EmpSAS to view the complete details of your performance plan and next steps.");

        try {
            sendMulti(new String[]{employeeEmail}, cc,
                    "PIP Update: " + pipTitle + " – EmpSAS",
                    wrap(icon, "Performance Improvement Plan – " + outcome, body));
            log.info("PIP outcome email sent to {} — outcome: {}", employeeEmail, outcome);
        } catch (Exception e) {
            log.error("Failed to send PIP outcome email to {}: {}", employeeEmail, e.getMessage());
        }
    }

    private String fmtMin(Integer minutes) {
        if (minutes == null || minutes <= 0) return "0h 00m";
        return (minutes / 60) + "h " + String.format("%02d", minutes % 60) + "m";
    }

    private String breakColor(Integer mins) {
        if (mins == null || mins <= 60) return "#16a34a";
        if (mins <= 90) return "#d97706";
        return "#dc2626";
    }

    // ── Interview: round assigned (To: interviewer) ───────────────────────────

    public void sendInterviewAssignedEmail(String to, String interviewerName,
                                           String candidateName, String candidatePosition,
                                           String roundType, String scheduledAtFormatted,
                                           String location, String managerNotes,
                                           Long roundId, LocalDateTime scheduledAt,
                                           Integer durationMinutes, String assignedByName) {
        String body =
                "<p>Dear <strong>" + interviewerName + "</strong>,</p>" +
                        "<p>You have been assigned to conduct an interview. Please find the details below:</p>" +
                        tableOpen() +
                        row("Candidate",     candidateName) +
                        row("Position",      candidatePosition) +
                        row("Round Type",    roundType.replace("_", " ")) +
                        row("Scheduled At",  scheduledAtFormatted) +
                        row("Location/Link", location != null ? location : "—") +
                        row("Notes",         managerNotes != null ? managerNotes : "—") +
                        tableClose() +
                        "<p style='margin:16px 0;font-size:13px;color:#475569;'>" +
                        "&#128197; A calendar invite is attached. Open it to add this interview to your calendar with reminders.</p>" +
                        note("Please log in to EmpSAS to view full candidate details and submit your feedback after the interview.");

        String subject = "Interview Assignment: " + candidateName + " – " + roundType.replace("_", " ") + " Round – EmpSAS";
        String html    = wrap("&#128101;", "Interview Round Assignment", body);
        String ics     = generateInterviewIcs(roundId, to, interviewerName, candidateName,
                                              candidatePosition, roundType, scheduledAt,
                                              durationMinutes, location, managerNotes, assignedByName);
        try {
            sendWithCalendarAttachment(to, subject, html, ics);
            log.info("Interview assignment email with calendar invite sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send interview assignment email to {}: {}", to, e.getMessage());
        }
    }

    /** Builds a VCALENDAR/VEVENT ICS string for an interview round. */
    public String generateInterviewIcs(Long roundId, String attendeeEmail, String attendeeName,
                                        String candidateName, String candidatePosition,
                                        String roundType, LocalDateTime scheduledAt,
                                        Integer durationMinutes, String location,
                                        String notes, String organizerName) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
        String now   = LocalDateTime.now().format(fmt);
        String start = scheduledAt != null ? scheduledAt.format(fmt) : LocalDateTime.now().plusDays(1).format(fmt);
        int    mins  = durationMinutes != null && durationMinutes > 0 ? durationMinutes : 60;
        String end   = (scheduledAt != null ? scheduledAt.plusMinutes(mins) : LocalDateTime.now().plusDays(1).plusHours(1)).format(fmt);

        String uid     = "interview-round-" + roundId + "-" + UUID.randomUUID() + "@empsas";
        String summary = "Interview: " + candidateName + " – " + roundType.replace("_", " ") + " Round";
        String desc    = "Candidate: " + candidateName + "\\n" +
                         "Position: " + candidatePosition + "\\n" +
                         "Round Type: " + roundType.replace("_", " ") + "\\n" +
                         "Assigned By: " + (organizerName != null ? organizerName : "EmpSAS") +
                         (notes != null && !notes.isBlank() ? "\\nNotes: " + notes : "");

        StringBuilder ics = new StringBuilder();
        ics.append("BEGIN:VCALENDAR\r\n");
        ics.append("VERSION:2.0\r\n");
        ics.append("PRODID:-//EmpSAS//Interview Management//EN\r\n");
        ics.append("METHOD:REQUEST\r\n");
        ics.append("CALSCALE:GREGORIAN\r\n");
        ics.append("BEGIN:VEVENT\r\n");
        ics.append("UID:").append(uid).append("\r\n");
        ics.append("DTSTAMP:").append(now).append("\r\n");
        ics.append("DTSTART:").append(start).append("\r\n");
        ics.append("DTEND:").append(end).append("\r\n");
        ics.append("SUMMARY:").append(summary).append("\r\n");
        ics.append("DESCRIPTION:").append(desc).append("\r\n");
        if (location != null && !location.isBlank()) {
            ics.append("LOCATION:").append(location).append("\r\n");
        }
        ics.append("ORGANIZER;CN=EmpSAS:mailto:noreply@empsas.com\r\n");
        ics.append("ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;CN=")
           .append(attendeeName).append(":mailto:").append(attendeeEmail).append("\r\n");
        ics.append("BEGIN:VALARM\r\n");
        ics.append("TRIGGER:-PT1H\r\n");
        ics.append("ACTION:DISPLAY\r\n");
        ics.append("DESCRIPTION:Interview in 1 hour – ").append(candidateName).append("\r\n");
        ics.append("END:VALARM\r\n");
        ics.append("BEGIN:VALARM\r\n");
        ics.append("TRIGGER:-PT15M\r\n");
        ics.append("ACTION:DISPLAY\r\n");
        ics.append("DESCRIPTION:Interview in 15 minutes – ").append(candidateName).append("\r\n");
        ics.append("END:VALARM\r\n");
        ics.append("END:VEVENT\r\n");
        ics.append("END:VCALENDAR\r\n");
        return ics.toString();
    }

    /** Sends an HTML email with a .ics calendar invite attached. */
    private void sendWithCalendarAttachment(String to, String subject, String html, String icsContent) throws Exception {
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);
        byte[] icsBytes = icsContent.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        helper.addAttachment("interview-invite.ics",
                new ByteArrayResource(icsBytes), "text/calendar; method=REQUEST");
        mailSender.send(msg);
    }

    // ── Interview: feedback submitted (To: manager) ───────────────────────────

    public void sendInterviewFeedbackEmail(String to, String managerName,
                                           String interviewerName, String candidateName,
                                           String roundType, String recommendation, String overallRating) {
        String recBg = recommendation != null && (recommendation.contains("HIRE") && !recommendation.contains("NO"))
                ? "#dcfce7" : recommendation != null && recommendation.contains("NO") ? "#fee2e2" : "#fff7ed";
        String recColor = recommendation != null && (recommendation.contains("HIRE") && !recommendation.contains("NO"))
                ? "#16a34a" : recommendation != null && recommendation.contains("NO") ? "#dc2626" : "#d97706";
        String recBadge = recommendation == null ? "&mdash;" :
                "<span style='background:" + recBg + ";color:" + recColor + ";padding:3px 10px;border-radius:12px;font-weight:bold;font-size:13px;'>"
                + recommendation.replace("_", " ") + "</span>";

        String body =
                "<p>Dear <strong>" + managerName + "</strong>,</p>" +
                        "<p><strong>" + interviewerName + "</strong> has submitted feedback for the interview round.</p>" +
                        tableOpen() +
                        row("Candidate",      candidateName) +
                        row("Round Type",     roundType.replace("_", " ")) +
                        row("Interviewer",    interviewerName) +
                        row("Overall Rating", overallRating + " / 5") +
                        row("Recommendation", recBadge) +
                        tableClose() +
                        note("Log in to EmpSAS to review the full feedback and decide the next action for this candidate.");

        try {
            send(to, null, "Interview Feedback Submitted: " + candidateName + " – EmpSAS",
                    wrap("&#128203;", "Interview Feedback Received", body));
            log.info("Interview feedback notification email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send interview feedback email to {}: {}", to, e.getMessage());
        }
    }

    // ── Interview: candidate decision (To: panel / CC: HR) ───────────────────

    public void sendCandidateDecisionEmail(String[] to, String candidateName,
                                           String position, String decision, String decidedBy) {
        String badge;
        String icon;
        switch (decision) {
            case "SELECTED":
                badge = "<span style='background:#dcfce7;color:#16a34a;padding:3px 10px;border-radius:12px;font-weight:bold;font-size:13px;'>SELECTED</span>";
                icon = "&#9989;";
                break;
            case "REJECTED":
                badge = "<span style='background:#fee2e2;color:#dc2626;padding:3px 10px;border-radius:12px;font-weight:bold;font-size:13px;'>REJECTED</span>";
                icon = "&#10060;";
                break;
            default:
                badge = "<span style='background:#fff7ed;color:#d97706;padding:3px 10px;border-radius:12px;font-weight:bold;font-size:13px;'>" + decision.replace("_", " ") + "</span>";
                icon = "&#128336;";
        }
        String body =
                "<p>A final decision has been made for the following candidate:</p>" +
                        tableOpen() +
                        row("Candidate",   candidateName) +
                        row("Position",    position) +
                        row("Decision",    badge) +
                        row("Decided By",  decidedBy) +
                        tableClose() +
                        note("Log in to EmpSAS to view the full interview history and all feedback for this candidate.");

        try {
            if (to != null && to.length > 0)
                sendMulti(to, null, "Candidate Decision: " + candidateName + " – EmpSAS",
                        wrap(icon, "Interview Decision", body));
            log.info("Candidate decision email sent for {}", candidateName);
        } catch (Exception e) {
            log.error("Failed to send candidate decision email: {}", e.getMessage());
        }
    }

    // ── Interview: 30-minute reminder (To: interviewer) ───────────────────────

    public void sendInterviewReminderEmail(String to, String interviewerName,
                                           String candidateName, String candidatePosition,
                                           String roundType, LocalDateTime scheduledAt,
                                           String location) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
        String timeStr = scheduledAt != null ? scheduledAt.format(fmt) : "—";

        String body =
                "<p>Dear <strong>" + interviewerName + "</strong>,</p>" +
                "<p>&#9203; This is a reminder that your interview is starting in <strong>30 minutes</strong>.</p>" +
                tableOpen() +
                row("Candidate",     candidateName) +
                row("Position",      candidatePosition) +
                row("Round Type",    roundType.replace("_", " ")) +
                row("Scheduled At",  timeStr) +
                row("Location/Link", location != null && !location.isBlank() ? location : "—") +
                tableClose() +
                note("Please log in to EmpSAS to review the candidate profile before the interview.");

        String subject = "Reminder: Interview in 30 Minutes — " + candidateName + " (" + roundType.replace("_", " ") + ") – EmpSAS";
        try {
            send(to, null, subject, wrap("&#128276;", "Interview Reminder", body));
            log.info("Interview reminder email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send interview reminder email to {}: {}", to, e.getMessage());
        }
    }

    public void sendVideoInterviewInvitation(
            String to, String candidateName, String position, String technology,
            int durationMinutes, int numQuestions, String interviewLink,
            java.time.LocalDateTime expiresAt) {

        String expiryStr = expiresAt != null
                ? expiresAt.format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"))
                : "7 days from now";

        String body = "<p>Dear <strong>" + candidateName + "</strong>,</p>" +
                "<p>You have been invited to complete an <strong>Online Video Interview</strong>. " +
                "Please review the details below and click the button to begin when you are ready.</p>" +
                tableOpen() +
                row("Position",          position != null ? position : "—") +
                row("Technology",        technology) +
                row("Duration",          durationMinutes + " minutes") +
                row("No. of Questions",  String.valueOf(numQuestions)) +
                row("Link Expires",      expiryStr) +
                tableClose() +
                "<div style='text-align:center;margin:28px 0;'>" +
                "<a href='" + interviewLink + "' " +
                "style='background:#1a2847;color:#ffffff;padding:14px 32px;border-radius:8px;" +
                "text-decoration:none;font-size:16px;font-weight:bold;display:inline-block;'>" +
                "&#127916; Start My Interview</a></div>" +
                "<p>Or copy this link: <a href='" + interviewLink + "' style='color:#1a2847;'>" + interviewLink + "</a></p>" +
                "<div style='background:#fff8e1;border-left:4px solid #f59e0b;padding:12px 16px;margin:16px 0;border-radius:4px;'>" +
                "<strong>&#9888; Important Instructions:</strong><ul style='margin:8px 0;padding-left:18px;'>" +
                "<li>Use a desktop/laptop browser (Chrome or Firefox recommended)</li>" +
                "<li>Allow camera and microphone access when prompted</li>" +
                "<li>Ensure a stable internet connection</li>" +
                "<li>The interview must be completed in a single sitting — do not close the browser</li>" +
                "<li>Switching tabs or windows will be detected and may result in auto-submission</li>" +
                "</ul></div>" +
                note("This link is unique to you. Do not share it with others.");

        String subject = "Video Interview Invitation — " + (position != null ? position : technology) + " – EmpSAS";
        try {
            send(to, null, subject, wrap("&#127916;", "Video Interview Invitation", body));
            log.info("Video interview invitation sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send video interview invitation to {}: {}", to, e.getMessage());
        }
    }

    // ── Job time tracking: break exceeded 60 minutes (To: manager, CC: HR/Admin) ─

    public void sendJobBreakAlertEmail(String[] to, String[] cc,
                                       String employeeName, String employeeCode, String department,
                                       String client, String jobName, String jobTask,
                                       LocalDateTime breakStartedAt, String currentBreakDuration, LocalDate date) {
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy");
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("hh:mm a");
        String body =
                "<p>Dear Team,</p>" +
                        "<p>The following employee's break has exceeded <strong>60 minutes</strong>.</p>" +
                        "<p style='font-size:13px;font-weight:bold;color:#4a5568;margin:18px 0 6px;'>Employee Details</p>" +
                        tableOpen() +
                        row("Employee Name", employeeName) +
                        row("Employee ID",   employeeCode) +
                        row("Department",    department) +
                        tableClose() +
                        "<p style='font-size:13px;font-weight:bold;color:#4a5568;margin:18px 0 6px;'>Break Details</p>" +
                        tableOpen() +
                        row("Client",       client) +
                        row("Job Name",     jobName) +
                        row("Job Task",     jobTask) +
                        row("Break Started At",    breakStartedAt.format(timeFmt)) +
                        row("Current Break Duration",
                                "<span style='color:#dc2626;font-weight:bold;'>" + currentBreakDuration + "</span>") +
                        row("Date",     date.format(dateFmt)) +
                        tableClose() +
                        note("Employee: please resume work and log back in promptly. Manager: please follow up if required.");

        sendMulti(to, cc,
                "Employee Break Alert – Break Exceeded 60 Minutes",
                wrap("&#9202;", "Employee Break Alert", body));
        log.info("Job break alert sent to {} (cc: {}) for {} — break since {}",
                Arrays.toString(to), Arrays.toString(cc), employeeName, breakStartedAt);
    }

    // ── Job time tracking: consolidated under-hours audit (To: manager, CC: HR/Admin) ─

    public static class UnderHoursRow {
        public final String employeeId;
        public final String employeeName;
        public final String department;
        public final String client;
        public final String workingHours;
        public final String breakTime;
        public final String requiredHours;
        public final String shortfall;
        public final String attendanceDate;
        public final String loginTime;
        public final String logoutTime;
        public final String attendanceStatus;

        public UnderHoursRow(String employeeId, String employeeName, String department, String client,
                              String workingHours, String breakTime, String requiredHours, String shortfall,
                              String attendanceDate, String loginTime, String logoutTime, String attendanceStatus) {
            this.employeeId = employeeId;
            this.employeeName = employeeName;
            this.department = department;
            this.client = client;
            this.workingHours = workingHours;
            this.breakTime = breakTime;
            this.requiredHours = requiredHours;
            this.shortfall = shortfall;
            this.attendanceDate = attendanceDate;
            this.loginTime = loginTime;
            this.logoutTime = logoutTime;
            this.attendanceStatus = attendanceStatus;
        }
    }

    public void sendUnderHoursAuditAlert(String to, String[] cc, LocalDate date, List<UnderHoursRow> rows) {
        String dateStr = date.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy"));

        StringBuilder table = new StringBuilder();
        table.append("<table width='100%' cellpadding='0' cellspacing='0' ")
             .append("style='border-collapse:collapse;margin:18px 0;font-size:12px;'>")
             .append("<tr style='background:#1a2847;color:#ffffff;'>")
             .append("<th style='padding:8px 10px;text-align:left;'>Employee ID</th>")
             .append("<th style='padding:8px 10px;text-align:left;'>Employee</th>")
             .append("<th style='padding:8px 10px;text-align:left;'>Department</th>")
             .append("<th style='padding:8px 10px;text-align:left;'>Client</th>")
             .append("<th style='padding:8px 10px;text-align:right;'>Working Hours</th>")
             .append("<th style='padding:8px 10px;text-align:right;'>Break Time</th>")
             .append("<th style='padding:8px 10px;text-align:right;'>Required Hours</th>")
             .append("<th style='padding:8px 10px;text-align:right;'>Shortfall</th>")
             .append("<th style='padding:8px 10px;text-align:left;'>Date</th>")
             .append("<th style='padding:8px 10px;text-align:left;'>Login</th>")
             .append("<th style='padding:8px 10px;text-align:left;'>Logout</th>")
             .append("<th style='padding:8px 10px;text-align:left;'>Status</th>")
             .append("</tr>");
        boolean stripe = false;
        for (UnderHoursRow r : rows) {
            String bg = stripe ? "#f4f6f9" : "#ffffff";
            table.append("<tr style='background:").append(bg).append(";'>")
                 .append("<td style='padding:7px 10px;border-bottom:1px solid #dde3ec;'>").append(r.employeeId != null ? r.employeeId : "&mdash;").append("</td>")
                 .append("<td style='padding:7px 10px;border-bottom:1px solid #dde3ec;'>").append(r.employeeName).append("</td>")
                 .append("<td style='padding:7px 10px;border-bottom:1px solid #dde3ec;'>").append(r.department != null ? r.department : "&mdash;").append("</td>")
                 .append("<td style='padding:7px 10px;border-bottom:1px solid #dde3ec;'>").append(r.client != null ? r.client : "&mdash;").append("</td>")
                 .append("<td style='padding:7px 10px;border-bottom:1px solid #dde3ec;text-align:right;'>").append(r.workingHours).append("</td>")
                 .append("<td style='padding:7px 10px;border-bottom:1px solid #dde3ec;text-align:right;'>").append(r.breakTime).append("</td>")
                 .append("<td style='padding:7px 10px;border-bottom:1px solid #dde3ec;text-align:right;'>").append(r.requiredHours).append("</td>")
                 .append("<td style='padding:7px 10px;border-bottom:1px solid #dde3ec;text-align:right;color:#dc2626;font-weight:bold;'>").append(r.shortfall).append("</td>")
                 .append("<td style='padding:7px 10px;border-bottom:1px solid #dde3ec;'>").append(r.attendanceDate).append("</td>")
                 .append("<td style='padding:7px 10px;border-bottom:1px solid #dde3ec;'>").append(r.loginTime != null ? r.loginTime : "&mdash;").append("</td>")
                 .append("<td style='padding:7px 10px;border-bottom:1px solid #dde3ec;'>").append(r.logoutTime != null ? r.logoutTime : "&mdash;").append("</td>")
                 .append("<td style='padding:7px 10px;border-bottom:1px solid #dde3ec;'>").append(r.attendanceStatus).append("</td>")
                 .append("</tr>");
            stripe = !stripe;
        }
        table.append("</table>");

        String body =
                "<p>Dear Manager,</p>" +
                        "<p>The following direct reportees worked below the required 8 hours on <strong>" + dateStr + "</strong> " +
                        "(approved leave, holidays, weekends, and approved attendance corrections are already excluded).</p>" +
                        table +
                        note("Please review and follow up with these employees if required.");

        sendMulti(new String[]{to}, cc,
                "Attendance Audit Alert – Employees with Under 8 Working Hours",
                wrap("&#9888;&#65039;", "Attendance Audit Alert", body));
        log.info("Under-hours audit alert sent to {} (cc: {}) for {} — {} employee(s)",
                to, Arrays.toString(cc), date, rows.size());
    }
}

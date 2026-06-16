package com.emp.management.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
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
}

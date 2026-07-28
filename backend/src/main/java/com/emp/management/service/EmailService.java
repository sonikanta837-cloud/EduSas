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

    // ── Job time tracking: break exceeded threshold — reporting manager notification ─

    public void sendJobBreakAlertToManager(String to, String[] cc,
                                            String employeeName, String employeeCode, String department,
                                            String designation, String jobName,
                                            LocalDateTime breakStartedAt, int currentDurationMinutes,
                                            int allowedDurationMinutes, LocalDate date) {
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd MMMM yyyy");
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("hh:mm a");
        String body =
                "<p>Dear Reporting Manager,</p>" +
                        "<p>This is an automated notification from EduSAS.</p>" +
                        "<p>The following employee has exceeded the maximum allowed break duration.</p>" +
                        "<p style='font-size:13px;font-weight:bold;color:#4a5568;margin:18px 0 6px;'>Employee Details</p>" +
                        tableOpen() +
                        row("Employee Name", employeeName) +
                        row("Employee ID",   employeeCode) +
                        row("Department",    department) +
                        row("Designation",   designation) +
                        row("Job Name",      jobName) +
                        tableClose() +
                        "<p style='font-size:13px;font-weight:bold;color:#4a5568;margin:18px 0 6px;'>Break Details</p>" +
                        tableOpen() +
                        row("Break Started At", breakStartedAt.format(timeFmt)) +
                        row("Current Duration",
                                "<span style='color:#dc2626;font-weight:bold;'>" + fmtHourMin(currentDurationMinutes) + "</span>") +
                        row("Allowed Duration", fmtHourMin(allowedDurationMinutes)) +
                        row("Date",             date.format(dateFmt)) +
                        tableClose() +
                        note("Please contact the employee if additional action or approval is required.");

        sendMulti(new String[]{to}, cc,
                "Break Alert: Employee Break Exceeded " + allowedDurationMinutes + " Minutes – " + employeeName,
                wrap("&#9202;", "Break Alert", body));
        log.info("Job break alert sent to manager {} (cc: {}) for {} — break since {}",
                to, Arrays.toString(cc), employeeName, breakStartedAt);
    }

    // ── Job time tracking: break exceeded threshold — employee reminder ──────

    public void sendJobBreakAlertToEmployee(String to, String employeeName, String jobName,
                                             LocalDateTime breakStartedAt, int currentDurationMinutes,
                                             int allowedDurationMinutes, LocalDate date) {
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd MMMM yyyy");
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("hh:mm a");
        String body =
                "<p>Dear " + employeeName + ",</p>" +
                        "<p>This is an automated reminder from EduSAS.</p>" +
                        "<p>Our records indicate that your break has exceeded the maximum permitted duration.</p>" +
                        "<p style='font-size:13px;font-weight:bold;color:#4a5568;margin:18px 0 6px;'>Break Details</p>" +
                        tableOpen() +
                        row("Job Name",         jobName) +
                        row("Break Started At", breakStartedAt.format(timeFmt)) +
                        row("Current Duration",
                                "<span style='color:#dc2626;font-weight:bold;'>" + fmtHourMin(currentDurationMinutes) + "</span>") +
                        row("Allowed Duration", fmtHourMin(allowedDurationMinutes)) +
                        row("Date",             date.format(dateFmt)) +
                        tableClose() +
                        "<p>Please resume your work as soon as possible.</p>" +
                        note("If you require additional break time due to an emergency or other valid reason, please inform your Reporting Manager.");

        send(to, null,
                "Reminder: Your Break Has Exceeded the Allowed Duration",
                wrap("&#9202;", "Break Reminder", body));
        log.info("Job break reminder sent to employee {} — break since {}", to, breakStartedAt);
    }

    private String fmtHourMin(int minutes) {
        int m = Math.max(0, minutes);
        return String.format("%02d Hour %02d Minutes", m / 60, m % 60);
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
                        "(approved leave, holidays, and weekends are already excluded).</p>" +
                        table +
                        note("Please review and follow up with these employees if required.");

        sendMulti(new String[]{to}, cc,
                "Attendance Audit Alert – Employees with Under 8 Working Hours",
                wrap("&#9888;&#65039;", "Attendance Audit Alert", body));
        log.info("Under-hours audit alert sent to {} (cc: {}) for {} — {} employee(s)",
                to, Arrays.toString(cc), date, rows.size());
    }
}

package com.emp.management.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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

    public void sendLeaveRequestEmail(String to, String employeeEmail, String employeeName,
                                      String leaveType, LocalDate startDate, LocalDate endDate,
                                      int totalDays, String reason) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
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
            log.info("Leave request email sent to {} for employee {}", to, employeeName);
        } catch (Exception e) {
            log.error("Failed to send leave request email to {}: {}", to, e.getMessage());
        }
    }

    public void sendLeaveDecisionEmail(String to, String employeeName, String status,
                                       String leaveType, LocalDate startDate, LocalDate endDate,
                                       String managerName, String comment) {
        try {
            boolean approved = "APPROVED".equalsIgnoreCase(status);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("Leave Request " + (approved ? "Approved" : "Rejected") + " - EmpSAS");
            message.setText(
                "Leave Request Update\n\n" +
                "Your leave request has been " + status + ".\n\n" +
                "Leave Type: " + leaveType + "\n" +
                "From      : " + startDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) + "\n" +
                "To        : " + endDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) + "\n" +
                "Reviewed by: " + managerName + "\n" +
                (comment != null && !comment.isBlank() ? "Comment   : " + comment + "\n" : "") +
                "\nEmpSAS Team"
            );
            mailSender.send(message);
            log.info("Leave decision email sent to {} — status: {}", to, status);
        } catch (Exception e) {
            log.error("Failed to send leave decision email to {}: {}", to, e.getMessage());
        }
    }

    public void sendOvertimeAlert(String to, String employeeName, Double hours, LocalDate date) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("Overtime Alert - " + employeeName);
            message.setText(
                "Overtime Alert\n\n" +
                "Employee: " + employeeName + "\n" +
                "Date: " + date + "\n" +
                "Hours Worked: " + String.format("%.1f", hours) + "h\n\n" +
                "This employee has exceeded 8 hours today.\n\n" +
                "EmpSAS Team"
            );
            mailSender.send(message);
            log.info("Overtime alert sent to {} for employee {}", to, employeeName);
        } catch (Exception e) {
            log.error("Failed to send overtime alert to {}: {}", to, e.getMessage());
        }
    }
}

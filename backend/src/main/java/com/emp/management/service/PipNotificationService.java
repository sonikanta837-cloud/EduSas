package com.emp.management.service;

import com.emp.management.dto.PipNotificationDTO;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.PerformanceImprovementPlan;
import com.emp.management.entity.PipNotification;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.repository.PipNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PipNotificationService {

    private final PipNotificationRepository repo;
    private final EmployeeDetailsRepository employeeRepo;

    @Transactional
    public void notifyComment(PerformanceImprovementPlan pip, EmployeeDetails sender, String commentText) {
        String preview = commentText.length() > 80 ? commentText.substring(0, 77) + "..." : commentText;
        String message = sender.getFullName() + " commented on \"" + pip.getTitle() + "\": " + preview;

        // Notify pip employee if they are not the sender
        if (!pip.getEmployee().getId().equals(sender.getId())) {
            save(pip, pip.getEmployee(), sender.getFullName(), message);
        }
        // Notify pip creator if they are not the sender and not the same as employee
        if (!pip.getCreatedBy().getId().equals(sender.getId()) &&
            !pip.getCreatedBy().getId().equals(pip.getEmployee().getId())) {
            save(pip, pip.getCreatedBy(), sender.getFullName(), message);
        }
    }

    public List<PipNotificationDTO> getUnread(String username) {
        EmployeeDetails emp = resolve(username);
        if (emp == null) return List.of();
        return repo.findByRecipientIdAndReadFalseOrderByCreatedAtDesc(emp.getId())
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public long getUnreadCount(String username) {
        EmployeeDetails emp = resolve(username);
        if (emp == null) return 0;
        return repo.countByRecipientIdAndReadFalse(emp.getId());
    }

    @Transactional
    public void markAsRead(Long id, String username) {
        EmployeeDetails emp = resolve(username);
        if (emp == null) return;
        repo.findById(id).ifPresent(n -> {
            if (n.getRecipient().getId().equals(emp.getId())) { // IDOR guard
                n.setRead(true);
                repo.save(n);
            }
        });
    }

    @Transactional
    public void markAllRead(String username) {
        EmployeeDetails emp = resolve(username);
        if (emp == null) return;
        repo.markAllReadByRecipientId(emp.getId());
    }

    private void save(PerformanceImprovementPlan pip, EmployeeDetails recipient, String senderName, String message) {
        repo.save(PipNotification.builder()
                .pip(pip).recipient(recipient)
                .senderName(senderName).message(message)
                .read(false).build());
    }

    private EmployeeDetails resolve(String username) {
        if (username == null) return null;
        return employeeRepo.findByUserEmail(username).orElse(null);
    }

    private PipNotificationDTO toDTO(PipNotification n) {
        return PipNotificationDTO.builder()
                .id(n.getId())
                .pipId(n.getPip().getId())
                .pipTitle(n.getPip().getTitle())
                .senderName(n.getSenderName())
                .message(n.getMessage())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}

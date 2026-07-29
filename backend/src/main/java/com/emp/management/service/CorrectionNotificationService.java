package com.emp.management.service;

import com.emp.management.dto.CorrectionNotificationDTO;
import com.emp.management.entity.CorrectionNotification;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.WorkingHoursCorrectionRequest;
import com.emp.management.repository.CorrectionNotificationRepository;
import com.emp.management.repository.EmployeeDetailsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CorrectionNotificationService {

    private final CorrectionNotificationRepository repo;
    private final EmployeeDetailsRepository employeeRepo;

    @Transactional
    public void notify(WorkingHoursCorrectionRequest request, EmployeeDetails recipient, String senderName, String message) {
        repo.save(CorrectionNotification.builder()
                .correctionRequest(request).recipient(recipient)
                .senderName(senderName).message(message)
                .read(false).build());
    }

    public List<CorrectionNotificationDTO> getUnread(String username) {
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

    private EmployeeDetails resolve(String username) {
        if (username == null) return null;
        return employeeRepo.findByUserEmail(username).orElse(null);
    }

    private CorrectionNotificationDTO toDTO(CorrectionNotification n) {
        return CorrectionNotificationDTO.builder()
                .id(n.getId())
                .correctionRequestId(n.getCorrectionRequest().getId())
                .senderName(n.getSenderName())
                .message(n.getMessage())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}

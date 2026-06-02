package com.emp.management.service;

import com.emp.management.dto.CourseNotificationDTO;
import com.emp.management.entity.Course;
import com.emp.management.entity.CourseNotification;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.repository.CourseNotificationRepository;
import com.emp.management.repository.EmployeeDetailsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseNotificationService {

    private final CourseNotificationRepository notifRepo;
    private final EmployeeDetailsRepository employeeRepo;

    @Transactional
    public void createNotification(EmployeeDetails employee, Course course) {
        notifRepo.save(CourseNotification.builder()
                .employee(employee)
                .course(course)
                .read(false)
                .build());
    }

    public List<CourseNotificationDTO> getUnread(String username) {
        EmployeeDetails employee = resolve(username);
        if (employee == null) return List.of();
        return notifRepo
                .findByEmployeeIdAndReadFalseOrderByCreatedAtDesc(employee.getId())
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public long getUnreadCount(String username) {
        EmployeeDetails employee = resolve(username);
        if (employee == null) return 0;
        return notifRepo.countByEmployeeIdAndReadFalse(employee.getId());
    }

    @Transactional
    public void markAsRead(Long notifId, String username) {
        notifRepo.findById(notifId).ifPresent(n -> {
            n.setRead(true);
            notifRepo.save(n);
        });
    }

    @Transactional
    public void markAllRead(String username) {
        EmployeeDetails employee = resolve(username);
        if (employee == null) return;
        notifRepo.markAllReadByEmployeeId(employee.getId());
    }

    private EmployeeDetails resolve(String username) {
        if (username == null) return null;
        return employeeRepo.findByUserEmail(username).orElse(null);
    }

    private CourseNotificationDTO toDTO(CourseNotification n) {
        return CourseNotificationDTO.builder()
                .id(n.getId())
                .courseId(n.getCourse().getId())
                .courseTitle(n.getCourse().getTitle())
                .courseDescription(n.getCourse().getDescription())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}

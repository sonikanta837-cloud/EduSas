package com.emp.management.service;

import com.emp.management.dto.CourseNotificationDTO;
import com.emp.management.entity.*;
import com.emp.management.repository.CourseNotificationRepository;
import com.emp.management.repository.EmployeeDetailsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseNotificationServiceTest {

    @Mock private CourseNotificationRepository notifRepo;
    @Mock private EmployeeDetailsRepository employeeRepo;

    @InjectMocks private CourseNotificationService courseNotificationService;

    private EmployeeDetails employee;
    private Course course;
    private CourseNotification notification;

    @BeforeEach
    void setUp() {
        User empUser = User.builder().id(1L).email("emp@company.com").role(Role.EMPLOYEE).build();
        employee = EmployeeDetails.builder().id(1L).user(empUser)
                .firstName("John").lastName("Doe").build();
        empUser.setEmployeeDetails(employee);

        course = Course.builder().id(5L).title("Java Fundamentals")
                .description("Learn Java basics").active(true).build();

        notification = CourseNotification.builder()
                .id(1L).employee(employee).course(course).read(false).build();
    }

    // ── createNotification ────────────────────────────────────────────────────

    @Test
    void createNotification_savesNotification() {
        when(notifRepo.save(any())).thenReturn(notification);

        courseNotificationService.createNotification(employee, course);

        verify(notifRepo).save(argThat(n -> {
            CourseNotification cn = (CourseNotification) n;
            return cn.getEmployee().equals(employee)
                    && cn.getCourse().equals(course)
                    && !cn.isRead();
        }));
    }

    // ── getUnread ─────────────────────────────────────────────────────────────

    @Test
    void getUnread_returnsNotifications() {
        when(employeeRepo.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(notifRepo.findByEmployeeIdAndReadFalseOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(notification));

        List<CourseNotificationDTO> result = courseNotificationService.getUnread("emp@company.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCourseTitle()).isEqualTo("Java Fundamentals");
        assertThat(result.get(0).isRead()).isFalse();
    }

    @Test
    void getUnread_nullUsername_returnsEmptyList() {
        List<CourseNotificationDTO> result = courseNotificationService.getUnread(null);

        assertThat(result).isEmpty();
        verify(employeeRepo, never()).findByUserEmail(any());
    }

    @Test
    void getUnread_unknownUser_returnsEmptyList() {
        when(employeeRepo.findByUserEmail("ghost@company.com")).thenReturn(Optional.empty());

        List<CourseNotificationDTO> result = courseNotificationService.getUnread("ghost@company.com");

        assertThat(result).isEmpty();
    }

    @Test
    void getUnread_emptyList_returnsEmpty() {
        when(employeeRepo.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(notifRepo.findByEmployeeIdAndReadFalseOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        List<CourseNotificationDTO> result = courseNotificationService.getUnread("emp@company.com");

        assertThat(result).isEmpty();
    }

    // ── getUnreadCount ────────────────────────────────────────────────────────

    @Test
    void getUnreadCount_returnsCount() {
        when(employeeRepo.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(notifRepo.countByEmployeeIdAndReadFalse(1L)).thenReturn(3L);

        long count = courseNotificationService.getUnreadCount("emp@company.com");

        assertThat(count).isEqualTo(3L);
    }

    @Test
    void getUnreadCount_nullUsername_returnsZero() {
        long count = courseNotificationService.getUnreadCount(null);

        assertThat(count).isEqualTo(0L);
    }

    @Test
    void getUnreadCount_unknownUser_returnsZero() {
        when(employeeRepo.findByUserEmail("ghost@company.com")).thenReturn(Optional.empty());

        long count = courseNotificationService.getUnreadCount("ghost@company.com");

        assertThat(count).isEqualTo(0L);
    }

    // ── markAsRead ────────────────────────────────────────────────────────────

    @Test
    void markAsRead_existingNotification_marksRead() {
        when(notifRepo.findById(1L)).thenReturn(Optional.of(notification));
        when(notifRepo.save(any())).thenReturn(notification);

        courseNotificationService.markAsRead(1L, "emp@company.com");

        assertThat(notification.isRead()).isTrue();
        verify(notifRepo).save(notification);
    }

    @Test
    void markAsRead_notificationNotFound_doesNothing() {
        when(notifRepo.findById(999L)).thenReturn(Optional.empty());

        courseNotificationService.markAsRead(999L, "emp@company.com");

        verify(notifRepo, never()).save(any());
    }

    // ── markAllRead ───────────────────────────────────────────────────────────

    @Test
    void markAllRead_callsRepository() {
        when(employeeRepo.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        doNothing().when(notifRepo).markAllReadByEmployeeId(1L);

        courseNotificationService.markAllRead("emp@company.com");

        verify(notifRepo).markAllReadByEmployeeId(1L);
    }

    @Test
    void markAllRead_nullUsername_doesNothing() {
        courseNotificationService.markAllRead(null);

        verify(notifRepo, never()).markAllReadByEmployeeId(any());
    }

    @Test
    void markAllRead_unknownUser_doesNothing() {
        when(employeeRepo.findByUserEmail("ghost@company.com")).thenReturn(Optional.empty());

        courseNotificationService.markAllRead("ghost@company.com");

        verify(notifRepo, never()).markAllReadByEmployeeId(any());
    }
}

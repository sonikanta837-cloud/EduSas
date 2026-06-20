package com.emp.management.service;

import com.emp.management.dto.PipNotificationDTO;
import com.emp.management.entity.*;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.repository.PipNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PipNotificationServiceTest {

    @Mock private PipNotificationRepository repo;
    @Mock private EmployeeDetailsRepository employeeRepo;

    @InjectMocks private PipNotificationService pipNotificationService;

    private EmployeeDetails manager;
    private EmployeeDetails employee;
    private PerformanceImprovementPlan pip;

    @BeforeEach
    void setUp() {
        User mgrUser = User.builder().id(1L).email("mgr@company.com").role(Role.MANAGER).build();
        manager = EmployeeDetails.builder().id(1L).user(mgrUser)
                .firstName("Jane").lastName("Manager").build();
        mgrUser.setEmployeeDetails(manager);

        User empUser = User.builder().id(2L).email("emp@company.com").role(Role.EMPLOYEE).build();
        employee = EmployeeDetails.builder().id(2L).user(empUser)
                .firstName("John").lastName("Doe").manager(manager).build();
        empUser.setEmployeeDetails(employee);

        pip = PerformanceImprovementPlan.builder()
                .id(10L)
                .employee(employee)
                .createdBy(manager)
                .title("Performance Improvement")
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(3))
                .status(PipStatus.ACTIVE)
                .build();
    }

    // ── notifyComment ─────────────────────────────────────────────────────────

    @Test
    void notifyComment_senderIsThirdParty_notifiesEmployeeAndCreator() {
        // Admin comments — notify both employee and creator (manager)
        User adminUser = User.builder().id(3L).email("admin@company.com").role(Role.ADMIN).build();
        EmployeeDetails admin = EmployeeDetails.builder().id(3L).user(adminUser)
                .firstName("Admin").lastName("User").build();
        adminUser.setEmployeeDetails(admin);

        when(repo.save(any())).thenReturn(PipNotification.builder().build());

        pipNotificationService.notifyComment(pip, admin, "Great progress on the plan!");

        // Should save notifications for both employee (id=2) and creator/manager (id=1)
        verify(repo, times(2)).save(any(PipNotification.class));
    }

    @Test
    void notifyComment_senderIsEmployee_notifiesCreatorOnly() {
        // Employee comments on own PIP — only notify creator (manager)
        when(repo.save(any())).thenReturn(PipNotification.builder().build());

        pipNotificationService.notifyComment(pip, employee, "Working on it!");

        // Sender is employee, so skip employee notification; creator is manager (not sender) → 1 notification
        verify(repo, times(1)).save(any(PipNotification.class));
    }

    @Test
    void notifyComment_senderIsCreator_notifiesEmployeeOnly() {
        // Manager (creator) comments — notify employee only
        when(repo.save(any())).thenReturn(PipNotification.builder().build());

        pipNotificationService.notifyComment(pip, manager, "Please review the goals.");

        // Sender is creator (manager), so skip creator notification; notify employee → 1 notification
        verify(repo, times(1)).save(any(PipNotification.class));
    }

    @Test
    void notifyComment_longComment_truncatedInMessage() {
        String longComment = "A".repeat(100); // > 80 chars
        when(repo.save(any())).thenReturn(PipNotification.builder().build());

        User adminUser = User.builder().id(3L).email("admin@company.com").role(Role.ADMIN).build();
        EmployeeDetails admin = EmployeeDetails.builder().id(3L).user(adminUser)
                .firstName("Admin").lastName("User").build();

        pipNotificationService.notifyComment(pip, admin, longComment);

        // Verify save called with truncated message (77 chars + "...")
        verify(repo, atLeastOnce()).save(argThat(n -> {
            String msg = ((PipNotification) n).getMessage();
            return msg.contains("...");
        }));
    }

    // ── getUnread ─────────────────────────────────────────────────────────────

    @Test
    void getUnread_returnsUnreadNotifications() {
        PipNotification notification = PipNotification.builder()
                .id(1L).pip(pip).recipient(employee)
                .senderName("Jane Manager").message("New comment on your PIP")
                .read(false).build();

        when(employeeRepo.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(repo.findByRecipientIdAndReadFalseOrderByCreatedAtDesc(2L))
                .thenReturn(List.of(notification));

        List<PipNotificationDTO> result = pipNotificationService.getUnread("emp@company.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSenderName()).isEqualTo("Jane Manager");
        assertThat(result.get(0).isRead()).isFalse();
    }

    @Test
    void getUnread_nullUsername_returnsEmptyList() {
        List<PipNotificationDTO> result = pipNotificationService.getUnread(null);

        assertThat(result).isEmpty();
        verify(employeeRepo, never()).findByUserEmail(any());
    }

    @Test
    void getUnread_unknownUser_returnsEmptyList() {
        when(employeeRepo.findByUserEmail("ghost@company.com")).thenReturn(Optional.empty());

        List<PipNotificationDTO> result = pipNotificationService.getUnread("ghost@company.com");

        assertThat(result).isEmpty();
    }

    // ── getUnreadCount ────────────────────────────────────────────────────────

    @Test
    void getUnreadCount_returnsCount() {
        when(employeeRepo.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(repo.countByRecipientIdAndReadFalse(2L)).thenReturn(5L);

        long count = pipNotificationService.getUnreadCount("emp@company.com");

        assertThat(count).isEqualTo(5L);
    }

    @Test
    void getUnreadCount_nullUsername_returnsZero() {
        long count = pipNotificationService.getUnreadCount(null);

        assertThat(count).isEqualTo(0L);
    }

    @Test
    void getUnreadCount_unknownUser_returnsZero() {
        when(employeeRepo.findByUserEmail("ghost@company.com")).thenReturn(Optional.empty());

        long count = pipNotificationService.getUnreadCount("ghost@company.com");

        assertThat(count).isEqualTo(0L);
    }

    // ── markAsRead ────────────────────────────────────────────────────────────

    @Test
    void markAsRead_ownNotification_marksRead() {
        PipNotification notification = PipNotification.builder()
                .id(1L).pip(pip).recipient(employee)
                .senderName("Jane").message("Comment").read(false).build();

        when(employeeRepo.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(repo.findById(1L)).thenReturn(Optional.of(notification));
        when(repo.save(any())).thenReturn(notification);

        pipNotificationService.markAsRead(1L, "emp@company.com");

        assertThat(notification.isRead()).isTrue();
        verify(repo).save(notification);
    }

    @Test
    void markAsRead_notificationBelongsToDifferentEmployee_notMarked() {
        // Notification belongs to employee (id=2), but manager (id=1) tries to mark it
        PipNotification notification = PipNotification.builder()
                .id(1L).pip(pip).recipient(employee)
                .senderName("Jane").message("Comment").read(false).build();

        when(employeeRepo.findByUserEmail("mgr@company.com")).thenReturn(Optional.of(manager));
        when(repo.findById(1L)).thenReturn(Optional.of(notification));

        pipNotificationService.markAsRead(1L, "mgr@company.com");

        // IDOR guard — not saved
        assertThat(notification.isRead()).isFalse();
        verify(repo, never()).save(any());
    }

    @Test
    void markAsRead_nullUsername_doesNothing() {
        pipNotificationService.markAsRead(1L, null);

        verify(repo, never()).findById(any());
    }

    @Test
    void markAsRead_notificationNotFound_doesNothing() {
        when(employeeRepo.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(repo.findById(999L)).thenReturn(Optional.empty());

        // Should not throw
        pipNotificationService.markAsRead(999L, "emp@company.com");

        verify(repo, never()).save(any());
    }

    // ── markAllRead ───────────────────────────────────────────────────────────

    @Test
    void markAllRead_callsRepositoryMethod() {
        when(employeeRepo.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        doNothing().when(repo).markAllReadByRecipientId(2L);

        pipNotificationService.markAllRead("emp@company.com");

        verify(repo).markAllReadByRecipientId(2L);
    }

    @Test
    void markAllRead_nullUsername_doesNothing() {
        pipNotificationService.markAllRead(null);

        verify(repo, never()).markAllReadByRecipientId(any());
    }

    @Test
    void markAllRead_unknownUser_doesNothing() {
        when(employeeRepo.findByUserEmail("ghost@company.com")).thenReturn(Optional.empty());

        pipNotificationService.markAllRead("ghost@company.com");

        verify(repo, never()).markAllReadByRecipientId(any());
    }
}

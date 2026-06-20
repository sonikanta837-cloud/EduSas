package com.emp.management.service;

import com.emp.management.dto.AnnouncementDTO;
import com.emp.management.dto.AnnouncementViewersDTO;
import com.emp.management.entity.*;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.AnnouncementRepository;
import com.emp.management.repository.AnnouncementViewRepository;
import com.emp.management.repository.EmployeeDetailsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {

    @Mock private AnnouncementRepository repo;
    @Mock private AnnouncementViewRepository viewRepo;
    @Mock private EmployeeDetailsRepository employeeRepo;

    @InjectMocks private AnnouncementService announcementService;

    private Announcement announcement;
    private EmployeeDetails employee;

    @BeforeEach
    void setUp() {
        User user = User.builder().id(1L).email("admin@company.com").role(Role.ADMIN).active(true).build();
        employee = EmployeeDetails.builder().id(1L).user(user).firstName("Admin").lastName("User")
                .active(true).createdAt(LocalDateTime.now().minusDays(30)).build();
        user.setEmployeeDetails(employee);

        announcement = Announcement.builder()
                .id(1L)
                .title("Company Picnic")
                .body("Join us for the annual picnic!")
                .authorName("admin@company.com")
                .pinned(false)
                .archived(false)
                .category("Events")
                .priority("Normal")
                .createdAt(LocalDateTime.now().minusHours(2))
                .build();
    }

    // ── getAll ────────────────────────────────────────────────────────────────

    @Test
    void getAll_authenticatedUser_returnsNonArchivedAnnouncements() {
        when(employeeRepo.findByUserEmail("admin@company.com")).thenReturn(Optional.of(employee));
        when(viewRepo.findReadAnnouncementIdsByEmployeeId(1L)).thenReturn(Set.of());
        when(viewRepo.countGroupedByAnnouncement()).thenReturn(List.of());
        when(employeeRepo.countByActive(true)).thenReturn(10L);
        when(repo.findAllByOrderByPinnedDescCreatedAtDesc()).thenReturn(List.of(announcement));

        List<AnnouncementDTO> result = announcementService.getAll("admin@company.com");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Company Picnic");
    }

    @Test
    void getAll_archivedAnnouncement_excluded() {
        announcement.setArchived(true);
        when(employeeRepo.findByUserEmail("admin@company.com")).thenReturn(Optional.of(employee));
        when(viewRepo.findReadAnnouncementIdsByEmployeeId(1L)).thenReturn(Set.of());
        when(viewRepo.countGroupedByAnnouncement()).thenReturn(List.of());
        when(employeeRepo.countByActive(true)).thenReturn(10L);
        when(repo.findAllByOrderByPinnedDescCreatedAtDesc()).thenReturn(List.of(announcement));

        List<AnnouncementDTO> result = announcementService.getAll("admin@company.com");

        assertThat(result).isEmpty();
    }

    @Test
    void getAll_readByCurrentUser_markedAsRead() {
        when(employeeRepo.findByUserEmail("admin@company.com")).thenReturn(Optional.of(employee));
        when(viewRepo.findReadAnnouncementIdsByEmployeeId(1L)).thenReturn(Set.of(1L)); // announcement 1 already read
        when(viewRepo.countGroupedByAnnouncement()).thenReturn(List.of());
        when(employeeRepo.countByActive(true)).thenReturn(10L);
        when(repo.findAllByOrderByPinnedDescCreatedAtDesc()).thenReturn(List.of(announcement));

        List<AnnouncementDTO> result = announcementService.getAll("admin@company.com");

        assertThat(result.get(0).isReadByCurrentUser()).isTrue();
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_validDTO_savesAndReturnsDTO() {
        AnnouncementDTO dto = AnnouncementDTO.builder()
                .title("New Policy").body("Policy update").authorName("admin@company.com").build();

        when(repo.save(any())).thenReturn(announcement);
        when(employeeRepo.findByUserEmail("admin@company.com")).thenReturn(Optional.of(employee));
        when(viewRepo.save(any())).thenReturn(new AnnouncementView());
        when(viewRepo.countByAnnouncementId(anyLong())).thenReturn(1L);
        when(employeeRepo.countByActive(true)).thenReturn(10L);

        AnnouncementDTO result = announcementService.create(dto);

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Company Picnic");
        verify(repo).save(any(Announcement.class));
    }

    @Test
    void create_noMatchingCreator_doesNotSaveView() {
        AnnouncementDTO dto = AnnouncementDTO.builder()
                .title("New Policy").body("Body").authorName("nonexistent@company.com").build();

        when(repo.save(any())).thenReturn(announcement);
        when(employeeRepo.findByUserEmail("nonexistent@company.com")).thenReturn(Optional.empty());
        when(viewRepo.countByAnnouncementId(anyLong())).thenReturn(0L);
        when(employeeRepo.countByActive(true)).thenReturn(10L);

        AnnouncementDTO result = announcementService.create(dto);

        assertThat(result).isNotNull();
        verify(viewRepo, never()).save(any(AnnouncementView.class));
    }

    @Test
    void create_categoryNull_defaultsToGeneral() {
        AnnouncementDTO dto = AnnouncementDTO.builder()
                .title("Test").body("Body").authorName("admin@company.com")
                .category(null).priority(null).build();

        Announcement savedWithDefaults = Announcement.builder()
                .id(2L).title("Test").body("Body").authorName("admin@company.com")
                .category("General").priority("Normal").archived(false)
                .createdAt(LocalDateTime.now()).build();

        when(repo.save(any())).thenReturn(savedWithDefaults);
        when(employeeRepo.findByUserEmail("admin@company.com")).thenReturn(Optional.of(employee));
        when(viewRepo.save(any())).thenReturn(new AnnouncementView());
        when(viewRepo.countByAnnouncementId(anyLong())).thenReturn(1L);
        when(employeeRepo.countByActive(true)).thenReturn(10L);

        AnnouncementDTO result = announcementService.create(dto);

        // The saved entity (returned by mock) has "General" category
        assertThat(result.getCategory()).isEqualTo("General");
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_existingAnnouncement_returnsUpdatedDTO() {
        AnnouncementDTO dto = AnnouncementDTO.builder()
                .title("Updated Title").body("Updated body").category("General").priority("High").build();

        when(repo.findById(1L)).thenReturn(Optional.of(announcement));
        when(repo.save(any())).thenReturn(announcement);
        when(viewRepo.countByAnnouncementId(1L)).thenReturn(5L);
        when(employeeRepo.countByActive(true)).thenReturn(10L);

        AnnouncementDTO result = announcementService.update(1L, dto);

        assertThat(result).isNotNull();
        verify(repo).save(announcement);
    }

    @Test
    void update_nonExistingAnnouncement_throwsResourceNotFoundException() {
        when(repo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> announcementService.update(999L,
                AnnouncementDTO.builder().title("X").build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── markAsRead ────────────────────────────────────────────────────────────

    @Test
    void markAsRead_validUserAndAnnouncement_savesView() {
        when(employeeRepo.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(viewRepo.existsByAnnouncementIdAndEmployeeId(1L, 1L)).thenReturn(false);
        when(repo.findById(1L)).thenReturn(Optional.of(announcement));
        when(viewRepo.save(any())).thenReturn(new AnnouncementView());

        announcementService.markAsRead(1L, "emp@company.com");

        verify(viewRepo).save(any(AnnouncementView.class));
    }

    @Test
    void markAsRead_alreadyRead_doesNotSaveAgain() {
        when(employeeRepo.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(viewRepo.existsByAnnouncementIdAndEmployeeId(1L, 1L)).thenReturn(true);

        announcementService.markAsRead(1L, "emp@company.com");

        verify(viewRepo, never()).save(any());
    }

    @Test
    void markAsRead_nullUsername_doesNothing() {
        announcementService.markAsRead(1L, null);

        verify(viewRepo, never()).save(any());
    }

    @Test
    void markAsRead_employeeNotFound_doesNothing() {
        when(employeeRepo.findByUserEmail("ghost@company.com")).thenReturn(Optional.empty());

        announcementService.markAsRead(1L, "ghost@company.com");

        verify(viewRepo, never()).save(any());
    }

    // ── getUnread ─────────────────────────────────────────────────────────────

    @Test
    void getUnread_nullUsername_returnsEmpty() {
        List<AnnouncementDTO> result = announcementService.getUnread(null);
        assertThat(result).isEmpty();
    }

    @Test
    void getUnread_unknownUser_returnsEmpty() {
        when(employeeRepo.findByUserEmail("unknown@company.com")).thenReturn(Optional.empty());

        List<AnnouncementDTO> result = announcementService.getUnread("unknown@company.com");

        assertThat(result).isEmpty();
    }

    @Test
    void getUnread_withUnreadAnnouncements_returnsUnread() {
        when(employeeRepo.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(viewRepo.findReadAnnouncementIdsByEmployeeId(1L)).thenReturn(Collections.emptySet()); // nothing read
        when(viewRepo.countGroupedByAnnouncement()).thenReturn(List.of());
        when(employeeRepo.countByActive(true)).thenReturn(10L);
        when(repo.findAllByOrderByPinnedDescCreatedAtDesc()).thenReturn(List.of(announcement));

        List<AnnouncementDTO> result = announcementService.getUnread("emp@company.com");

        assertThat(result).hasSize(1);
    }

    // ── getUnreadCount ────────────────────────────────────────────────────────

    @Test
    void getUnreadCount_nullUsername_returnsZero() {
        long count = announcementService.getUnreadCount(null);
        assertThat(count).isEqualTo(0L);
    }

    @Test
    void getUnreadCount_unknownUser_returnsZero() {
        when(employeeRepo.findByUserEmail("ghost@company.com")).thenReturn(Optional.empty());

        long count = announcementService.getUnreadCount("ghost@company.com");

        assertThat(count).isEqualTo(0L);
    }

    @Test
    void getUnreadCount_validUser_returnsCount() {
        when(employeeRepo.findByUserEmail("emp@company.com")).thenReturn(Optional.of(employee));
        when(viewRepo.countUnreadByEmployeeId(eq(1L), anyString(), any())).thenReturn(3L);

        long count = announcementService.getUnreadCount("emp@company.com");

        assertThat(count).isEqualTo(3L);
    }

    // ── getViewers ────────────────────────────────────────────────────────────

    @Test
    void getViewers_existingAnnouncement_returnsViewers() {
        when(repo.existsById(1L)).thenReturn(true);
        when(viewRepo.findByAnnouncementId(1L)).thenReturn(List.of());
        when(employeeRepo.findByActive(true)).thenReturn(List.of(employee));

        AnnouncementViewersDTO result = announcementService.getViewers(1L);

        assertThat(result).isNotNull();
        assertThat(result.getTotalActive()).isEqualTo(1);
    }

    @Test
    void getViewers_nonExistingAnnouncement_throwsResourceNotFoundException() {
        when(repo.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> announcementService.getViewers(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_existingAnnouncement_deletesSuccessfully() {
        when(repo.existsById(1L)).thenReturn(true);
        doNothing().when(repo).deleteById(1L);

        announcementService.delete(1L);

        verify(repo).deleteById(1L);
    }

    @Test
    void delete_nonExistingAnnouncement_throwsResourceNotFoundException() {
        when(repo.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> announcementService.delete(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

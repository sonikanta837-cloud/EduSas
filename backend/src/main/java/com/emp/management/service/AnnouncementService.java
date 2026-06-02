package com.emp.management.service;

import com.emp.management.dto.AnnouncementDTO;
import com.emp.management.dto.AnnouncementViewersDTO;
import com.emp.management.entity.Announcement;
import com.emp.management.entity.AnnouncementView;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.AnnouncementRepository;
import com.emp.management.repository.AnnouncementViewRepository;
import com.emp.management.repository.EmployeeDetailsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository repo;
    private final AnnouncementViewRepository viewRepo;
    private final EmployeeDetailsRepository employeeRepo;

    public List<AnnouncementDTO> getAll(String username) {
        // Resolve current employee (null for unauthenticated)
        EmployeeDetails currentEmployee = resolveEmployee(username);
        Set<Long> readIds = currentEmployee != null
                ? viewRepo.findReadAnnouncementIdsByEmployeeId(currentEmployee.getId())
                : Collections.emptySet();

        // Build view-count map in one query
        Map<Long, Long> viewCountMap = new HashMap<>();
        for (Object[] row : viewRepo.countGroupedByAnnouncement()) {
            viewCountMap.put((Long) row[0], (Long) row[1]);
        }

        long totalActive = employeeRepo.countByActive(true);

        return repo.findAllByOrderByPinnedDescCreatedAtDesc()
                .stream()
                .filter(a -> !a.isArchived())
                .map(a -> toDTO(a,
                        readIds.contains(a.getId()) || username.equals(a.getAuthorName()),
                        viewCountMap.getOrDefault(a.getId(), 0L),
                        totalActive))
                .collect(Collectors.toList());
    }

    @Transactional
    public AnnouncementDTO create(AnnouncementDTO dto) {
        Announcement saved = repo.save(Announcement.builder()
                .title(dto.getTitle())
                .body(dto.getBody())
                .authorName(dto.getAuthorName())
                .pinned(dto.isPinned())
                .location(dto.getLocation())
                .category(dto.getCategory() != null ? dto.getCategory() : "General")
                .priority(dto.getPriority() != null ? dto.getPriority() : "Normal")
                .archived(false)
                .build());

        // Auto-mark as read for the creator so their own announcement
        // never appears as an unread notification for them.
        EmployeeDetails creator = resolveEmployee(dto.getAuthorName());
        if (creator != null) {
            viewRepo.save(AnnouncementView.builder()
                    .announcement(saved)
                    .employee(creator)
                    .build());
        }

        long viewedByCount = viewRepo.countByAnnouncementId(saved.getId());
        long totalActive   = employeeRepo.countByActive(true);
        return toDTO(saved, true, viewedByCount, totalActive);
    }

    @Transactional
    public AnnouncementDTO update(Long id, AnnouncementDTO dto) {
        Announcement a = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Announcement", id));
        if (dto.getTitle()    != null) a.setTitle(dto.getTitle());
        a.setBody(dto.getBody());
        a.setLocation(dto.getLocation());
        if (dto.getCategory() != null) a.setCategory(dto.getCategory());
        if (dto.getPriority() != null) a.setPriority(dto.getPriority());
        a.setPinned(dto.isPinned());
        a.setArchived(dto.isArchived());
        long viewedByCount = viewRepo.countByAnnouncementId(id);
        long totalActive   = employeeRepo.countByActive(true);
        return toDTO(repo.save(a), false, viewedByCount, totalActive);
    }

    @Transactional
    public void markAsRead(Long announcementId, String username) {
        if (username == null) return;
        EmployeeDetails employee = resolveEmployee(username);
        if (employee == null) return;
        if (viewRepo.existsByAnnouncementIdAndEmployeeId(announcementId, employee.getId())) return;
        Announcement ann = repo.findById(announcementId).orElse(null);
        if (ann == null) return;
        viewRepo.save(AnnouncementView.builder()
                .announcement(ann)
                .employee(employee)
                .build());
    }

    public List<AnnouncementDTO> getUnread(String username) {
        if (username == null) return Collections.emptyList();
        EmployeeDetails employee = resolveEmployee(username);
        if (employee == null) return Collections.emptyList();

        Set<Long> readIds = viewRepo.findReadAnnouncementIdsByEmployeeId(employee.getId());
        Map<Long, Long> viewCountMap = new HashMap<>();
        for (Object[] row : viewRepo.countGroupedByAnnouncement()) {
            viewCountMap.put((Long) row[0], (Long) row[1]);
        }
        long totalActive = employeeRepo.countByActive(true);

        return repo.findAllByOrderByPinnedDescCreatedAtDesc()
                .stream()
                .filter(a -> !a.isArchived()
                          && !readIds.contains(a.getId())
                          && !username.equals(a.getAuthorName()))
                .map(a -> toDTO(a, false, viewCountMap.getOrDefault(a.getId(), 0L), totalActive))
                .collect(Collectors.toList());
    }

    public long getUnreadCount(String username) {
        if (username == null) return 0;
        EmployeeDetails employee = resolveEmployee(username);
        if (employee == null) return 0;
        return viewRepo.countUnreadByEmployeeId(employee.getId(), username);
    }

    public AnnouncementViewersDTO getViewers(Long announcementId) {
        if (!repo.existsById(announcementId)) {
            throw new ResourceNotFoundException("Announcement", announcementId);
        }

        // All views for this announcement
        List<AnnouncementView> views = viewRepo.findByAnnouncementId(announcementId);
        Map<Long, AnnouncementView> viewedMap = views.stream()
                .collect(Collectors.toMap(v -> v.getEmployee().getId(), v -> v));

        // All active employees
        List<EmployeeDetails> activeEmployees = employeeRepo.findByActive(true);

        List<AnnouncementViewersDTO.ViewerEntry> viewed    = new ArrayList<>();
        List<AnnouncementViewersDTO.ViewerEntry> notViewed = new ArrayList<>();

        for (EmployeeDetails emp : activeEmployees) {
            AnnouncementViewersDTO.ViewerEntry entry = AnnouncementViewersDTO.ViewerEntry.builder()
                    .employeeId(emp.getId())
                    .employeeName(emp.getFullName())
                    .department(emp.getDepartment())
                    .viewedAt(viewedMap.containsKey(emp.getId()) ? viewedMap.get(emp.getId()).getViewedAt() : null)
                    .build();
            if (viewedMap.containsKey(emp.getId())) {
                viewed.add(entry);
            } else {
                notViewed.add(entry);
            }
        }

        viewed.sort(Comparator.comparing(AnnouncementViewersDTO.ViewerEntry::getViewedAt).reversed());

        return AnnouncementViewersDTO.builder()
                .viewed(viewed)
                .notViewed(notViewed)
                .totalViewed(viewed.size())
                .totalActive(activeEmployees.size())
                .build();
    }

    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) throw new ResourceNotFoundException("Announcement", id);
        repo.deleteById(id);
    }

    private EmployeeDetails resolveEmployee(String username) {
        if (username == null) return null;
        return employeeRepo.findByUserEmail(username).orElse(null);
    }

    private AnnouncementDTO toDTO(Announcement a, boolean readByCurrentUser, long viewedByCount, long totalActive) {
        return AnnouncementDTO.builder()
                .id(a.getId())
                .title(a.getTitle())
                .body(a.getBody())
                .authorName(a.getAuthorName())
                .pinned(a.isPinned())
                .location(a.getLocation())
                .category(a.getCategory() != null ? a.getCategory() : "General")
                .priority(a.getPriority() != null ? a.getPriority() : "Normal")
                .archived(a.isArchived())
                .readByCurrentUser(readByCurrentUser)
                .viewedByCount((int) viewedByCount)
                .totalActiveEmployees((int) totalActive)
                .createdAt(a.getCreatedAt())
                .build();
    }
}

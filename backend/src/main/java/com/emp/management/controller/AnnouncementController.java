package com.emp.management.controller;

import com.emp.management.dto.AnnouncementDTO;
import com.emp.management.dto.AnnouncementViewersDTO;
import com.emp.management.service.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService service;

    @GetMapping
    public List<AnnouncementDTO> getAll(@AuthenticationPrincipal UserDetails principal) {
        return service.getAll(principal != null ? principal.getUsername() : null);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','HR')")
    public ResponseEntity<AnnouncementDTO> create(
            @RequestBody AnnouncementDTO dto,
            @AuthenticationPrincipal UserDetails principal) {
        dto.setAuthorName(principal.getUsername());
        return ResponseEntity.ok(service.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','HR')")
    public ResponseEntity<AnnouncementDTO> update(
            @PathVariable Long id,
            @RequestBody AnnouncementDTO dto) {
        return ResponseEntity.ok(service.update(id, dto));
    }

    @PostMapping("/{id}/view")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal) {
        service.markAsRead(id, principal != null ? principal.getUsername() : null);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/unread")
    public List<AnnouncementDTO> getUnread(@AuthenticationPrincipal UserDetails principal) {
        return service.getUnread(principal != null ? principal.getUsername() : null);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @AuthenticationPrincipal UserDetails principal) {
        long count = service.getUnreadCount(principal != null ? principal.getUsername() : null);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @GetMapping("/{id}/viewers")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','HR')")
    public ResponseEntity<AnnouncementViewersDTO> getViewers(@PathVariable Long id) {
        return ResponseEntity.ok(service.getViewers(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','HR')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

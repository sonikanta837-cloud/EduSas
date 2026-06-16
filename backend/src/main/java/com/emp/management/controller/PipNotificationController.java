package com.emp.management.controller;

import com.emp.management.dto.PipNotificationDTO;
import com.emp.management.service.PipNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pip-notifications")
@RequiredArgsConstructor
public class PipNotificationController {

    private final PipNotificationService service;

    @GetMapping("/unread")
    public List<PipNotificationDTO> getUnread(@AuthenticationPrincipal UserDetails principal) {
        return service.getUnread(principal != null ? principal.getUsername() : null);
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(@AuthenticationPrincipal UserDetails principal) {
        long count = service.getUnreadCount(principal != null ? principal.getUsername() : null);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal) {
        service.markAsRead(id, principal != null ? principal.getUsername() : null);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal UserDetails principal) {
        service.markAllRead(principal != null ? principal.getUsername() : null);
        return ResponseEntity.noContent().build();
    }
}

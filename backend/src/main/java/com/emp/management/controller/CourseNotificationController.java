package com.emp.management.controller;

import com.emp.management.dto.CourseNotificationDTO;
import com.emp.management.service.CourseNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/course-notifications")
@RequiredArgsConstructor
public class CourseNotificationController {

    private final CourseNotificationService service;

    @GetMapping("/unread")
    public List<CourseNotificationDTO> getUnread(@AuthenticationPrincipal UserDetails principal) {
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

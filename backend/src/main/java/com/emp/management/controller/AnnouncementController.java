package com.emp.management.controller;

import com.emp.management.dto.AnnouncementDTO;
import com.emp.management.service.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService service;

    @GetMapping
    public List<AnnouncementDTO> getAll() {
        return service.getAll();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<AnnouncementDTO> create(
            @RequestBody AnnouncementDTO dto,
            @AuthenticationPrincipal UserDetails principal) {
        dto.setAuthorName(principal.getUsername());
        return ResponseEntity.ok(service.create(dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

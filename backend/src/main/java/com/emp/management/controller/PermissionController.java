package com.emp.management.controller;

import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.User;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final UserRepository userRepository;

    /** List all active users with their current allowedModules */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public ResponseEntity<List<Map<String, Object>>> getAllUserPermissions() {
        List<Map<String, Object>> result = userRepository.findAll().stream()
            .filter(User::isActive)
            .map(u -> {
                EmployeeDetails emp = u.getEmployeeDetails();
                String name = emp != null ? emp.getFullName() : u.getEmail();
                return Map.<String, Object>of(
                    "userId", u.getId(),
                    "name", name,
                    "email", u.getEmail(),
                    "role", u.getRole().name(),
                    "allowedModules", u.getAllowedModules() != null ? u.getAllowedModules() : ""
                );
            })
            .toList();
        return ResponseEntity.ok(result);
    }

    /** Update allowed modules for a user. Empty/null body clears custom permissions (reverts to role defaults). */
    @PutMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public ResponseEntity<Void> updatePermissions(
            @PathVariable Long userId,
            @RequestBody Map<String, String> body) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String modules = body.get("allowedModules");
        user.setAllowedModules((modules != null && !modules.isBlank()) ? modules : null);
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }
}

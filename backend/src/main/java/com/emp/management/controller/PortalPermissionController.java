package com.emp.management.controller;

import com.emp.management.entity.Role;
import com.emp.management.entity.User;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.UserRepository;
import com.emp.management.service.PortalPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/portal-permissions")
@RequiredArgsConstructor
public class PortalPermissionController {

    private final PortalPermissionService portalPermissionService;
    private final UserRepository userRepository;

    private User currentUser(UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername())
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    /**
     * Portals the logged-in user currently has access to — their role's grants plus any portal
     * they need for an active assignment (e.g. an interview assigned to them). Fetched on login
     * and on refresh.
     */
    @GetMapping("/me")
    public ResponseEntity<List<String>> getMine(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(portalPermissionService.getEffectiveAllowedPortals(currentUser(principal)));
    }

    /** Server-authoritative check — 403s unless the role grants this portal or the user has an active assignment requiring it. */
    @GetMapping("/check/{portalId}")
    public ResponseEntity<Void> check(
            @PathVariable String portalId,
            @AuthenticationPrincipal UserDetails principal) {
        if (!portalPermissionService.isPortalAllowedForUser(currentUser(principal), portalId)) {
            throw new AccessDeniedException("Access to portal '" + portalId + "' is not permitted for your role");
        }
        return ResponseEntity.ok().build();
    }

    /** Full role → allowed-portal-ids map, for the Roles & Permissions screen. */
    @GetMapping("/roles")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public ResponseEntity<Map<String, List<String>>> getAllRolePermissions() {
        return ResponseEntity.ok(portalPermissionService.getAllRolePermissions());
    }

    /** Replace the full set of portals allowed for a role. Body: { "portalIds": ["hr", "training"] } */
    @PutMapping("/roles/{role}")
    @PreAuthorize("hasAnyRole('ADMIN', 'DIRECTOR')")
    public ResponseEntity<Void> updateRolePermissions(
            @PathVariable Role role,
            @RequestBody Map<String, List<String>> body) {
        portalPermissionService.updateRolePermissions(role, body.getOrDefault("portalIds", List.of()));
        return ResponseEntity.ok().build();
    }
}

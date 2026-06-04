package com.emp.management.controller;

import com.emp.management.dto.EmployeeDTO;
import com.emp.management.dto.RegisterRequest;
import com.emp.management.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'ASSISTANT_MANAGER', 'HR', 'EMPLOYEE')")
    public ResponseEntity<List<EmployeeDTO>> getAllEmployees(
            @RequestParam(required = false) String search,
            Authentication authentication) {
        if (search != null && !search.isBlank()) {
            return ResponseEntity.ok(employeeService.searchEmployees(search, authentication.getName()));
        }
        return ResponseEntity.ok(employeeService.getAllEmployees(authentication.getName()));
    }

    @GetMapping("/hr-users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<EmployeeDTO>> getHrUsers() {
        return ResponseEntity.ok(employeeService.getHrEmployees());
    }

    @PatchMapping("/{id}/assign-hr")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmployeeDTO> assignHr(@PathVariable Long id,
                                                 @RequestParam(required = false) Long hrId) {
        return ResponseEntity.ok(employeeService.assignHr(id, hrId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmployeeDTO> getEmployee(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(employeeService.getEmployeeById(id, authentication.getName()));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<EmployeeDTO> getEmployeeByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(employeeService.getEmployeeByUserId(userId));
    }

    @GetMapping("/manager/{managerId}/team")
    public ResponseEntity<List<EmployeeDTO>> getTeam(@PathVariable Long managerId) {
        return ResponseEntity.ok(employeeService.getEmployeesByManager(managerId));
    }

    @GetMapping("/ex")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<EmployeeDTO>> getExEmployees() {
        return ResponseEntity.ok(employeeService.getExEmployees());
    }

    @GetMapping("/org-chart")
    public ResponseEntity<List<EmployeeDTO>> getOrgChart() {
        return ResponseEntity.ok(employeeService.getOrgChart());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmployeeDTO> createEmployee(@Valid @RequestBody RegisterRequest request,
                                                       Authentication authentication) {
        return ResponseEntity.ok(employeeService.createEmployee(request, authentication.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER', 'ASSISTANT_MANAGER', 'EMPLOYEE')")
    public ResponseEntity<EmployeeDTO> updateEmployee(@PathVariable Long id,
                                                       @RequestBody EmployeeDTO dto,
                                                       Authentication authentication) {
        return ResponseEntity.ok(employeeService.updateEmployee(id, dto, authentication.getName()));
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> toggleStatus(@PathVariable Long id) {
        employeeService.toggleEmployeeStatus(id);
        return ResponseEntity.ok("Status updated");
    }

    @PatchMapping("/{id}/clear-manager")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> clearManager(@PathVariable Long id) {
        employeeService.clearManager(id);
        return ResponseEntity.ok("Manager cleared");
    }

    @GetMapping("/locations")
    public ResponseEntity<List<String>> getLocations() {
        return ResponseEntity.ok(employeeService.getDistinctLocations());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deleteEmployee(@PathVariable Long id) {
        employeeService.deleteEmployee(id);
        return ResponseEntity.ok("Employee deactivated");
    }
}

package com.emp.management.controller;

import com.emp.management.dto.MasterValueCreateRequest;
import com.emp.management.dto.MasterValueUpdateRequest;
import com.emp.management.dto.TimesheetMasterValueDTO;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.MasterDataType;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.service.TimesheetMasterValueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/timesheet-master")
@RequiredArgsConstructor
public class TimesheetMasterController {

    private final TimesheetMasterValueService masterValueService;
    private final EmployeeDetailsRepository employeeDetailsRepository;

    @GetMapping("/{type}")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','HR','MANAGER','ASSISTANT_MANAGER','EMPLOYEE')")
    public ResponseEntity<List<TimesheetMasterValueDTO>> list(
            @PathVariable MasterDataType type,
            @RequestParam(defaultValue = "true") boolean activeOnly) {
        return ResponseEntity.ok(masterValueService.list(type, activeOnly));
    }

    @PostMapping("/{type}")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','HR','MANAGER','ASSISTANT_MANAGER','EMPLOYEE')")
    public ResponseEntity<TimesheetMasterValueDTO> add(
            @PathVariable MasterDataType type,
            @RequestBody MasterValueCreateRequest req,
            Authentication authentication) {
        String requesterName = employeeDetailsRepository.findByUserEmail(authentication.getName())
                .map(EmployeeDetails::getFullName)
                .orElse(authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(masterValueService.add(type, req, requesterName));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','HR')")
    public ResponseEntity<TimesheetMasterValueDTO> update(
            @PathVariable Long id,
            @RequestBody MasterValueUpdateRequest req) {
        return ResponseEntity.ok(masterValueService.update(id, req));
    }
}

package com.emp.management.service;

import com.emp.management.dto.EmployeeDTO;
import com.emp.management.dto.RegisterRequest;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.Role;
import com.emp.management.entity.User;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.time.LocalDate;
import java.time.Period;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeDetailsRepository employeeDetailsRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<EmployeeDTO> getAllEmployees(String requesterEmail) {
        EmployeeDetails requester = employeeDetailsRepository.findByUserEmail(requesterEmail).orElse(null);
        if (requester != null && "HR".equals(requester.getUser().getRole().name())) {
            return employeeDetailsRepository.findByAssignedHrIdAndActiveTrue(requester.getId())
                    .stream().filter(e -> !isDirector(e)).map(this::toDTO).collect(Collectors.toList());
        }
        return employeeDetailsRepository.findByActive(true).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EmployeeDTO> getAllEmployees() {
        return employeeDetailsRepository.findByActive(true).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EmployeeDTO> getActiveEmployees() {
        return employeeDetailsRepository.findByActive(true).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EmployeeDTO> getExEmployees() {
        return employeeDetailsRepository.findByActive(false).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EmployeeDTO getEmployeeById(Long id) {
        return toDTO(findEmployee(id));
    }

    @Transactional(readOnly = true)
    public EmployeeDTO getEmployeeById(Long id, String requesterEmail) {
        EmployeeDetails target = findEmployee(id);
        EmployeeDetails requester = employeeDetailsRepository.findByUserEmail(requesterEmail).orElse(null);

        if (requester != null) {
            String role = requester.getUser().getRole().name();

            if (!target.isActive() && !"ADMIN".equals(role)) {
                throw new AccessDeniedException("Only admins can view inactive employee profiles");
            }

            boolean isAdmin       = "ADMIN".equals(role);
            boolean isHrRole      = "HR".equals(role);
            boolean isManagerRole = "MANAGER".equals(role) || "ASSISTANT_MANAGER".equals(role);
            boolean isOwnProfile  = requester.getId().equals(id);
            boolean isAssignedHr  = isHrRole && target.getAssignedHr() != null
                    && target.getAssignedHr().getId().equals(requester.getId());
            boolean isDirectReport = isManagerRole
                    && target.getManager() != null
                    && target.getManager().getId().equals(requester.getId());

            if (isHrRole && isDirector(target) && !isOwnProfile) {
                throw new AccessDeniedException("HR does not have permission to view director profiles");
            }

            if (!isAdmin && !isAssignedHr && !isOwnProfile && !isDirectReport) {
                throw new AccessDeniedException("You do not have permission to view this employee's profile");
            }
        }

        return toDTO(target);
    }

    public EmployeeDTO getEmployeeByUserId(Long userId) {
        EmployeeDetails emp = employeeDetailsRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found for user: " + userId));
        return toDTO(emp);
    }

    @Transactional(readOnly = true)
    public List<EmployeeDTO> getEmployeesByManager(Long managerId) {
        return employeeDetailsRepository.findByManagerIdAndActiveTrue(managerId).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EmployeeDTO> searchEmployees(String query, String requesterEmail) {
        EmployeeDetails requester = employeeDetailsRepository.findByUserEmail(requesterEmail).orElse(null);
        if (requester != null && "HR".equals(requester.getUser().getRole().name())) {
            return employeeDetailsRepository.searchActiveEmployeesByHr(query, requester.getId())
                    .stream().filter(e -> !isDirector(e)).map(this::toDTO).collect(Collectors.toList());
        }
        return employeeDetailsRepository.searchActiveEmployees(query).stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public EmployeeDTO createEmployee(RegisterRequest request, String requesterEmail) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered: " + request.getEmail());
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .active(true)
                .build();
        user = userRepository.save(user);

        String empCode = (request.getEmployeeCode() != null && !request.getEmployeeCode().isBlank())
                ? request.getEmployeeCode()
                : generateNextEmployeeCode();

        EmployeeDetails.EmployeeDetailsBuilder empBuilder = EmployeeDetails.builder()
                .user(user)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .employeeCode(empCode)
                .phone(request.getPhone())
                .personalEmail(request.getPersonalEmail())
                .workEmail(request.getWorkEmail())
                .department(request.getDepartment())
                .position(request.getPosition())
                .employmentType(request.getEmploymentType())
                .sourceOfHire(request.getSourceOfHire())
                .hireDate(request.getHireDate() != null ? request.getHireDate() : LocalDate.now())
                .dateOfExit(request.getDateOfExit())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .maritalStatus(request.getMaritalStatus())
                .aadharNumber(request.getAadharNumber())
                .panNumber(request.getPanNumber())
                .uanNumber(request.getUanNumber())
                .address(request.getAddress())
                .presentAddress(request.getPresentAddress())
                .permanentAddress(request.getPermanentAddress())
                .seatingLocation(request.getSeatingLocation())
                .currentExperience(request.getCurrentExperience())
                .totalExperience(request.getTotalExperience())
                .skills(request.getSkills())
                .experience(request.getExperience())
                .photoUrl(request.getPhotoUrl())
                .addedBy(requesterEmail)
                .active(true);

        if (request.getManagerId() != null) {
            EmployeeDetails manager = findEmployee(request.getManagerId());
            empBuilder.manager(manager);
        }

        // Auto-assign the least-loaded active HR
        EmployeeDetails defaultHr = pickLeastLoadedHr();
        if (defaultHr != null) {
            empBuilder.assignedHr(defaultHr);
        }

        return toDTO(employeeDetailsRepository.save(empBuilder.build()));
    }

    @Transactional
    public EmployeeDTO updateEmployee(Long id, EmployeeDTO dto, String requesterEmail) {
        EmployeeDetails emp = findEmployee(id);
        EmployeeDetails requester = employeeDetailsRepository.findByUserEmail(requesterEmail).orElse(null);

        if (requester != null) {
            String requesterRole = requester.getUser().getRole().name();
            boolean isAdmin       = "ADMIN".equals(requesterRole);
            boolean isHrRequester = "HR".equals(requesterRole);
            boolean isAssignedHr  = isHrRequester && emp.getAssignedHr() != null
                    && emp.getAssignedHr().getId().equals(requester.getId());
            boolean isManagerRole = "MANAGER".equals(requesterRole) || "ASSISTANT_MANAGER".equals(requesterRole);
            boolean isDirectReport = isManagerRole
                    && emp.getManager() != null
                    && emp.getManager().getId().equals(requester.getId());
            boolean isSelf = emp.getId().equals(requester.getId());

            if (isHrRequester && isDirector(emp) && !isSelf) {
                throw new AccessDeniedException("HR does not have permission to edit director profiles");
            }

            if (!isAdmin && !isAssignedHr && !isDirectReport && !isSelf) {
                throw new AccessDeniedException("You do not have permission to update this employee");
            }
        }

        User user = emp.getUser();

        // Update User-level fields (role changes: admin can set any role; HR can set any role except ADMIN)
        if (dto.getRole() != null) {
            String requesterRole = requester != null ? requester.getUser().getRole().name() : "ADMIN";
            boolean isAdminReq = "ADMIN".equals(requesterRole);
            boolean isHrReq    = "HR".equals(requesterRole);
            if (isAdminReq) {
                user.setRole(Role.valueOf(dto.getRole()));
            } else if (isHrReq) {
                if ("ADMIN".equals(dto.getRole()))
                    throw new BadRequestException("HR cannot assign the Admin role");
                if ("ADMIN".equals(user.getRole().name()))
                    throw new BadRequestException("HR cannot change the role of a director");
                user.setRole(Role.valueOf(dto.getRole()));
            }
        }
        if (dto.getEmail() != null && !dto.getEmail().isBlank()
                && !dto.getEmail().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmail(dto.getEmail())) {
                throw new BadRequestException("Email already in use: " + dto.getEmail());
            }
            user.setEmail(dto.getEmail());
        }
        if (dto.getWorkEmail() != null && !dto.getWorkEmail().isBlank()
                && !dto.getWorkEmail().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmail(dto.getWorkEmail())) {
                throw new BadRequestException("Work email is already in use: " + dto.getWorkEmail());
            }
            user.setEmail(dto.getWorkEmail());
        }
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        userRepository.save(user);

        // Update EmployeeDetails fields
        emp.setFirstName(dto.getFirstName());
        emp.setLastName(dto.getLastName());
        emp.setEmployeeCode(dto.getEmployeeCode());
        emp.setPhone(dto.getPhone());
        emp.setPersonalEmail(dto.getPersonalEmail());
        emp.setWorkEmail(dto.getWorkEmail());
        emp.setDepartment(dto.getDepartment());
        emp.setPosition(dto.getPosition());
        emp.setEmploymentType(dto.getEmploymentType());
        emp.setSourceOfHire(dto.getSourceOfHire());
        if (dto.getHireDate() != null) emp.setHireDate(dto.getHireDate());
        emp.setDateOfExit(dto.getDateOfExit());
        emp.setDateOfBirth(dto.getDateOfBirth());
        emp.setGender(dto.getGender());
        emp.setMaritalStatus(dto.getMaritalStatus());
        emp.setAadharNumber(dto.getAadharNumber());
        emp.setPanNumber(dto.getPanNumber());
        emp.setUanNumber(dto.getUanNumber());
        emp.setAddress(dto.getAddress());
        emp.setPresentAddress(dto.getPresentAddress());
        emp.setPermanentAddress(dto.getPermanentAddress());
        emp.setSeatingLocation(dto.getSeatingLocation());
        emp.setCurrentExperience(dto.getCurrentExperience());
        emp.setTotalExperience(dto.getTotalExperience());
        emp.setSkills(dto.getSkills());
        emp.setExperience(dto.getExperience());
        emp.setSalary(dto.getSalary());
        emp.setPhotoUrl(dto.getPhotoUrl());
        emp.setModifiedBy(requesterEmail);

        if (dto.getManagerId() != null) {
            if (dto.getManagerId().equals(id)) {
                throw new BadRequestException("Employee cannot be their own manager");
            }
            // Walk up the proposed manager's chain — if we reach 'id' it's a cycle
            Long cursor = dto.getManagerId();
            while (cursor != null) {
                if (cursor.equals(id)) {
                    throw new BadRequestException("This assignment creates a circular reporting chain");
                }
                EmployeeDetails m = employeeDetailsRepository.findById(cursor).orElse(null);
                cursor = (m != null && m.getManager() != null) ? m.getManager().getId() : null;
            }
            emp.setManager(findEmployee(dto.getManagerId()));
        } else {
            emp.setManager(null);
        }

        // Admin and HR can assign / clear assigned HR via the edit form
        String requesterRole = requester != null ? requester.getUser().getRole().name() : "ADMIN";
        if ("ADMIN".equals(requesterRole) || "HR".equals(requesterRole)) {
            if (dto.getAssignedHrId() != null) {
                EmployeeDetails hr = findEmployee(dto.getAssignedHrId());
                if (!"HR".equals(hr.getUser().getRole().name())) {
                    throw new BadRequestException("Selected employee does not have the HR role");
                }
                emp.setAssignedHr(hr);
            } else {
                emp.setAssignedHr(null);
            }
        }

        return toDTO(employeeDetailsRepository.save(emp));
    }

    @Transactional
    public void clearManager(Long id) {
        EmployeeDetails emp = findEmployee(id);
        emp.setManager(null);
        employeeDetailsRepository.save(emp);
    }

    @Transactional
    public EmployeeDTO assignHr(Long employeeId, Long hrId) {
        EmployeeDetails emp = findEmployee(employeeId);
        if (hrId == null) {
            emp.setAssignedHr(null);
        } else {
            EmployeeDetails hr = findEmployee(hrId);
            if (!"HR".equals(hr.getUser().getRole().name())) {
                throw new BadRequestException("Selected employee does not have the HR role");
            }
            emp.setAssignedHr(hr);
        }
        return toDTO(employeeDetailsRepository.save(emp));
    }

    @Transactional(readOnly = true)
    public List<EmployeeDTO> getHrEmployees() {
        return employeeDetailsRepository.findActiveHrEmployees()
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public void toggleEmployeeStatus(Long id) {
        EmployeeDetails emp = findEmployee(id);
        boolean wasActive = emp.isActive();
        emp.setActive(!wasActive);
        emp.getUser().setActive(!wasActive);
        if (wasActive) {
            reassignSubordinates(emp);
        }
        employeeDetailsRepository.save(emp);
    }

    @Transactional
    public void deleteEmployee(Long id) {
        EmployeeDetails emp = findEmployee(id);
        emp.setActive(false);
        emp.getUser().setActive(false);
        reassignSubordinates(emp);
        employeeDetailsRepository.save(emp);
    }

    private void reassignSubordinates(EmployeeDetails deactivated) {
        List<EmployeeDetails> subordinates = employeeDetailsRepository.findByManagerId(deactivated.getId());
        if (subordinates.isEmpty()) return;

        // Prefer the deactivated manager's own reporting manager (if active)
        EmployeeDetails reportingManager = deactivated.getManager();
        if (reportingManager != null && !reportingManager.isActive()) {
            reportingManager = null;
        }

        // Fall back to first active ADMIN when there is no active reporting manager
        EmployeeDetails newManager = reportingManager;
        if (newManager == null) {
            newManager = employeeDetailsRepository.findActiveAdmins().stream()
                    .filter(a -> !a.getId().equals(deactivated.getId()))
                    .findFirst()
                    .orElse(null);
        }

        for (EmployeeDetails sub : subordinates) {
            sub.setManager(newManager);
            employeeDetailsRepository.save(sub);
        }
    }

    @Transactional(readOnly = true)
    public List<EmployeeDTO> getOrgChart() {
        return employeeDetailsRepository.findByManagerIsNullAndActiveTrue().stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    // ── Employee code auto-generation ─────────────────────────────────────────

    private synchronized String generateNextEmployeeCode() {
        List<String> existing = employeeDetailsRepository.findAllEmployeeCodes();
        int max = 10000;
        for (String code : existing) {
            try {
                int val = Integer.parseInt(code.trim());
                if (val > max) max = val;
            } catch (NumberFormatException ignored) {}
        }
        return String.valueOf(max + 1);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfillEmployeeCodes() {
        List<EmployeeDetails> missing = employeeDetailsRepository.findByEmployeeCodeMissing();
        if (missing.isEmpty()) return;

        List<String> existing = employeeDetailsRepository.findAllEmployeeCodes();
        int max = 10000;
        for (String code : existing) {
            try {
                int val = Integer.parseInt(code.trim());
                if (val > max) max = val;
            } catch (NumberFormatException ignored) {}
        }

        // Sort by DB id so codes are assigned in creation order
        missing.sort(java.util.Comparator.comparingLong(EmployeeDetails::getId));
        for (EmployeeDetails emp : missing) {
            emp.setEmployeeCode(String.valueOf(++max));
            employeeDetailsRepository.save(emp);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfillHrAssignments() {
        List<EmployeeDetails> hrs = employeeDetailsRepository.findActiveHrEmployees();

        // Clear stale assignments where the stored person is no longer an active HR
        int cleared = 0;
        for (EmployeeDetails emp : employeeDetailsRepository.findByActive(true)) {
            if (emp.getAssignedHr() != null && !isActiveHr(emp.getAssignedHr())) {
                emp.setAssignedHr(null);
                employeeDetailsRepository.save(emp);
                cleared++;
            }
        }
        if (cleared > 0) log.info("Cleared {} stale HR assignments", cleared);

        if (hrs.isEmpty()) return;

        // Assign HR to employees who have none
        List<EmployeeDetails> unassigned = employeeDetailsRepository.findByAssignedHrIsNullAndActiveTrue();
        unassigned.sort(Comparator.comparingLong(EmployeeDetails::getId));
        int count = 0;
        for (EmployeeDetails emp : unassigned) {
            if ("HR".equals(emp.getUser().getRole().name())) continue;
            EmployeeDetails hr = pickLeastLoadedHr();
            if (hr == null) break;
            emp.setAssignedHr(hr);
            employeeDetailsRepository.save(emp);
            count++;
        }
        if (count > 0) log.info("Backfilled HR assignments for {} employees", count);
    }

    private static boolean isDirector(EmployeeDetails e) {
        return e != null && e.getUser() != null && "ADMIN".equals(e.getUser().getRole().name());
    }

    private static boolean isActiveHr(EmployeeDetails e) {
        return e != null && e.isActive()
                && e.getUser() != null
                && "HR".equals(e.getUser().getRole().name());
    }

    private EmployeeDetails pickLeastLoadedHr() {
        List<EmployeeDetails> hrs = employeeDetailsRepository.findActiveHrEmployees();
        if (hrs.isEmpty()) return null;
        return hrs.stream()
                .min(Comparator.comparingLong(hr ->
                        employeeDetailsRepository.countActiveAssignmentsByHrId(hr.getId())))
                .orElse(null);
    }

    private EmployeeDetails findEmployee(Long id) {
        return employeeDetailsRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", id));
    }

    public EmployeeDTO toDTO(EmployeeDetails emp) {
        Integer age = null;
        if (emp.getDateOfBirth() != null) {
            age = Period.between(emp.getDateOfBirth(), LocalDate.now()).getYears();
        }
        return EmployeeDTO.builder()
                .id(emp.getId())
                .userId(emp.getUser().getId())
                .email(emp.getUser().getEmail())
                .firstName(emp.getFirstName())
                .lastName(emp.getLastName())
                .fullName(emp.getFullName())
                .employeeCode(emp.getEmployeeCode())
                .phone(emp.getPhone())
                .personalEmail(emp.getPersonalEmail())
                .workEmail(emp.getWorkEmail())
                .department(emp.getDepartment())
                .position(emp.getPosition())
                .employmentType(emp.getEmploymentType())
                .sourceOfHire(emp.getSourceOfHire())
                .hireDate(emp.getHireDate())
                .dateOfExit(emp.getDateOfExit())
                .dateOfBirth(emp.getDateOfBirth())
                .age(age)
                .gender(emp.getGender())
                .maritalStatus(emp.getMaritalStatus())
                .aadharNumber(emp.getAadharNumber())
                .panNumber(emp.getPanNumber())
                .uanNumber(emp.getUanNumber())
                .address(emp.getAddress())
                .presentAddress(emp.getPresentAddress())
                .permanentAddress(emp.getPermanentAddress())
                .seatingLocation(emp.getSeatingLocation())
                .currentExperience(emp.getCurrentExperience())
                .totalExperience(emp.getTotalExperience())
                .skills(emp.getSkills())
                .experience(emp.getExperience())
                .salary(emp.getSalary())
                .profileImageUrl(emp.getProfileImageUrl())
                .photoUrl(emp.getPhotoUrl())
                .active(emp.isActive())
                .role(emp.getUser().getRole().name())
                .managerId(emp.getManager() != null && emp.getManager().isActive() ? emp.getManager().getId() : null)
                .managerName(emp.getManager() != null && emp.getManager().isActive() ? emp.getManager().getFullName() : null)
                .subordinateCount(emp.getSubordinates() != null
                        ? (int) emp.getSubordinates().stream().filter(EmployeeDetails::isActive).count() : 0)
                .assignedHrId(isActiveHr(emp.getAssignedHr()) ? emp.getAssignedHr().getId() : null)
                .assignedHrName(isActiveHr(emp.getAssignedHr()) ? emp.getAssignedHr().getFullName() : null)
                .addedBy(emp.getAddedBy())
                .modifiedBy(emp.getModifiedBy())
                .createdAt(emp.getCreatedAt())
                .updatedAt(emp.getUpdatedAt())
                .build();
    }

    public List<String> getDistinctLocations() {
        return employeeDetailsRepository.findDistinctLocations();
    }
}

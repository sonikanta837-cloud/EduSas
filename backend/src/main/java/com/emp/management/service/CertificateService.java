package com.emp.management.service;

import com.emp.management.dto.CertificateDTO;
import com.emp.management.entity.Certificate;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.CertificateRepository;
import com.emp.management.repository.EmployeeDetailsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final EmployeeDetailsRepository employeeDetailsRepository;

    @Transactional(readOnly = true)
    public List<CertificateDTO> getMyCertificates(String userEmail) {
        var employee = employeeDetailsRepository.findByUserEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found for email: " + userEmail));
        return certificateRepository.findByEmployeeId(employee.getId()).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CertificateDTO> getAllCertificates() {
        return certificateRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    private CertificateDTO toDTO(Certificate c) {
        return CertificateDTO.builder()
                .id(c.getId())
                .courseTitle(c.getCourse() != null ? c.getCourse().getTitle() : "—")
                .certificateNumber(c.getCertificateNumber())
                .issuedAt(c.getIssuedAt())
                .employeeName(c.getEmployee() != null
                        ? c.getEmployee().getFirstName() + " " + c.getEmployee().getLastName()
                        : "—")
                .employeeId(c.getEmployee() != null ? c.getEmployee().getId() : null)
                .score(c.getScore())
                .build();
    }
}

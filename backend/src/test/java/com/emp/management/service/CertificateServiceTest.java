package com.emp.management.service;

import com.emp.management.dto.CertificateDTO;
import com.emp.management.entity.Certificate;
import com.emp.management.entity.Course;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.Role;
import com.emp.management.entity.User;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.CertificateRepository;
import com.emp.management.repository.EmployeeDetailsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificateServiceTest {

    @Mock
    private CertificateRepository certificateRepository;

    @Mock
    private EmployeeDetailsRepository employeeDetailsRepository;

    @InjectMocks
    private CertificateService certificateService;

    private EmployeeDetails employee;
    private Course course;
    private Certificate certificate;
    private final String userEmail = "john@company.com";

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id(1L)
                .email(userEmail)
                .role(Role.EMPLOYEE)
                .active(true)
                .build();

        employee = EmployeeDetails.builder()
                .id(1L)
                .user(user)
                .firstName("John")
                .lastName("Doe")
                .employeeCode("10001")
                .active(true)
                .build();

        course = Course.builder()
                .id(1L)
                .title("Spring Boot Fundamentals")
                .active(true)
                .build();

        certificate = Certificate.builder()
                .id(1L)
                .employee(employee)
                .course(course)
                .certificateNumber("CERT-2024-001")
                .score(85)
                .issuedAt(LocalDateTime.of(2024, 6, 15, 10, 0))
                .build();
    }

    // ── getMyCertificates ─────────────────────────────────────────────────────

    @Test
    void getMyCertificates_knownEmail_returnsListOfDTOs() {
        when(employeeDetailsRepository.findByUserEmail(userEmail))
                .thenReturn(Optional.of(employee));
        when(certificateRepository.findByEmployeeId(1L))
                .thenReturn(List.of(certificate));

        List<CertificateDTO> result = certificateService.getMyCertificates(userEmail);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCertificateNumber()).isEqualTo("CERT-2024-001");
        assertThat(result.get(0).getCourseTitle()).isEqualTo("Spring Boot Fundamentals");
        assertThat(result.get(0).getEmployeeName()).isEqualTo("John Doe");
        assertThat(result.get(0).getScore()).isEqualTo(85);
        assertThat(result.get(0).getEmployeeId()).isEqualTo(1L);
    }

    @Test
    void getMyCertificates_unknownEmail_throwsResourceNotFoundException() {
        when(employeeDetailsRepository.findByUserEmail("unknown@company.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> certificateService.getMyCertificates("unknown@company.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("unknown@company.com");
    }

    @Test
    void getMyCertificates_noOwnedCertificates_returnsEmptyList() {
        when(employeeDetailsRepository.findByUserEmail(userEmail))
                .thenReturn(Optional.of(employee));
        when(certificateRepository.findByEmployeeId(1L))
                .thenReturn(List.of());

        List<CertificateDTO> result = certificateService.getMyCertificates(userEmail);

        assertThat(result).isEmpty();
    }

    @Test
    void getMyCertificates_multipleCertificates_returnsAll() {
        Certificate cert2 = Certificate.builder()
                .id(2L)
                .employee(employee)
                .course(Course.builder().id(2L).title("Advanced Java").build())
                .certificateNumber("CERT-2024-002")
                .score(92)
                .issuedAt(LocalDateTime.now())
                .build();

        when(employeeDetailsRepository.findByUserEmail(userEmail))
                .thenReturn(Optional.of(employee));
        when(certificateRepository.findByEmployeeId(1L))
                .thenReturn(List.of(certificate, cert2));

        List<CertificateDTO> result = certificateService.getMyCertificates(userEmail);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCertificateNumber()).isEqualTo("CERT-2024-001");
        assertThat(result.get(1).getCertificateNumber()).isEqualTo("CERT-2024-002");
    }

    @Test
    void getMyCertificates_certificateWithNullCourse_mapsToPlaceholder() {
        Certificate certNoCourse = Certificate.builder()
                .id(3L)
                .employee(employee)
                .course(null)
                .certificateNumber("CERT-NO-COURSE")
                .score(70)
                .issuedAt(LocalDateTime.now())
                .build();

        when(employeeDetailsRepository.findByUserEmail(userEmail))
                .thenReturn(Optional.of(employee));
        when(certificateRepository.findByEmployeeId(1L))
                .thenReturn(List.of(certNoCourse));

        List<CertificateDTO> result = certificateService.getMyCertificates(userEmail);

        assertThat(result.get(0).getCourseTitle()).isEqualTo("—");
    }

    @Test
    void getMyCertificates_certificateWithNullEmployee_mapsToPlaceholder() {
        Certificate certNoEmployee = Certificate.builder()
                .id(4L)
                .employee(null)
                .course(course)
                .certificateNumber("CERT-NO-EMP")
                .score(88)
                .issuedAt(LocalDateTime.now())
                .build();

        when(employeeDetailsRepository.findByUserEmail(userEmail))
                .thenReturn(Optional.of(employee));
        when(certificateRepository.findByEmployeeId(1L))
                .thenReturn(List.of(certNoEmployee));

        List<CertificateDTO> result = certificateService.getMyCertificates(userEmail);

        assertThat(result.get(0).getEmployeeName()).isEqualTo("—");
        assertThat(result.get(0).getEmployeeId()).isNull();
    }

    // ── getAllCertificates ─────────────────────────────────────────────────────

    @Test
    void getAllCertificates_returnsDTOListForAllCertificates() {
        when(certificateRepository.findAll()).thenReturn(List.of(certificate));

        List<CertificateDTO> result = certificateService.getAllCertificates();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCertificateNumber()).isEqualTo("CERT-2024-001");
        verify(certificateRepository).findAll();
    }

    @Test
    void getAllCertificates_emptyRepository_returnsEmptyList() {
        when(certificateRepository.findAll()).thenReturn(List.of());

        List<CertificateDTO> result = certificateService.getAllCertificates();

        assertThat(result).isEmpty();
    }

    @Test
    void getAllCertificates_correctlyMapsIssuedAt() {
        LocalDateTime issuedAt = LocalDateTime.of(2024, 6, 15, 10, 0);
        when(certificateRepository.findAll()).thenReturn(List.of(certificate));

        List<CertificateDTO> result = certificateService.getAllCertificates();

        assertThat(result.get(0).getIssuedAt()).isEqualTo(issuedAt);
    }

    @Test
    void getAllCertificates_correctlyMapsId() {
        when(certificateRepository.findAll()).thenReturn(List.of(certificate));

        List<CertificateDTO> result = certificateService.getAllCertificates();

        assertThat(result.get(0).getId()).isEqualTo(1L);
    }

    @Test
    void getAllCertificates_multipleCertificates_mapsAll() {
        Certificate cert2 = Certificate.builder()
                .id(2L)
                .employee(employee)
                .course(course)
                .certificateNumber("CERT-2024-002")
                .score(90)
                .issuedAt(LocalDateTime.now())
                .build();

        when(certificateRepository.findAll()).thenReturn(List.of(certificate, cert2));

        List<CertificateDTO> result = certificateService.getAllCertificates();

        assertThat(result).hasSize(2);
        assertThat(result.stream().map(CertificateDTO::getCertificateNumber))
                .containsExactly("CERT-2024-001", "CERT-2024-002");
    }
}

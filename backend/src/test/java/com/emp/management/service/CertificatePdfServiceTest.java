package com.emp.management.service;

import com.emp.management.entity.Certificate;
import com.emp.management.entity.Course;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.User;
import com.emp.management.entity.Role;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.CertificateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificatePdfServiceTest {

    @Mock
    private CertificateRepository certificateRepository;

    @InjectMocks
    private CertificatePdfService certificatePdfService;

    private Certificate certificate;
    private EmployeeDetails employee;
    private Course course;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id(1L)
                .email("john@company.com")
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

    // ── generateByCertNumber ─────────────────────────────────────────────────

    @Test
    void generateByCertNumber_validCertNumber_returnsNonEmptyByteArray() throws IOException {
        when(certificateRepository.findByCertificateNumber("CERT-2024-001"))
                .thenReturn(Optional.of(certificate));

        byte[] result = certificatePdfService.generateByCertNumber("CERT-2024-001");

        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);
    }

    @Test
    void generateByCertNumber_unknownCertNumber_throwsResourceNotFoundException() {
        when(certificateRepository.findByCertificateNumber("UNKNOWN-999"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> certificatePdfService.generateByCertNumber("UNKNOWN-999"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("UNKNOWN-999");
    }

    @Test
    void generateByCertNumber_returnsPdfBytes_startingWithPdfMagicBytes() throws IOException {
        when(certificateRepository.findByCertificateNumber("CERT-2024-001"))
                .thenReturn(Optional.of(certificate));

        byte[] result = certificatePdfService.generateByCertNumber("CERT-2024-001");

        // PDF files always begin with "%PDF" (bytes 37, 80, 68, 70)
        assertThat(result[0]).isEqualTo((byte) '%');
        assertThat(result[1]).isEqualTo((byte) 'P');
        assertThat(result[2]).isEqualTo((byte) 'D');
        assertThat(result[3]).isEqualTo((byte) 'F');
    }

    // ── generateById ─────────────────────────────────────────────────────────

    @Test
    void generateById_validId_returnsNonEmptyByteArray() throws IOException {
        when(certificateRepository.findById(1L))
                .thenReturn(Optional.of(certificate));

        byte[] result = certificatePdfService.generateById(1L);

        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);
    }

    @Test
    void generateById_unknownId_throwsResourceNotFoundException() {
        when(certificateRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> certificatePdfService.generateById(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void generateById_returnsPdfBytes_startingWithPdfMagicBytes() throws IOException {
        when(certificateRepository.findById(1L))
                .thenReturn(Optional.of(certificate));

        byte[] result = certificatePdfService.generateById(1L);

        assertThat(result[0]).isEqualTo((byte) '%');
        assertThat(result[1]).isEqualTo((byte) 'P');
        assertThat(result[2]).isEqualTo((byte) 'D');
        assertThat(result[3]).isEqualTo((byte) 'F');
    }

    // ── Edge cases: null employee / course ───────────────────────────────────

    @Test
    void generateByCertNumber_nullEmployee_stillGeneratesPdf() throws IOException {
        Certificate certNoEmployee = Certificate.builder()
                .id(2L)
                .employee(null)
                .course(course)
                .certificateNumber("CERT-NO-EMP")
                .score(90)
                .issuedAt(LocalDateTime.now())
                .build();

        when(certificateRepository.findByCertificateNumber("CERT-NO-EMP"))
                .thenReturn(Optional.of(certNoEmployee));

        byte[] result = certificatePdfService.generateByCertNumber("CERT-NO-EMP");

        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);
    }

    @Test
    void generateByCertNumber_nullCourse_stillGeneratesPdf() throws IOException {
        Certificate certNoCourse = Certificate.builder()
                .id(3L)
                .employee(employee)
                .course(null)
                .certificateNumber("CERT-NO-COURSE")
                .score(75)
                .issuedAt(LocalDateTime.now())
                .build();

        when(certificateRepository.findByCertificateNumber("CERT-NO-COURSE"))
                .thenReturn(Optional.of(certNoCourse));

        byte[] result = certificatePdfService.generateByCertNumber("CERT-NO-COURSE");

        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);
    }

    @Test
    void generateByCertNumber_nullIssuedAt_stillGeneratesPdf() throws IOException {
        Certificate certNoDate = Certificate.builder()
                .id(4L)
                .employee(employee)
                .course(course)
                .certificateNumber("CERT-NO-DATE")
                .score(80)
                .issuedAt(null)
                .build();

        when(certificateRepository.findByCertificateNumber("CERT-NO-DATE"))
                .thenReturn(Optional.of(certNoDate));

        byte[] result = certificatePdfService.generateByCertNumber("CERT-NO-DATE");

        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);
    }

    @Test
    void generateByCertNumber_nullEmployeeAndCourse_stillGeneratesPdf() throws IOException {
        Certificate minimalCert = Certificate.builder()
                .id(5L)
                .employee(null)
                .course(null)
                .certificateNumber("CERT-MINIMAL")
                .issuedAt(null)
                .build();

        when(certificateRepository.findByCertificateNumber("CERT-MINIMAL"))
                .thenReturn(Optional.of(minimalCert));

        byte[] result = certificatePdfService.generateByCertNumber("CERT-MINIMAL");

        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);
    }

    @Test
    void generateById_nullId_throwsException() {
        when(certificateRepository.findById(null)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> certificatePdfService.generateById(null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void generateByCertNumber_emptyString_throwsResourceNotFoundException() {
        when(certificateRepository.findByCertificateNumber(""))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> certificatePdfService.generateByCertNumber(""))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void generateByCertNumber_specialCharsInCertNumber_returnsNonEmptyByteArray() throws IOException {
        Certificate specialCert = Certificate.builder()
                .id(6L)
                .employee(employee)
                .course(course)
                .certificateNumber("CERT/2024-@#$%001")
                .score(95)
                .issuedAt(LocalDateTime.now())
                .build();

        when(certificateRepository.findByCertificateNumber("CERT/2024-@#$%001"))
                .thenReturn(Optional.of(specialCert));

        byte[] result = certificatePdfService.generateByCertNumber("CERT/2024-@#$%001");

        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);
    }

    @Test
    void generateByCertNumber_employeeWithUnicodeChars_returnsNonEmptyByteArray() throws IOException {
        User unicodeUser = User.builder()
                .id(2L)
                .email("unicode@company.com")
                .role(Role.EMPLOYEE)
                .active(true)
                .build();

        EmployeeDetails unicodeEmployee = EmployeeDetails.builder()
                .id(2L)
                .user(unicodeUser)
                .firstName("Ångström")
                .lastName("Müller")
                .active(true)
                .build();

        Certificate unicodeCert = Certificate.builder()
                .id(7L)
                .employee(unicodeEmployee)
                .course(course)
                .certificateNumber("CERT-UNICODE-001")
                .score(88)
                .issuedAt(LocalDateTime.now())
                .build();

        when(certificateRepository.findByCertificateNumber("CERT-UNICODE-001"))
                .thenReturn(Optional.of(unicodeCert));

        byte[] result = certificatePdfService.generateByCertNumber("CERT-UNICODE-001");

        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);
    }

    @Test
    void generateByCertNumber_calledTwiceWithSameCert_returnsBothNonEmpty() throws IOException {
        when(certificateRepository.findByCertificateNumber("CERT-2024-001"))
                .thenReturn(Optional.of(certificate));

        byte[] result1 = certificatePdfService.generateByCertNumber("CERT-2024-001");
        byte[] result2 = certificatePdfService.generateByCertNumber("CERT-2024-001");

        assertThat(result1).isNotNull();
        assertThat(result2).isNotNull();
        assertThat(result1.length).isGreaterThan(0);
        assertThat(result2.length).isGreaterThan(0);
    }

    @Test
    void generateById_largeId_throwsResourceNotFoundException() {
        when(certificateRepository.findById(Long.MAX_VALUE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> certificatePdfService.generateById(Long.MAX_VALUE))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void generateByCertNumber_longCourseName_returnsNonEmptyByteArray() throws IOException {
        Course longTitleCourse = Course.builder()
                .id(2L)
                .title("This is a very long course title that might overflow the PDF layout boundaries and test text wrapping behavior in the certificate generator")
                .active(true)
                .build();

        Certificate longCert = Certificate.builder()
                .id(8L)
                .employee(employee)
                .course(longTitleCourse)
                .certificateNumber("CERT-LONG-001")
                .score(92)
                .issuedAt(LocalDateTime.now())
                .build();

        when(certificateRepository.findByCertificateNumber("CERT-LONG-001"))
                .thenReturn(Optional.of(longCert));

        byte[] result = certificatePdfService.generateByCertNumber("CERT-LONG-001");

        assertThat(result).isNotNull();
        assertThat(result.length).isGreaterThan(0);
    }
}

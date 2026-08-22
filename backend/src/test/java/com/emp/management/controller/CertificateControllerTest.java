//package com.emp.management.controller;
//
//import com.emp.management.config.TestSecurityConfig;
//import com.emp.management.dto.CertificateDTO;
//import com.emp.management.exception.GlobalExceptionHandler;
//import com.emp.management.exception.ResourceNotFoundException;
//import com.emp.management.security.JwtAuthFilter;
//import com.emp.management.service.CertificatePdfService;
//import com.emp.management.service.CertificateService;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
//import org.springframework.boot.test.mock.mockito.MockBean;
//import org.springframework.context.annotation.ComponentScan;
//import org.springframework.context.annotation.FilterType;
//import org.springframework.context.annotation.Import;
//import org.springframework.http.MediaType;
//import org.springframework.security.test.context.support.WithMockUser;
//import org.springframework.test.web.servlet.MockMvc;
//
//import java.io.IOException;
//import java.time.LocalDateTime;
//import java.util.List;
//
//import static org.hamcrest.Matchers.containsString;
//import static org.mockito.ArgumentMatchers.anyString;
//import static org.mockito.Mockito.when;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
//
//@WebMvcTest(controllers = CertificateController.class,
//    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
//@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
//class CertificateControllerTest {
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @MockBean
//    private CertificateService certificateService;
//
//    @MockBean
//    private CertificatePdfService certificatePdfService;
//
//    private CertificateDTO sampleCertDto;
//
//    @BeforeEach
//    void setUp() {
//        sampleCertDto = CertificateDTO.builder()
//                .id(1L)
//                .courseTitle("Spring Boot Fundamentals")
//                .certificateNumber("CERT-2024-001")
//                .issuedAt(LocalDateTime.of(2024, 6, 15, 10, 0))
//                .employeeName("John Doe")
//                .employeeId(1L)
//                .score(85)
//                .build();
//    }
//
//    // ── GET /api/certificates/download ───────────────────────────────────────
//
//    @Test
//    @WithMockUser(username = "john@company.com", roles = "EMPLOYEE")
//    void downloadByCertNumber_validCertNo_returns200WithPdfContentType() throws Exception {
//        byte[] fakePdf = buildMinimalPdfBytes();
//        when(certificatePdfService.generateByCertNumber("CERT-2024-001")).thenReturn(fakePdf);
//
//        mockMvc.perform(get("/api/certificates/download")
//                        .param("certNo", "CERT-2024-001"))
//                .andExpect(status().isOk())
//                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
//    }
//
//    @Test
//    @WithMockUser(username = "john@company.com", roles = "EMPLOYEE")
//    void downloadByCertNumber_validCertNo_returns200WithContentDispositionHeader() throws Exception {
//        byte[] fakePdf = buildMinimalPdfBytes();
//        when(certificatePdfService.generateByCertNumber("CERT-2024-001")).thenReturn(fakePdf);
//
//        mockMvc.perform(get("/api/certificates/download")
//                        .param("certNo", "CERT-2024-001"))
//                .andExpect(status().isOk())
//                .andExpect(header().string("Content-Disposition",
//                        containsString("Certificate_CERT-2024-001.pdf")));
//    }
//
//    @Test
//    @WithMockUser(username = "john@company.com", roles = "EMPLOYEE")
//    void downloadByCertNumber_validCertNo_returnsNonEmptyBody() throws Exception {
//        byte[] fakePdf = buildMinimalPdfBytes();
//        when(certificatePdfService.generateByCertNumber("CERT-2024-001")).thenReturn(fakePdf);
//
//        mockMvc.perform(get("/api/certificates/download")
//                        .param("certNo", "CERT-2024-001"))
//                .andExpect(status().isOk())
//                .andExpect(content().bytes(fakePdf));
//    }
//
//    @Test
//    @WithMockUser(username = "john@company.com", roles = "EMPLOYEE")
//    void downloadByCertNumber_unknownCertNo_returns404() throws Exception {
//        when(certificatePdfService.generateByCertNumber("UNKNOWN-999"))
//                .thenThrow(new ResourceNotFoundException("Certificate not found: UNKNOWN-999"));
//
//        mockMvc.perform(get("/api/certificates/download")
//                        .param("certNo", "UNKNOWN-999"))
//                .andExpect(status().isNotFound());
//    }
//
//    @Test
//    void downloadByCertNumber_unauthenticated_returns401or403() throws Exception {
//        mockMvc.perform(get("/api/certificates/download")
//                        .param("certNo", "CERT-2024-001"))
//                .andExpect(result -> {
//                    int status = result.getResponse().getStatus();
//                    assert status == 401 || status == 403
//                            : "Expected 401 or 403 but got " + status;
//                });
//    }
//
//    @Test
//    @WithMockUser(username = "john@company.com", roles = "EMPLOYEE")
//    void downloadByCertNumber_missingCertNoParam_returns4xx() throws Exception {
//        // No certNo param provided — Spring throws MissingServletRequestParameterException;
//        // GlobalExceptionHandler does not handle it specifically, so it falls through
//        // to the generic handler which returns 500, or Spring returns 400 before handler.
//        mockMvc.perform(get("/api/certificates/download"))
//                .andExpect(result -> {
//                    int status = result.getResponse().getStatus();
//                    // Accept 400 (if Spring resolves before handler) or 500 (caught by generic handler)
//                    assert status == 400 || status == 500
//                            : "Expected 400 or 500 for missing param but got " + status;
//                });
//    }
//
//    @Test
//    @WithMockUser(roles = "EMPLOYEE")
//    void downloadByCertNumber_certNoWithSpecialChars_sanitizesFilename() throws Exception {
//        byte[] fakePdf = buildMinimalPdfBytes();
//        // certNo with characters that must be sanitized in the filename
//        when(certificatePdfService.generateByCertNumber("CERT/2024@001"))
//                .thenReturn(fakePdf);
//
//        mockMvc.perform(get("/api/certificates/download")
//                        .param("certNo", "CERT/2024@001"))
//                .andExpect(status().isOk())
//                .andExpect(header().string("Content-Disposition",
//                        containsString("Certificate_CERT_2024_001.pdf")));
//    }
//
//    @Test
//    @WithMockUser(roles = "ADMIN")
//    void downloadByCertNumber_asAdmin_returns200() throws Exception {
//        byte[] fakePdf = buildMinimalPdfBytes();
//        when(certificatePdfService.generateByCertNumber("CERT-2024-001")).thenReturn(fakePdf);
//
//        mockMvc.perform(get("/api/certificates/download")
//                        .param("certNo", "CERT-2024-001"))
//                .andExpect(status().isOk())
//                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
//    }
//
//    @Test
//    @WithMockUser(roles = "HR")
//    void downloadByCertNumber_asHr_returns200() throws Exception {
//        byte[] fakePdf = buildMinimalPdfBytes();
//        when(certificatePdfService.generateByCertNumber("CERT-2024-001")).thenReturn(fakePdf);
//
//        mockMvc.perform(get("/api/certificates/download")
//                        .param("certNo", "CERT-2024-001"))
//                .andExpect(status().isOk());
//    }
//
//    @Test
//    @WithMockUser(roles = "MANAGER")
//    void downloadByCertNumber_asManager_returns200() throws Exception {
//        byte[] fakePdf = buildMinimalPdfBytes();
//        when(certificatePdfService.generateByCertNumber("CERT-2024-001")).thenReturn(fakePdf);
//
//        mockMvc.perform(get("/api/certificates/download")
//                        .param("certNo", "CERT-2024-001"))
//                .andExpect(status().isOk());
//    }
//
//    @Test
//    @WithMockUser(roles = "EMPLOYEE")
//    void downloadByCertNumber_serviceThrowsIoException_returns500() throws Exception {
//        when(certificatePdfService.generateByCertNumber(anyString()))
//                .thenThrow(new IOException("PDF generation failed"));
//
//        mockMvc.perform(get("/api/certificates/download")
//                        .param("certNo", "CERT-ERR-001"))
//                .andExpect(status().isInternalServerError());
//    }
//
//    // ── GET /api/certificates/my ─────────────────────────────────────────────
//
//    @Test
//    @WithMockUser(username = "john@company.com", roles = "EMPLOYEE")
//    void getMyCertificates_authenticated_returns200WithList() throws Exception {
//        when(certificateService.getMyCertificates("john@company.com"))
//                .thenReturn(List.of(sampleCertDto));
//
//        mockMvc.perform(get("/api/certificates/my"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.length()").value(1))
//                .andExpect(jsonPath("$[0].certificateNumber").value("CERT-2024-001"))
//                .andExpect(jsonPath("$[0].courseTitle").value("Spring Boot Fundamentals"))
//                .andExpect(jsonPath("$[0].employeeName").value("John Doe"))
//                .andExpect(jsonPath("$[0].score").value(85));
//    }
//
//    @Test
//    @WithMockUser(username = "john@company.com", roles = "EMPLOYEE")
//    void getMyCertificates_noCertificates_returns200WithEmptyList() throws Exception {
//        when(certificateService.getMyCertificates("john@company.com"))
//                .thenReturn(List.of());
//
//        mockMvc.perform(get("/api/certificates/my"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.length()").value(0));
//    }
//
//    @Test
//    void getMyCertificates_unauthenticated_returns401or403() throws Exception {
//        mockMvc.perform(get("/api/certificates/my"))
//                .andExpect(result -> {
//                    int status = result.getResponse().getStatus();
//                    assert status == 401 || status == 403;
//                });
//    }
//
//    @Test
//    @WithMockUser(username = "unknown@company.com", roles = "EMPLOYEE")
//    void getMyCertificates_employeeNotFound_returns404() throws Exception {
//        when(certificateService.getMyCertificates("unknown@company.com"))
//                .thenThrow(new ResourceNotFoundException("Employee not found for email: unknown@company.com"));
//
//        mockMvc.perform(get("/api/certificates/my"))
//                .andExpect(status().isNotFound());
//    }
//
//    // ── GET /api/certificates ────────────────────────────────────────────────
//
//    @Test
//    @WithMockUser(roles = "ADMIN")
//    void getAllCertificates_asAdmin_returns200WithList() throws Exception {
//        when(certificateService.getAllCertificates()).thenReturn(List.of(sampleCertDto));
//
//        mockMvc.perform(get("/api/certificates"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.length()").value(1))
//                .andExpect(jsonPath("$[0].certificateNumber").value("CERT-2024-001"));
//    }
//
//    @Test
//    @WithMockUser(roles = "EMPLOYEE")
//    void getAllCertificates_asEmployee_returns403() throws Exception {
//        mockMvc.perform(get("/api/certificates"))
//                .andExpect(status().isForbidden());
//    }
//
//    @Test
//    @WithMockUser(roles = "HR")
//    void getAllCertificates_asHr_returns403() throws Exception {
//        mockMvc.perform(get("/api/certificates"))
//                .andExpect(status().isForbidden());
//    }
//
//    @Test
//    @WithMockUser(roles = "MANAGER")
//    void getAllCertificates_asManager_returns403() throws Exception {
//        mockMvc.perform(get("/api/certificates"))
//                .andExpect(status().isForbidden());
//    }
//
//    @Test
//    void getAllCertificates_unauthenticated_returns401or403() throws Exception {
//        mockMvc.perform(get("/api/certificates"))
//                .andExpect(result -> {
//                    int status = result.getResponse().getStatus();
//                    assert status == 401 || status == 403;
//                });
//    }
//
//    @Test
//    @WithMockUser(roles = "ADMIN")
//    void getAllCertificates_emptyList_returns200WithEmptyArray() throws Exception {
//        when(certificateService.getAllCertificates()).thenReturn(List.of());
//
//        mockMvc.perform(get("/api/certificates"))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.length()").value(0));
//    }
//
//    // ── Helpers ──────────────────────────────────────────────────────────────
//
//    /**
//     * Creates a minimal valid PDF header so response body assertions work.
//     */
//    private byte[] buildMinimalPdfBytes() {
//        return "%PDF-1.4 fake pdf content for testing".getBytes();
//    }
//}

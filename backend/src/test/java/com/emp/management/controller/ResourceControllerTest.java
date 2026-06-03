package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.ResourceDTO;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.ResourceStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ResourceController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class ResourceControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private ResourceStorageService resourceStorageService;

    private ResourceDTO sampleDTO;

    @BeforeEach
    void setUp() {
        sampleDTO = ResourceDTO.builder()
                .id(1L)
                .title("HR Policy")
                .originalFileName("hr_policy.pdf")
                .fileType("application/pdf")
                .fileSize(2048L)
                .description("HR policy document")
                .category("POLICY")
                .subCategory("Policy")
                .active(true)
                .uploadedByName("admin@company.com")
                .uploadedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ── GET /api/resources ────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAll_anyAuthenticatedUser_returns200() throws Exception {
        when(resourceStorageService.getAllResources()).thenReturn(List.of(sampleDTO));

        mockMvc.perform(get("/api/resources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("HR Policy"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getAll_asAdmin_returns200() throws Exception {
        when(resourceStorageService.getAllResources()).thenReturn(List.of(sampleDTO));

        mockMvc.perform(get("/api/resources"))
                .andExpect(status().isOk());
    }

    @Test
    void getAll_unauthenticated_returns401Or403() throws Exception {
        mockMvc.perform(get("/api/resources"))
                .andExpect(result ->
                    assertThat(result.getResponse().getStatus()).isIn(401, 403));
    }

    // ── POST /api/resources/upload ────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN", username = "admin@company.com")
    void upload_asAdmin_returns200() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "policy.pdf",
                "application/pdf", "pdf content".getBytes());
        when(resourceStorageService.uploadFile(any(), any(), any(), any(), any(), any()))
                .thenReturn(sampleDTO);

        mockMvc.perform(multipart("/api/resources/upload")
                        .file(file)
                        .param("title", "HR Policy")
                        .param("category", "POLICY")
                        .param("subCategory", "Policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("HR Policy"));
    }

    @Test
    @WithMockUser(roles = "HR", username = "hr@company.com")
    void upload_asHR_returns200() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "form.pdf",
                "application/pdf", "content".getBytes());
        when(resourceStorageService.uploadFile(any(), any(), any(), any(), any(), any()))
                .thenReturn(sampleDTO);

        mockMvc.perform(multipart("/api/resources/upload")
                        .file(file)
                        .param("title", "Leave Form")
                        .param("category", "FORM_TEMPLATE"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void upload_asEmployee_returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "policy.pdf",
                "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/api/resources/upload")
                        .file(file)
                        .param("title", "Policy"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void upload_asManager_returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf",
                "application/pdf", "content".getBytes());

        mockMvc.perform(multipart("/api/resources/upload")
                        .file(file)
                        .param("title", "Doc"))
                .andExpect(status().isForbidden());
    }

    // ── PATCH /api/resources/{id} ─────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_asAdmin_returns200() throws Exception {
        when(resourceStorageService.updateResource(eq(1L), any())).thenReturn(sampleDTO);

        mockMvc.perform(patch("/api/resources/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Updated HR Policy"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("HR Policy"));
    }

    @Test
    @WithMockUser(roles = "HR")
    void update_asHR_returns200() throws Exception {
        when(resourceStorageService.updateResource(eq(1L), any())).thenReturn(sampleDTO);

        mockMvc.perform(patch("/api/resources/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("description", "Updated"))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void update_asEmployee_returns403() throws Exception {
        mockMvc.perform(patch("/api/resources/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Bad"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_resourceNotFound_returns404() throws Exception {
        when(resourceStorageService.updateResource(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Resource", 99L));

        mockMvc.perform(patch("/api/resources/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "X"))))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /api/resources/{id}/toggle ─────────────────────────────────────

    @Test
    @WithMockUser(roles = "HR")
    void toggle_asHR_returns200() throws Exception {
        ResourceDTO toggled = ResourceDTO.builder().id(1L).title("HR Policy")
                .active(false).build();
        when(resourceStorageService.toggleActive(1L)).thenReturn(toggled);

        mockMvc.perform(patch("/api/resources/1/toggle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void toggle_asManager_returns403() throws Exception {
        mockMvc.perform(patch("/api/resources/1/toggle"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void toggle_asEmployee_returns403() throws Exception {
        mockMvc.perform(patch("/api/resources/1/toggle"))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/resources/download/{id} ─────────────────────────────────────

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void download_anyUser_returns200WithAttachmentHeader() throws Exception {
        ByteArrayResource fileResource = new ByteArrayResource("pdf content".getBytes()) {
            @Override public String getFilename() { return "hr_policy.pdf"; }
        };
        when(resourceStorageService.downloadFile(1L)).thenReturn(fileResource);

        mockMvc.perform(get("/api/resources/download/1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void download_resourceNotFound_returns404() throws Exception {
        when(resourceStorageService.downloadFile(99L))
                .thenThrow(new ResourceNotFoundException("Resource", 99L));

        mockMvc.perform(get("/api/resources/download/99"))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/resources/view/{id} ─────────────────────────────────────────

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void view_anyUser_returns200WithInlineHeader() throws Exception {
        ByteArrayResource fileResource = new ByteArrayResource("pdf content".getBytes()) {
            @Override public String getFilename() { return "hr_policy.pdf"; }
        };
        when(resourceStorageService.downloadFile(1L)).thenReturn(fileResource);

        mockMvc.perform(get("/api/resources/view/1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("inline")));
    }

    // ── DELETE /api/resources/{id} ────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_asAdmin_returns204() throws Exception {
        doNothing().when(resourceStorageService).deleteResource(1L);

        mockMvc.perform(delete("/api/resources/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "HR")
    void delete_asHR_returns204() throws Exception {
        doNothing().when(resourceStorageService).deleteResource(1L);

        mockMvc.perform(delete("/api/resources/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void delete_asEmployee_returns403() throws Exception {
        mockMvc.perform(delete("/api/resources/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void delete_asManager_returns403() throws Exception {
        mockMvc.perform(delete("/api/resources/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_resourceNotFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Resource", 99L))
                .when(resourceStorageService).deleteResource(99L);

        mockMvc.perform(delete("/api/resources/99"))
                .andExpect(status().isNotFound());
    }
}

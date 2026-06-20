package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.ResourceDTO;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.ResourceStorageService;
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

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ResourceController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthFilter.class))
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class ResourceControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ResourceStorageService resourceStorageService;

    private ResourceDTO sampleResource;

    @BeforeEach
    void setUp() {
        sampleResource = ResourceDTO.builder()
                .id(1L).title("Employee Handbook").category("Policies")
                .originalFileName("handbook.pdf").fileType("application/pdf")
                .fileSize(1024L).active(true).uploadedByName("admin@company.com").build();
    }

    // ── GET /api/resources ────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAll_asEmployee_returns200WithResources() throws Exception {
        when(resourceStorageService.getAllResources()).thenReturn(List.of(sampleResource));

        mockMvc.perform(get("/api/resources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Employee Handbook"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAll_emptyList_returns200() throws Exception {
        when(resourceStorageService.getAllResources()).thenReturn(List.of());

        mockMvc.perform(get("/api/resources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getAll_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(get("/api/resources"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    if (s != 401 && s != 403) throw new AssertionError("Expected 401 or 403, got: " + s);
                });
    }

    // ── POST /api/resources/upload ────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@company.com", roles = "ADMIN")
    void upload_asAdmin_returns200() throws Exception {
        when(resourceStorageService.uploadFile(any(), any(), any(), any(), any(), any()))
                .thenReturn(sampleResource);

        MockMultipartFile file = new MockMultipartFile(
                "file", "handbook.pdf", "application/pdf", "PDF_CONTENT".getBytes());

        mockMvc.perform(multipart("/api/resources/upload")
                .file(file)
                .param("title", "Employee Handbook")
                .param("category", "Policies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Employee Handbook"));
    }

    @Test
    @WithMockUser(roles = "HR")
    void upload_asHr_returns200() throws Exception {
        when(resourceStorageService.uploadFile(any(), any(), any(), any(), any(), any()))
                .thenReturn(sampleResource);

        MockMultipartFile file = new MockMultipartFile(
                "file", "policy.pdf", "application/pdf", "PDF_CONTENT".getBytes());

        mockMvc.perform(multipart("/api/resources/upload").file(file))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void upload_asEmployee_returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "PDF_CONTENT".getBytes());

        mockMvc.perform(multipart("/api/resources/upload").file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void upload_asManager_returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "PDF_CONTENT".getBytes());

        mockMvc.perform(multipart("/api/resources/upload").file(file))
                .andExpect(status().isForbidden());
    }

    // ── PATCH /api/resources/{id} ─────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void update_asAdmin_returns200() throws Exception {
        when(resourceStorageService.updateResource(eq(1L), any())).thenReturn(sampleResource);

        mockMvc.perform(patch("/api/resources/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Updated Handbook\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void update_asEmployee_returns403() throws Exception {
        mockMvc.perform(patch("/api/resources/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Updated\"}"))
                .andExpect(status().isForbidden());
    }

    // ── PATCH /api/resources/{id}/toggle ──────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    void toggle_asAdmin_returns200() throws Exception {
        ResourceDTO toggled = ResourceDTO.builder().id(1L).title("Handbook").active(false).build();
        when(resourceStorageService.toggleActive(1L)).thenReturn(toggled);

        mockMvc.perform(patch("/api/resources/1/toggle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void toggle_asEmployee_returns403() throws Exception {
        mockMvc.perform(patch("/api/resources/1/toggle"))
                .andExpect(status().isForbidden());
    }

    // ── GET /api/resources/view/{id} ──────────────────────────────────────────

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void view_asEmployee_returns200() throws Exception {
        byte[] content = "file content".getBytes();
        org.springframework.core.io.Resource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() { return "handbook.pdf"; }
        };
        when(resourceStorageService.downloadFile(1L)).thenReturn(resource);

        mockMvc.perform(get("/api/resources/view/1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "inline; filename=\"handbook.pdf\""));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void view_notFound_returns404() throws Exception {
        when(resourceStorageService.downloadFile(999L))
                .thenThrow(new ResourceNotFoundException("Resource not found"));

        mockMvc.perform(get("/api/resources/view/999"))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/resources/download/{id} ──────────────────────────────────────

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void download_asEmployee_returns200() throws Exception {
        byte[] content = "file content".getBytes();
        org.springframework.core.io.Resource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() { return "handbook.pdf"; }
        };
        when(resourceStorageService.downloadFile(1L)).thenReturn(resource);

        mockMvc.perform(get("/api/resources/download/1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"handbook.pdf\""));
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
    @WithMockUser(roles = "ADMIN")
    void delete_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Resource not found"))
                .when(resourceStorageService).deleteResource(999L);

        mockMvc.perform(delete("/api/resources/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void delete_asEmployee_returns403() throws Exception {
        mockMvc.perform(delete("/api/resources/1"))
                .andExpect(status().isForbidden());
    }
}

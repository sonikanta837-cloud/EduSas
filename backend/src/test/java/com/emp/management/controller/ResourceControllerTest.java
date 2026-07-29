package com.emp.management.controller;

import com.emp.management.config.TestSecurityConfig;
import com.emp.management.dto.ResourceDTO;
import com.emp.management.exception.GlobalExceptionHandler;
import com.emp.management.security.JwtAuthFilter;
import com.emp.management.service.ResourceStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
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
    @Autowired private ObjectMapper objectMapper;
    @MockBean private ResourceStorageService resourceStorageService;

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void getAll_anyAuthenticatedRole_returns200() throws Exception {
        when(resourceStorageService.getAllResources()).thenReturn(List.of(
                ResourceDTO.builder().id(1L).title("Handbook").build()));

        mockMvc.perform(get("/api/resources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(username = "hr@company.com", roles = "HR")
    void upload_asHR_returns200() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "handbook.pdf", "application/pdf", new byte[]{1, 2, 3});
        when(resourceStorageService.uploadFile(any(), eq("Handbook"), eq("Policy"), isNull(), isNull(), eq("hr@company.com")))
                .thenReturn(ResourceDTO.builder().id(1L).title("Handbook").build());

        mockMvc.perform(multipart("/api/resources/upload").file(file)
                        .param("title", "Handbook").param("category", "Policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Handbook"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void upload_asEmployee_returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "handbook.pdf", "application/pdf", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/resources/upload").file(file))
                .andExpect(status().isForbidden());

        verify(resourceStorageService, never()).uploadFile(any(), any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void upload_asManager_returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "handbook.pdf", "application/pdf", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/resources/upload").file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "HR")
    void update_asHR_returns200() throws Exception {
        when(resourceStorageService.updateResource(eq(1L), any()))
                .thenReturn(ResourceDTO.builder().id(1L).title("Updated title").build());

        mockMvc.perform(patch("/api/resources/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Updated title"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated title"));
    }

    @Test
    @WithMockUser(roles = "HR")
    void toggle_asHR_returns200() throws Exception {
        when(resourceStorageService.toggleActive(1L)).thenReturn(ResourceDTO.builder().id(1L).active(false).build());

        mockMvc.perform(patch("/api/resources/1/toggle"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void view_anyAuthenticatedRole_returns200WithInlineDisposition() throws Exception {
        Resource file = new ByteArrayResource("data".getBytes()) {
            @Override public String getFilename() { return "handbook.pdf"; }
        };
        when(resourceStorageService.downloadFile(1L)).thenReturn(file);

        mockMvc.perform(get("/api/resources/view/1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "inline; filename=\"handbook.pdf\""));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    void download_anyAuthenticatedRole_returns200WithAttachmentDisposition() throws Exception {
        Resource file = new ByteArrayResource("data".getBytes()) {
            @Override public String getFilename() { return "handbook.pdf"; }
        };
        when(resourceStorageService.downloadFile(1L)).thenReturn(file);

        mockMvc.perform(get("/api/resources/download/1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"handbook.pdf\""));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_asAdmin_returns204() throws Exception {
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
}

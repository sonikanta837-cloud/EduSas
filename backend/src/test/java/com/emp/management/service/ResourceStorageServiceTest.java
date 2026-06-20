package com.emp.management.service;

import com.emp.management.dto.ResourceDTO;
import com.emp.management.entity.Resource;
import com.emp.management.entity.Role;
import com.emp.management.entity.User;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.ResourceRepository;
import com.emp.management.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResourceStorageServiceTest {

    @Mock private ResourceRepository resourceRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private ResourceStorageService resourceStorageService;

    @TempDir Path tempDir;

    private User uploader;
    private Resource resource;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(resourceStorageService, "uploadDir", tempDir.toString());

        uploader = User.builder().id(1L).email("admin@company.com").role(Role.ADMIN).build();

        resource = Resource.builder()
                .id(1L).title("Test Policy").fileName("uuid_test.pdf")
                .originalFileName("test.pdf").fileType("application/pdf")
                .fileSize(1024L).filePath(tempDir.toString() + "/uuid_test.pdf")
                .description("Test description").category("POLICY")
                .subCategory("HR Policy").active(true).uploadedBy(uploader)
                .uploadedAt(LocalDateTime.now()).build();
    }

    // ── uploadFile ────────────────────────────────────────────────────────────

    @Test
    void uploadFile_validFile_savesAndReturnsDTO() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "policy.pdf", "application/pdf", "PDF content".getBytes());

        when(userRepository.findByEmail("admin@company.com")).thenReturn(Optional.of(uploader));
        when(resourceRepository.save(any(Resource.class))).thenReturn(resource);

        ResourceDTO result = resourceStorageService.uploadFile(
                file, "Test Policy", "POLICY", "HR Policy", "A policy doc", "admin@company.com");

        assertThat(result.getTitle()).isEqualTo("Test Policy");
        assertThat(result.getCategory()).isEqualTo("POLICY");
        verify(resourceRepository).save(any(Resource.class));
    }

    @Test
    void uploadFile_nullTitle_usesOriginalFileName() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "policy.pdf", "application/pdf", "PDF content".getBytes());

        when(userRepository.findByEmail("admin@company.com")).thenReturn(Optional.of(uploader));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(inv -> {
            Resource r = inv.getArgument(0);
            r.setId(99L);
            r.setUploadedBy(uploader);
            return r;
        });

        ResourceDTO result = resourceStorageService.uploadFile(
                file, null, "POLICY", null, null, "admin@company.com");

        // Title defaults to sanitized original filename
        assertThat(result.getTitle()).isEqualTo("policy.pdf");
    }

    @Test
    void uploadFile_blankTitle_usesOriginalFileName() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "content".getBytes());

        when(userRepository.findByEmail("admin@company.com")).thenReturn(Optional.of(uploader));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(inv -> {
            Resource r = inv.getArgument(0);
            r.setId(99L);
            r.setUploadedBy(uploader);
            return r;
        });

        ResourceDTO result = resourceStorageService.uploadFile(
                file, "   ", "GENERAL", null, null, "admin@company.com");

        assertThat(result.getTitle()).isEqualTo("report.pdf");
    }

    @Test
    void uploadFile_userNotFound_throwsResourceNotFoundException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "content".getBytes());

        when(userRepository.findByEmail("ghost@company.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resourceStorageService.uploadFile(
                file, "Title", "POLICY", null, null, "ghost@company.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void uploadFile_specialCharactersInFilename_sanitized() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "my policy 2026!.pdf", "application/pdf", "content".getBytes());

        when(userRepository.findByEmail("admin@company.com")).thenReturn(Optional.of(uploader));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(inv -> {
            Resource r = inv.getArgument(0);
            r.setId(99L); r.setUploadedBy(uploader);
            return r;
        });

        // Should not throw — filename sanitized
        assertThatCode(() -> resourceStorageService.uploadFile(
                file, "My Policy", "POLICY", null, null, "admin@company.com"))
                .doesNotThrowAnyException();
    }

    // ── getAllResources ────────────────────────────────────────────────────────

    @Test
    void getAllResources_returnsMappedDTOs() {
        when(resourceRepository.findAllByOrderByUploadedAtDesc()).thenReturn(List.of(resource));

        List<ResourceDTO> result = resourceStorageService.getAllResources();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Test Policy");
        assertThat(result.get(0).getCategory()).isEqualTo("POLICY");
        assertThat(result.get(0).getUploadedByName()).isEqualTo("admin@company.com");
    }

    @Test
    void getAllResources_nullUploader_returnsEmDash() {
        resource.setUploadedBy(null);
        when(resourceRepository.findAllByOrderByUploadedAtDesc()).thenReturn(List.of(resource));

        List<ResourceDTO> result = resourceStorageService.getAllResources();

        assertThat(result.get(0).getUploadedByName()).isEqualTo("—");
    }

    @Test
    void getAllResources_nullTitle_usesOriginalFileName() {
        resource.setTitle(null);
        when(resourceRepository.findAllByOrderByUploadedAtDesc()).thenReturn(List.of(resource));

        List<ResourceDTO> result = resourceStorageService.getAllResources();

        assertThat(result.get(0).getTitle()).isEqualTo("test.pdf");
    }

    @Test
    void getAllResources_empty_returnsEmptyList() {
        when(resourceRepository.findAllByOrderByUploadedAtDesc()).thenReturn(List.of());

        List<ResourceDTO> result = resourceStorageService.getAllResources();

        assertThat(result).isEmpty();
    }

    // ── updateResource ────────────────────────────────────────────────────────

    @Test
    void updateResource_validBody_updatesFields() {
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));
        when(resourceRepository.save(any())).thenReturn(resource);

        Map<String, String> body = Map.of(
                "title", "Updated Policy",
                "description", "New description",
                "category", "FORM_TEMPLATE",
                "subCategory", "IT Policy");

        ResourceDTO result = resourceStorageService.updateResource(1L, body);

        assertThat(resource.getTitle()).isEqualTo("Updated Policy");
        assertThat(resource.getDescription()).isEqualTo("New description");
        assertThat(resource.getCategory()).isEqualTo("FORM_TEMPLATE");
        assertThat(resource.getSubCategory()).isEqualTo("IT Policy");
    }

    @Test
    void updateResource_nullTitle_titleNotChanged() {
        resource.setTitle("Original Title");
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));
        when(resourceRepository.save(any())).thenReturn(resource);

        Map<String, String> body = Map.of("description", "New desc");

        resourceStorageService.updateResource(1L, body);

        assertThat(resource.getTitle()).isEqualTo("Original Title");
    }

    @Test
    void updateResource_notFound_throwsResourceNotFoundException() {
        when(resourceRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resourceStorageService.updateResource(999L, Map.of("title", "T")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── toggleActive ──────────────────────────────────────────────────────────

    @Test
    void toggleActive_activeResource_deactivates() {
        resource.setActive(true);
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));
        when(resourceRepository.save(any())).thenReturn(resource);

        ResourceDTO result = resourceStorageService.toggleActive(1L);

        assertThat(resource.isActive()).isFalse();
    }

    @Test
    void toggleActive_inactiveResource_activates() {
        resource.setActive(false);
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));
        when(resourceRepository.save(any())).thenReturn(resource);

        resourceStorageService.toggleActive(1L);

        assertThat(resource.isActive()).isTrue();
    }

    @Test
    void toggleActive_notFound_throws() {
        when(resourceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resourceStorageService.toggleActive(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── downloadFile ──────────────────────────────────────────────────────────

    @Test
    void downloadFile_fileExistsOnDisk_returnsResource() throws IOException {
        // Create actual file in temp dir
        Path file = tempDir.resolve("uuid_test.pdf");
        Files.write(file, "PDF content".getBytes());
        resource.setFilePath(file.toString());

        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));

        org.springframework.core.io.Resource result = resourceStorageService.downloadFile(1L);

        assertThat(result.exists()).isTrue();
    }

    @Test
    void downloadFile_fileNotOnDisk_throwsResourceNotFoundException() {
        resource.setFilePath("/nonexistent/path/file.pdf");
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));

        assertThatThrownBy(() -> resourceStorageService.downloadFile(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void downloadFile_resourceNotFound_throws() {
        when(resourceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resourceStorageService.downloadFile(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── deleteResource ────────────────────────────────────────────────────────

    @Test
    void deleteResource_fileExists_deletesFromDiskAndRepo() throws IOException {
        Path file = tempDir.resolve("uuid_test.pdf");
        Files.write(file, "PDF content".getBytes());
        resource.setFilePath(file.toString());

        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));
        doNothing().when(resourceRepository).delete(any());

        resourceStorageService.deleteResource(1L);

        assertThat(Files.exists(file)).isFalse();
        verify(resourceRepository).delete(resource);
    }

    @Test
    void deleteResource_fileNotOnDisk_stillDeletesFromRepo() {
        resource.setFilePath("/nonexistent/path/file.pdf");
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(resource));
        doNothing().when(resourceRepository).delete(any());

        // Should not throw even if file doesn't exist
        assertThatCode(() -> resourceStorageService.deleteResource(1L))
                .doesNotThrowAnyException();

        verify(resourceRepository).delete(resource);
    }

    @Test
    void deleteResource_notFound_throws() {
        when(resourceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resourceStorageService.deleteResource(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── toDTO — edge cases ────────────────────────────────────────────────────

    @Test
    void toDTO_updatedAtNull_usesUploadedAt() {
        resource.setUpdatedAt(null);
        LocalDateTime uploadedAt = LocalDateTime.now().minusDays(1);
        resource.setUploadedAt(uploadedAt);
        when(resourceRepository.findAllByOrderByUploadedAtDesc()).thenReturn(List.of(resource));

        List<ResourceDTO> result = resourceStorageService.getAllResources();

        assertThat(result.get(0).getUpdatedAt()).isEqualTo(uploadedAt);
    }
}

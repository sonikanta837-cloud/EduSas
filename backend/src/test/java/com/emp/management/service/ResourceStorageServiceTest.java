package com.emp.management.service;

import com.emp.management.dto.ResourceDTO;
import com.emp.management.entity.Resource;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
    @Mock private UserRepository     userRepository;

    @InjectMocks private ResourceStorageService service;

    @TempDir
    Path tempDir;

    private User uploader;
    private Resource sampleResource;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());

        uploader = new User();
        uploader.setId(1L);
        uploader.setEmail("admin@company.com");

        sampleResource = Resource.builder()
                .id(1L)
                .title("HR Policy")
                .fileName("uuid_hr_policy.pdf")
                .originalFileName("hr_policy.pdf")
                .fileType("application/pdf")
                .fileSize(2048L)
                .filePath(tempDir.toString() + "/uuid_hr_policy.pdf")
                .description("Human resources policy document")
                .category("POLICY")
                .subCategory("Policy")
                .active(true)
                .uploadedBy(uploader)
                .uploadedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ── uploadFile ────────────────────────────────────────────────────────────

    @Test
    void uploadFile_savesFileAndReturnsDTO() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "hr_policy.pdf", "application/pdf", "pdf content".getBytes());

        when(userRepository.findByEmail("admin@company.com")).thenReturn(Optional.of(uploader));
        when(resourceRepository.save(any(Resource.class))).thenReturn(sampleResource);

        ResourceDTO result = service.uploadFile(file, "HR Policy", "POLICY",
                "Policy", "HR policy doc", "admin@company.com");

        assertThat(result.getTitle()).isEqualTo("HR Policy");
        assertThat(result.getCategory()).isEqualTo("POLICY");
        assertThat(result.getSubCategory()).isEqualTo("Policy");
        assertThat(result.isActive()).isTrue();
        verify(resourceRepository).save(any(Resource.class));
    }

    @Test
    void uploadFile_withNullTitle_fallsBackToOriginalFileName() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "hr_policy.pdf", "application/pdf", "pdf content".getBytes());

        Resource savedWithOrigName = Resource.builder()
                .id(2L)
                .title("hr_policy.pdf")
                .fileName("uuid_hr_policy.pdf")
                .originalFileName("hr_policy.pdf")
                .fileType("application/pdf")
                .fileSize(11L)
                .filePath(tempDir.toString() + "/uuid_hr_policy.pdf")
                .active(true)
                .uploadedBy(uploader)
                .uploadedAt(LocalDateTime.now())
                .build();

        when(userRepository.findByEmail("admin@company.com")).thenReturn(Optional.of(uploader));
        when(resourceRepository.save(any(Resource.class))).thenReturn(savedWithOrigName);

        ResourceDTO result = service.uploadFile(file, null, "POLICY", null, null, "admin@company.com");

        assertThat(result.getTitle()).isEqualTo("hr_policy.pdf");
    }

    @Test
    void uploadFile_withBlankTitle_fallsBackToOriginalFileName() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "form.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "docx content".getBytes());

        Resource savedResource = Resource.builder()
                .id(3L)
                .title("form.docx")
                .originalFileName("form.docx")
                .active(true)
                .uploadedBy(uploader)
                .uploadedAt(LocalDateTime.now())
                .build();

        when(userRepository.findByEmail("admin@company.com")).thenReturn(Optional.of(uploader));
        when(resourceRepository.save(any(Resource.class))).thenReturn(savedResource);

        ResourceDTO result = service.uploadFile(file, "   ", "FORM_TEMPLATE", null, null, "admin@company.com");
        assertThat(result.getTitle()).isEqualTo("form.docx");
    }

    @Test
    void uploadFile_userNotFound_throwsResourceNotFoundException() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "content".getBytes());
        when(userRepository.findByEmail("ghost@company.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.uploadFile(file, "Title", "POLICY", null, null, "ghost@company.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("ghost@company.com");
    }

    @Test
    void uploadFile_ioError_throwsBadRequestException() throws IOException {
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.getOriginalFilename()).thenReturn("bad.pdf");
        when(mockFile.getInputStream()).thenThrow(new IOException("Disk full"));
        when(userRepository.findByEmail("admin@company.com")).thenReturn(Optional.of(uploader));

        assertThatThrownBy(() -> service.uploadFile(mockFile, "Title", "POLICY", null, null, "admin@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Failed to store file");
    }

    // ── getAllResources ────────────────────────────────────────────────────────

    @Test
    void getAllResources_returnsListOrderedByUploadedAtDesc() {
        Resource r2 = Resource.builder()
                .id(2L).title("Form A").active(true).uploadedBy(uploader)
                .uploadedAt(LocalDateTime.now().minusDays(1)).build();
        when(resourceRepository.findAllByOrderByUploadedAtDesc())
                .thenReturn(List.of(sampleResource, r2));

        List<ResourceDTO> result = service.getAllResources();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
    }

    @Test
    void getAllResources_emptyList_returnsEmpty() {
        when(resourceRepository.findAllByOrderByUploadedAtDesc()).thenReturn(List.of());

        List<ResourceDTO> result = service.getAllResources();
        assertThat(result).isEmpty();
    }

    // ── updateResource ────────────────────────────────────────────────────────

    @Test
    void updateResource_updatesTitle_descriptionAndSubCategory() {
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(sampleResource));
        when(resourceRepository.save(any(Resource.class))).thenReturn(sampleResource);

        ResourceDTO result = service.updateResource(1L, Map.of(
                "title", "Updated HR Policy",
                "description", "Updated description",
                "subCategory", "Document"
        ));

        assertThat(result).isNotNull();
        verify(resourceRepository).save(sampleResource);
        assertThat(sampleResource.getTitle()).isEqualTo("Updated HR Policy");
        assertThat(sampleResource.getDescription()).isEqualTo("Updated description");
        assertThat(sampleResource.getSubCategory()).isEqualTo("Document");
    }

    @Test
    void updateResource_notFound_throwsResourceNotFoundException() {
        when(resourceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateResource(99L, Map.of("title", "X")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateResource_nullTitleInBody_doesNotChangeTitle() {
        sampleResource.setTitle("Original Title");
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(sampleResource));
        when(resourceRepository.save(any(Resource.class))).thenReturn(sampleResource);

        service.updateResource(1L, Map.of("description", "New desc"));

        assertThat(sampleResource.getTitle()).isEqualTo("Original Title");
    }

    // ── toggleActive ──────────────────────────────────────────────────────────

    @Test
    void toggleActive_flipsTrueToFalse() {
        sampleResource.setActive(true);
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(sampleResource));
        Resource deactivated = Resource.builder().id(1L).title("HR Policy")
                .active(false).uploadedBy(uploader).uploadedAt(LocalDateTime.now()).build();
        when(resourceRepository.save(any(Resource.class))).thenReturn(deactivated);

        ResourceDTO result = service.toggleActive(1L);

        assertThat(sampleResource.isActive()).isFalse();
        verify(resourceRepository).save(sampleResource);
    }

    @Test
    void toggleActive_flipsFalseToTrue() {
        sampleResource.setActive(false);
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(sampleResource));
        when(resourceRepository.save(any(Resource.class))).thenReturn(sampleResource);

        service.toggleActive(1L);

        assertThat(sampleResource.isActive()).isTrue();
    }

    @Test
    void toggleActive_notFound_throwsResourceNotFoundException() {
        when(resourceRepository.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggleActive(77L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── deleteResource ────────────────────────────────────────────────────────

    @Test
    void deleteResource_deletesFromDbAndRemovesFile() throws Exception {
        // create the file so deleteIfExists succeeds
        Path filePath = tempDir.resolve("uuid_hr_policy.pdf");
        java.nio.file.Files.createFile(filePath);
        sampleResource.setFilePath(filePath.toString());

        when(resourceRepository.findById(1L)).thenReturn(Optional.of(sampleResource));

        service.deleteResource(1L);

        verify(resourceRepository).delete(sampleResource);
        assertThat(filePath).doesNotExist();
    }

    @Test
    void deleteResource_missingFile_stillDeletesFromDb() {
        sampleResource.setFilePath(tempDir.toString() + "/nonexistent.pdf");
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(sampleResource));

        service.deleteResource(1L);

        verify(resourceRepository).delete(sampleResource);
    }

    @Test
    void deleteResource_notFound_throwsResourceNotFoundException() {
        when(resourceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteResource(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── downloadFile ──────────────────────────────────────────────────────────

    @Test
    void downloadFile_notFound_throwsResourceNotFoundException() {
        when(resourceRepository.findById(55L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.downloadFile(55L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void downloadFile_fileNotOnDisk_throwsResourceNotFoundException() throws Exception {
        sampleResource.setFilePath(tempDir.toString() + "/missing.pdf");
        when(resourceRepository.findById(1L)).thenReturn(Optional.of(sampleResource));

        assertThatThrownBy(() -> service.downloadFile(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void downloadFile_fileOnDisk_returnsResource() throws Exception {
        Path filePath = tempDir.resolve("download_test.pdf");
        java.nio.file.Files.write(filePath, "pdf content".getBytes());
        sampleResource.setFilePath(filePath.toString());

        when(resourceRepository.findById(1L)).thenReturn(Optional.of(sampleResource));

        org.springframework.core.io.Resource result = service.downloadFile(1L);
        assertThat(result).isNotNull();
        assertThat(result.exists()).isTrue();
    }

    // ── toDTO ─────────────────────────────────────────────────────────────────

    @Test
    void toDTO_withNullUpdatedAt_fallsBackToUploadedAt() {
        LocalDateTime uploaded = LocalDateTime.now().minusDays(2);
        Resource r = Resource.builder()
                .id(5L).title("Doc").originalFileName("doc.pdf")
                .active(true).uploadedBy(uploader)
                .uploadedAt(uploaded)
                .updatedAt(null)
                .build();

        ResourceDTO dto = service.toDTO(r);
        assertThat(dto.getUpdatedAt()).isEqualTo(uploaded);
    }

    @Test
    void toDTO_withNullUploadedBy_returnsEmDash() {
        Resource r = Resource.builder()
                .id(6L).title("Doc").active(true)
                .uploadedBy(null)
                .uploadedAt(LocalDateTime.now())
                .build();

        ResourceDTO dto = service.toDTO(r);
        assertThat(dto.getUploadedByName()).isEqualTo("—");
    }

    @Test
    void toDTO_withNullTitle_usesOriginalFileName() {
        Resource r = Resource.builder()
                .id(7L).title(null).originalFileName("original.pdf")
                .active(true).uploadedBy(uploader)
                .uploadedAt(LocalDateTime.now())
                .build();

        ResourceDTO dto = service.toDTO(r);
        assertThat(dto.getTitle()).isEqualTo("original.pdf");
    }
}

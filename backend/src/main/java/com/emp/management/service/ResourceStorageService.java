package com.emp.management.service;

import com.emp.management.dto.ResourceDTO;
import com.emp.management.entity.Resource;
import com.emp.management.entity.User;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.ResourceRepository;
import com.emp.management.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceStorageService {

    private final ResourceRepository resourceRepository;
    private final UserRepository     userRepository;

    @Value("${app.file.upload-dir}")
    private String uploadDir;

    // ── Upload ────────────────────────────────────────────────────────────────

    @Transactional
    public ResourceDTO uploadFile(MultipartFile file, String title, String category,
                                  String subCategory, String description, String uploaderEmail) {
        User user = userRepository.findByEmail(uploaderEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + uploaderEmail));

        String safeOriginal = sanitizeFilename(file.getOriginalFilename());
        String uniqueName   = UUID.randomUUID() + "_" + safeOriginal;
        Path   uploadPath   = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadPath);
            Path dest = uploadPath.resolve(uniqueName).normalize();
            if (!dest.startsWith(uploadPath)) throw new BadRequestException("Invalid file name");
            Files.copy(file.getInputStream(), dest,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BadRequestException("Failed to store file: " + e.getMessage());
        }

        String resolvedTitle = (title != null && !title.isBlank()) ? title.trim() : safeOriginal;
        Resource saved = resourceRepository.save(Resource.builder()
                .title(resolvedTitle)
                .fileName(uniqueName)
                .originalFileName(safeOriginal)
                .fileType(file.getContentType())
                .fileSize(file.getSize())
                .filePath(uploadDir + "/" + uniqueName)
                .description(description)
                .category(category)
                .subCategory(subCategory)
                .active(true)
                .uploadedBy(user)
                .build());
        return toDTO(saved);
    }

    private static String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) return "upload";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public List<ResourceDTO> getAllResources() {
        return resourceRepository.findAllByOrderByUploadedAtDesc()
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    // ── Update metadata ───────────────────────────────────────────────────────

    @Transactional
    public ResourceDTO updateResource(Long id, Map<String, String> body) {
        Resource r = find(id);
        if (body.containsKey("title")       && body.get("title") != null)
            r.setTitle(body.get("title").trim());
        if (body.containsKey("description"))
            r.setDescription(body.get("description"));
        if (body.containsKey("category"))
            r.setCategory(body.get("category"));
        if (body.containsKey("subCategory"))
            r.setSubCategory(body.get("subCategory"));
        return toDTO(resourceRepository.save(r));
    }

    // ── Toggle active ─────────────────────────────────────────────────────────

    @Transactional
    public ResourceDTO toggleActive(Long id) {
        Resource r = find(id);
        r.setActive(!r.isActive());
        return toDTO(resourceRepository.save(r));
    }

    // ── Download ──────────────────────────────────────────────────────────────

    public org.springframework.core.io.Resource downloadFile(Long id) {
        Resource resource = find(id);
        try {
            Path path = Paths.get(resource.getFilePath());
            org.springframework.core.io.Resource res = new UrlResource(path.toUri());
            if (res.exists()) return res;
            throw new ResourceNotFoundException("File not found on disk");
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("File not found: " + e.getMessage());
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Transactional
    public void deleteResource(Long id) {
        Resource r = find(id);
        try { Files.deleteIfExists(Paths.get(r.getFilePath())); }
        catch (IOException e) { log.warn("Could not delete file: {}", r.getFilePath()); }
        resourceRepository.delete(r);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Resource find(Long id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource", id));
    }

    public ResourceDTO toDTO(Resource r) {
        String uploaderName = r.getUploadedBy() != null
                ? r.getUploadedBy().getEmail() : "—";
        return ResourceDTO.builder()
                .id(r.getId())
                .title(r.getTitle() != null ? r.getTitle() : r.getOriginalFileName())
                .originalFileName(r.getOriginalFileName())
                .fileType(r.getFileType())
                .fileSize(r.getFileSize())
                .description(r.getDescription())
                .category(r.getCategory())
                .subCategory(r.getSubCategory())
                .active(r.isActive())
                .uploadedByName(uploaderName)
                .uploadedAt(r.getUploadedAt())
                .updatedAt(r.getUpdatedAt() != null ? r.getUpdatedAt() : r.getUploadedAt())
                .build();
    }
}

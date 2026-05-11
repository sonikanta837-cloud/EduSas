package com.emp.management.service;

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
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceStorageService {

    private final ResourceRepository resourceRepository;
    private final UserRepository userRepository;

    @Value("${app.file.upload-dir}")
    private String uploadDir;

    @Transactional
    public Resource uploadFile(MultipartFile file, String description, Long uploadedByUserId) {
        User user = userRepository.findById(uploadedByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", uploadedByUserId));

        String originalName = file.getOriginalFilename();
        String uniqueName = UUID.randomUUID() + "_" + originalName;

        Path uploadPath = Paths.get(uploadDir);
        try {
            Files.createDirectories(uploadPath);
            Path filePath = uploadPath.resolve(uniqueName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BadRequestException("Failed to store file: " + e.getMessage());
        }

        return resourceRepository.save(Resource.builder()
                .fileName(uniqueName)
                .originalFileName(originalName)
                .fileType(file.getContentType())
                .fileSize(file.getSize())
                .filePath(uploadDir + "/" + uniqueName)
                .description(description)
                .uploadedBy(user)
                .build());
    }

    public org.springframework.core.io.Resource downloadFile(Long id) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource", id));
        try {
            Path path = Paths.get(resource.getFilePath());
            org.springframework.core.io.Resource res = new UrlResource(path.toUri());
            if (res.exists()) return res;
            throw new ResourceNotFoundException("File not found on disk");
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("File not found: " + e.getMessage());
        }
    }

    public List<Resource> getAllResources() {
        return resourceRepository.findAllByOrderByUploadedAtDesc();
    }

    @Transactional
    public void deleteResource(Long id) {
        Resource resource = resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource", id));
        try {
            Files.deleteIfExists(Paths.get(resource.getFilePath()));
        } catch (IOException e) {
            log.warn("Could not delete file: {}", resource.getFilePath());
        }
        resourceRepository.delete(resource);
    }
}

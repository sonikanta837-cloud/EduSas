package com.emp.management.controller;

import com.emp.management.entity.Resource;
import com.emp.management.service.ResourceStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceStorageService resourceStorageService;

    @GetMapping
    public ResponseEntity<List<Resource>> getAllResources() {
        return ResponseEntity.ok(resourceStorageService.getAllResources());
    }

    @PostMapping("/upload")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Resource> upload(@RequestParam("file") MultipartFile file,
                                            @RequestParam(required = false) String description,
                                            @RequestParam Long uploadedBy) {
        return ResponseEntity.ok(resourceStorageService.uploadFile(file, description, uploadedBy));
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable Long id) {
        org.springframework.core.io.Resource file = resourceStorageService.downloadFile(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(file);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        resourceStorageService.deleteResource(id);
        return ResponseEntity.ok("Resource deleted");
    }
}

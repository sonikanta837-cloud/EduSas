package com.emp.management.controller;

import com.emp.management.dto.ResourceDTO;
import com.emp.management.service.ResourceStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceStorageService resourceStorageService;

    @GetMapping
    public ResponseEntity<List<ResourceDTO>> getAll() {
        return ResponseEntity.ok(resourceStorageService.getAllResources());
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ResourceDTO> upload(
            @RequestParam("file")                    MultipartFile file,
            @RequestParam(required = false)          String title,
            @RequestParam(required = false)          String category,
            @RequestParam(required = false)          String subCategory,
            @RequestParam(required = false)          String description,
            Principal principal) {
        return ResponseEntity.ok(
                resourceStorageService.uploadFile(file, title, category, subCategory,
                        description, principal.getName()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ResourceDTO> update(@PathVariable Long id,
                                              @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(resourceStorageService.updateResource(id, body));
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<ResourceDTO> toggle(@PathVariable Long id) {
        return ResponseEntity.ok(resourceStorageService.toggleActive(id));
    }

    /** Serves the file inline so browsers can preview PDFs / images in a new tab. */
    @GetMapping("/view/{id}")
    public ResponseEntity<Resource> view(@PathVariable Long id) {
        Resource file = resourceStorageService.downloadFile(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + file.getFilename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(file);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Resource file = resourceStorageService.downloadFile(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.getFilename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(file);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        resourceStorageService.deleteResource(id);
        return ResponseEntity.noContent().build();
    }
}

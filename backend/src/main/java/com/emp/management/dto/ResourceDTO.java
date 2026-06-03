package com.emp.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceDTO {
    private Long   id;
    private String title;
    private String originalFileName;
    private String fileType;
    private Long   fileSize;
    private String description;
    private String category;
    private String subCategory;
    private boolean active;
    private String uploadedByName;
    private LocalDateTime uploadedAt;
    private LocalDateTime updatedAt;
}

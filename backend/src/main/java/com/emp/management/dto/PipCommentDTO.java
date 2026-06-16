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
public class PipCommentDTO {
    private Long id;
    private Long pipId;
    private Long authorId;
    private String authorName;
    private String authorRole;
    private String content;
    private LocalDateTime createdAt;
}

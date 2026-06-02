package com.emp.management.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnnouncementViewersDTO {

    private List<ViewerEntry> viewed;
    private List<ViewerEntry> notViewed;
    private int totalViewed;
    private int totalActive;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ViewerEntry {
        private Long employeeId;
        private String employeeName;
        private String department;
        private LocalDateTime viewedAt;
    }
}

package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "system_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemSetting {

    @Id
    @Column(name = "setting_key")
    private String key;

    @Column(columnDefinition = "TEXT")
    private String value;

    private LocalDateTime updatedAt;
}

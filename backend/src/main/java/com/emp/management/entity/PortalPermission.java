package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "portal_permissions", uniqueConstraints = @UniqueConstraint(columnNames = {"role", "portal_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortalPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(50)")
    private Role role;

    @Column(name = "portal_id", nullable = false)
    private String portalId;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = false;
}

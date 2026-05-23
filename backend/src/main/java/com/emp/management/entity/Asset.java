package com.emp.management.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "assets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String assetCode;

    @Column(nullable = false)
    private String assetName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetType assetType;

    private String brand;
    private String model;
    private String serialNumber;

    private LocalDate purchaseDate;
    private BigDecimal purchasePrice;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AssetStatus status = AssetStatus.AVAILABLE;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AssetCondition condition = AssetCondition.GOOD;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_employee_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private EmployeeDetails assignedEmployee;

    private LocalDate assignedDate;
    private LocalDate expectedReturnDate;
    private LocalDate actualReturnDate;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}

package com.emp.management.dto;

import com.emp.management.entity.AssetCondition;
import com.emp.management.entity.AssetStatus;
import com.emp.management.entity.AssetType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetDTO {
    private Long id;
    private String assetCode;
    private String assetName;
    private AssetType assetType;
    private String brand;
    private String model;
    private String serialNumber;
    private LocalDate purchaseDate;
    private BigDecimal purchasePrice;
    private AssetStatus status;
    private AssetCondition condition;

    // Assignment info
    private Long assignedEmployeeId;
    private String assignedEmployeeName;
    private String assignedEmployeeDepartment;
    private String assignedEmployeeCode;

    private LocalDate assignedDate;
    private LocalDate expectedReturnDate;
    private LocalDate actualReturnDate;

    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

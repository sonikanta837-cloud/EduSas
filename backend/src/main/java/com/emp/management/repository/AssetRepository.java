package com.emp.management.repository;

import com.emp.management.entity.Asset;
import com.emp.management.entity.AssetStatus;
import com.emp.management.entity.AssetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {
    List<Asset> findByStatus(AssetStatus status);
    List<Asset> findByAssetType(AssetType assetType);
    List<Asset> findByAssignedEmployeeId(Long employeeId);
    long countByStatus(AssetStatus status);
    boolean existsByAssetCode(String assetCode);

    @Query("SELECT a FROM Asset a LEFT JOIN FETCH a.assignedEmployee e LEFT JOIN FETCH e.user ORDER BY a.createdAt DESC")
    List<Asset> findAllWithEmployee();

    @Query("SELECT MAX(CAST(SUBSTRING(a.assetCode, 5) AS int)) FROM Asset a WHERE a.assetCode LIKE 'AST-%'")
    Integer findMaxAssetCodeSequence();
}

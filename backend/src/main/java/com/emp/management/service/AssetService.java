package com.emp.management.service;

import com.emp.management.dto.AssetDTO;
import com.emp.management.entity.*;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.AssetRepository;
import com.emp.management.repository.EmployeeDetailsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetRepository assetRepository;
    private final EmployeeDetailsRepository employeeDetailsRepository;

    @Transactional(readOnly = true)
    public List<AssetDTO> getAllAssets() {
        return assetRepository.findAllWithEmployee().stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AssetDTO getById(Long id) {
        return toDTO(findAsset(id));
    }

    @Transactional
    public AssetDTO create(AssetDTO dto) {
        Asset asset = Asset.builder()
                .assetCode(generateAssetCode())
                .assetName(dto.getAssetName())
                .assetType(dto.getAssetType())
                .brand(dto.getBrand())
                .model(dto.getModel())
                .serialNumber(dto.getSerialNumber())
                .purchaseDate(dto.getPurchaseDate())
                .purchasePrice(dto.getPurchasePrice())
                .status(dto.getStatus() != null ? dto.getStatus() : AssetStatus.AVAILABLE)
                .condition(dto.getCondition() != null ? dto.getCondition() : AssetCondition.GOOD)
                .notes(dto.getNotes())
                .build();
        return toDTO(assetRepository.save(asset));
    }

    @Transactional
    public AssetDTO update(Long id, AssetDTO dto) {
        Asset asset = findAsset(id);
        asset.setAssetName(dto.getAssetName());
        asset.setAssetType(dto.getAssetType());
        asset.setBrand(dto.getBrand());
        asset.setModel(dto.getModel());
        asset.setSerialNumber(dto.getSerialNumber());
        asset.setPurchaseDate(dto.getPurchaseDate());
        asset.setPurchasePrice(dto.getPurchasePrice());
        asset.setCondition(dto.getCondition());
        asset.setNotes(dto.getNotes());
        if (asset.getAssignedEmployee() == null) {
            asset.setStatus(dto.getStatus() != null ? dto.getStatus() : asset.getStatus());
        }
        return toDTO(assetRepository.save(asset));
    }

    @Transactional
    public void delete(Long id) {
        assetRepository.delete(findAsset(id));
    }

    @Transactional
    public AssetDTO assign(Long assetId, Long employeeId, LocalDate assignedDate, LocalDate expectedReturnDate) {
        Asset asset = findAsset(assetId);
        if (asset.getStatus() != AssetStatus.AVAILABLE) {
            throw new BadRequestException("Asset is not available for assignment");
        }
        EmployeeDetails employee = employeeDetailsRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));
        asset.setAssignedEmployee(employee);
        asset.setStatus(AssetStatus.ASSIGNED);
        asset.setAssignedDate(assignedDate != null ? assignedDate : LocalDate.now());
        asset.setExpectedReturnDate(expectedReturnDate);
        asset.setActualReturnDate(null);
        return toDTO(assetRepository.save(asset));
    }

    @Transactional
    public AssetDTO returnAsset(Long assetId) {
        Asset asset = findAsset(assetId);
        if (asset.getStatus() != AssetStatus.ASSIGNED) {
            throw new BadRequestException("Asset is not currently assigned");
        }
        asset.setAssignedEmployee(null);
        asset.setStatus(AssetStatus.AVAILABLE);
        asset.setActualReturnDate(LocalDate.now());
        asset.setAssignedDate(null);
        asset.setExpectedReturnDate(null);
        return toDTO(assetRepository.save(asset));
    }

    @Transactional
    public AssetDTO updateStatus(Long assetId, AssetStatus status) {
        Asset asset = findAsset(assetId);
        if (status == AssetStatus.AVAILABLE && asset.getAssignedEmployee() != null) {
            asset.setAssignedEmployee(null);
            asset.setAssignedDate(null);
            asset.setExpectedReturnDate(null);
        }
        asset.setStatus(status);
        return toDTO(assetRepository.save(asset));
    }

    public Map<String, Long> getStats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("total",            assetRepository.count());
        stats.put("available",        assetRepository.countByStatus(AssetStatus.AVAILABLE));
        stats.put("assigned",         assetRepository.countByStatus(AssetStatus.ASSIGNED));
        stats.put("underMaintenance", assetRepository.countByStatus(AssetStatus.UNDER_MAINTENANCE));
        stats.put("retired",          assetRepository.countByStatus(AssetStatus.RETIRED));
        return stats;
    }

    private synchronized String generateAssetCode() {
        Integer max = assetRepository.findMaxAssetCodeSequence();
        int next = (max != null ? max : 0) + 1;
        return String.format("AST-%04d", next);
    }

    private Asset findAsset(Long id) {
        return assetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", id));
    }

    public AssetDTO toDTO(Asset a) {
        EmployeeDetails emp = a.getAssignedEmployee();
        return AssetDTO.builder()
                .id(a.getId())
                .assetCode(a.getAssetCode())
                .assetName(a.getAssetName())
                .assetType(a.getAssetType())
                .brand(a.getBrand())
                .model(a.getModel())
                .serialNumber(a.getSerialNumber())
                .purchaseDate(a.getPurchaseDate())
                .purchasePrice(a.getPurchasePrice())
                .status(a.getStatus())
                .condition(a.getCondition())
                .assignedEmployeeId(emp != null ? emp.getId() : null)
                .assignedEmployeeName(emp != null ? emp.getFullName() : null)
                .assignedEmployeeDepartment(emp != null ? emp.getDepartment() : null)
                .assignedEmployeeCode(emp != null ? emp.getEmployeeCode() : null)
                .assignedDate(a.getAssignedDate())
                .expectedReturnDate(a.getExpectedReturnDate())
                .actualReturnDate(a.getActualReturnDate())
                .notes(a.getNotes())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}

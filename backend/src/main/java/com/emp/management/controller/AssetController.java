package com.emp.management.controller;

import com.emp.management.dto.AssetDTO;
import com.emp.management.entity.AssetStatus;
import com.emp.management.service.AssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assets")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    @GetMapping
    public ResponseEntity<List<AssetDTO>> getAll() {
        return ResponseEntity.ok(assetService.getAllAssets());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssetDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(assetService.getById(id));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats() {
        return ResponseEntity.ok(assetService.getStats());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AssetDTO> create(@RequestBody AssetDTO dto) {
        return ResponseEntity.ok(assetService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AssetDTO> update(@PathVariable Long id, @RequestBody AssetDTO dto) {
        return ResponseEntity.ok(assetService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        assetService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AssetDTO> assign(@PathVariable Long id,
                                            @RequestParam Long employeeId,
                                            @RequestParam(required = false) String assignedDate,
                                            @RequestParam(required = false) String expectedReturnDate) {
        LocalDate ad  = assignedDate       != null ? LocalDate.parse(assignedDate)       : null;
        LocalDate erd = expectedReturnDate != null ? LocalDate.parse(expectedReturnDate) : null;
        return ResponseEntity.ok(assetService.assign(id, employeeId, ad, erd));
    }

    @PatchMapping("/{id}/return")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AssetDTO> returnAsset(@PathVariable Long id) {
        return ResponseEntity.ok(assetService.returnAsset(id));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AssetDTO> updateStatus(@PathVariable Long id,
                                                  @RequestParam AssetStatus status) {
        return ResponseEntity.ok(assetService.updateStatus(id, status));
    }
}

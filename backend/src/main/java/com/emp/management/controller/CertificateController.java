package com.emp.management.controller;

import com.emp.management.dto.CertificateDTO;
import com.emp.management.service.CertificateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;

    @GetMapping("/my")
    public ResponseEntity<List<CertificateDTO>> getMyCertificates(Authentication authentication) {
        return ResponseEntity.ok(certificateService.getMyCertificates(authentication.getName()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CertificateDTO>> getAllCertificates() {
        return ResponseEntity.ok(certificateService.getAllCertificates());
    }
}

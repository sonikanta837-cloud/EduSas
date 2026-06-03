package com.emp.management.controller;

import com.emp.management.entity.FAQ;
import com.emp.management.service.FaqService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/faqs")
@RequiredArgsConstructor
public class FaqController {

    private final FaqService faqService;

    @GetMapping
    public ResponseEntity<List<FAQ>> getAll(
            @RequestParam(defaultValue = "false") boolean all,
            Principal principal) {
        boolean adminView = all && principal != null;
        return ResponseEntity.ok(faqService.getAll(adminView));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<FAQ> create(@RequestBody Map<String, Object> body,
                                      Principal principal) {
        return ResponseEntity.ok(faqService.create(body, principal.getName()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<FAQ> update(@PathVariable Long id,
                                      @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(faqService.update(id, body));
    }

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<FAQ> toggle(@PathVariable Long id) {
        return ResponseEntity.ok(faqService.toggleActive(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        faqService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

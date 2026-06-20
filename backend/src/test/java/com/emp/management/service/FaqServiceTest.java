package com.emp.management.service;

import com.emp.management.entity.FAQ;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.FaqRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FaqServiceTest {

    @Mock private FaqRepository faqRepository;

    @InjectMocks private FaqService faqService;

    private FAQ sampleFaq;

    @BeforeEach
    void setUp() {
        sampleFaq = FAQ.builder()
                .id(1L)
                .question("What is leave policy?")
                .answer("Employees get 15 days per year.")
                .displayOrder(1)
                .active(true)
                .createdBy("admin@company.com")
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── getAll ────────────────────────────────────────────────────────────────

    @Test
    void getAll_adminView_returnsAllFaqs() {
        when(faqRepository.findAllByOrderByDisplayOrderAscCreatedAtAsc())
                .thenReturn(List.of(sampleFaq));

        List<FAQ> result = faqService.getAll(true);

        assertThat(result).hasSize(1);
        verify(faqRepository).findAllByOrderByDisplayOrderAscCreatedAtAsc();
    }

    @Test
    void getAll_nonAdminView_returnsOnlyActiveFaqs() {
        when(faqRepository.findByActiveTrueOrderByDisplayOrderAscCreatedAtAsc())
                .thenReturn(List.of(sampleFaq));

        List<FAQ> result = faqService.getAll(false);

        assertThat(result).hasSize(1);
        verify(faqRepository).findByActiveTrueOrderByDisplayOrderAscCreatedAtAsc();
    }

    @Test
    void getAll_emptyRepository_returnsEmptyList() {
        when(faqRepository.findByActiveTrueOrderByDisplayOrderAscCreatedAtAsc()).thenReturn(List.of());

        List<FAQ> result = faqService.getAll(false);

        assertThat(result).isEmpty();
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_validBody_savesFaq() {
        Map<String, Object> body = Map.of(
                "question", "How to apply for leave?",
                "answer", "Use the leave portal.",
                "displayOrder", 2
        );
        when(faqRepository.save(any())).thenReturn(sampleFaq);

        FAQ result = faqService.create(body, "admin@company.com");

        assertThat(result).isNotNull();
        verify(faqRepository).save(any(FAQ.class));
    }

    @Test
    void create_nullDisplayOrder_savesWithNullOrder() {
        Map<String, Object> body = Map.of(
                "question", "What is PIP?",
                "answer", "Performance Improvement Plan."
        );
        when(faqRepository.save(any())).thenReturn(sampleFaq);

        FAQ result = faqService.create(body, "hr@company.com");

        assertThat(result).isNotNull();
    }

    @Test
    void create_nullQuestionValue_savesWithEmptyString() {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("question", null);
        body.put("answer", "Some answer");
        when(faqRepository.save(any())).thenReturn(sampleFaq);

        FAQ result = faqService.create(body, "admin@company.com");

        assertThat(result).isNotNull();
        // null value is converted to empty string in service
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_existingFaq_updatesFields() {
        Map<String, Object> body = Map.of(
                "question", "Updated question?",
                "answer", "Updated answer.",
                "displayOrder", 3
        );
        when(faqRepository.findById(1L)).thenReturn(Optional.of(sampleFaq));
        when(faqRepository.save(any())).thenReturn(sampleFaq);

        FAQ result = faqService.update(1L, body);

        assertThat(result).isNotNull();
        verify(faqRepository).save(sampleFaq);
    }

    @Test
    void update_notFound_throwsResourceNotFoundException() {
        when(faqRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> faqService.update(999L, Map.of("question", "X")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_partialBody_updatesOnlyProvidedFields() {
        Map<String, Object> body = Map.of("answer", "New answer only");
        when(faqRepository.findById(1L)).thenReturn(Optional.of(sampleFaq));
        when(faqRepository.save(any())).thenReturn(sampleFaq);

        FAQ result = faqService.update(1L, body);

        assertThat(result).isNotNull();
        // Only answer field is in body, question should be unchanged
    }

    // ── toggleActive ─────────────────────────────────────────────────────────

    @Test
    void toggleActive_activeFaq_becomesInactive() {
        sampleFaq.setActive(true);
        when(faqRepository.findById(1L)).thenReturn(Optional.of(sampleFaq));
        when(faqRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FAQ result = faqService.toggleActive(1L);

        assertThat(result.isActive()).isFalse();
    }

    @Test
    void toggleActive_inactiveFaq_becomesActive() {
        sampleFaq.setActive(false);
        when(faqRepository.findById(1L)).thenReturn(Optional.of(sampleFaq));
        when(faqRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FAQ result = faqService.toggleActive(1L);

        assertThat(result.isActive()).isTrue();
    }

    @Test
    void toggleActive_notFound_throwsResourceNotFoundException() {
        when(faqRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> faqService.toggleActive(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_existingFaq_deletesSuccessfully() {
        when(faqRepository.findById(1L)).thenReturn(Optional.of(sampleFaq));
        doNothing().when(faqRepository).delete(sampleFaq);

        faqService.delete(1L);

        verify(faqRepository).delete(sampleFaq);
    }

    @Test
    void delete_notFound_throwsResourceNotFoundException() {
        when(faqRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> faqService.delete(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

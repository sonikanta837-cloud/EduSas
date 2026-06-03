package com.emp.management.service;

import com.emp.management.entity.FAQ;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.FaqRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FaqServiceTest {

    @Mock private FaqRepository faqRepository;
    @InjectMocks private FaqService service;

    private FAQ activeFaq;
    private FAQ inactiveFaq;

    @BeforeEach
    void setUp() {
        activeFaq = FAQ.builder()
                .id(1L)
                .question("What is the leave policy?")
                .answer("Employees get 21 days of paid leave per year.")
                .active(true)
                .displayOrder(1)
                .createdBy("admin@company.com")
                .createdAt(LocalDateTime.now())
                .build();

        inactiveFaq = FAQ.builder()
                .id(2L)
                .question("How do I apply for reimbursement?")
                .answer("Submit a reimbursement form via the portal.")
                .active(false)
                .displayOrder(2)
                .createdBy("hr@company.com")
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ── getAll ────────────────────────────────────────────────────────────────

    @Test
    void getAll_adminView_true_returnsAllFaqs() {
        when(faqRepository.findAllByOrderByDisplayOrderAscCreatedAtAsc())
                .thenReturn(List.of(activeFaq, inactiveFaq));

        List<FAQ> result = service.getAll(true);

        assertThat(result).hasSize(2);
        verify(faqRepository).findAllByOrderByDisplayOrderAscCreatedAtAsc();
        verify(faqRepository, never()).findByActiveTrueOrderByDisplayOrderAscCreatedAtAsc();
    }

    @Test
    void getAll_adminView_false_returnsOnlyActiveFaqs() {
        when(faqRepository.findByActiveTrueOrderByDisplayOrderAscCreatedAtAsc())
                .thenReturn(List.of(activeFaq));

        List<FAQ> result = service.getAll(false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isActive()).isTrue();
        verify(faqRepository).findByActiveTrueOrderByDisplayOrderAscCreatedAtAsc();
        verify(faqRepository, never()).findAllByOrderByDisplayOrderAscCreatedAtAsc();
    }

    @Test
    void getAll_orderedByDisplayOrderThenCreatedAt() {
        FAQ faq3 = FAQ.builder().id(3L).question("Q3").answer("A3")
                .active(true).displayOrder(3)
                .createdAt(LocalDateTime.now()).build();
        when(faqRepository.findAllByOrderByDisplayOrderAscCreatedAtAsc())
                .thenReturn(List.of(activeFaq, inactiveFaq, faq3));

        List<FAQ> result = service.getAll(true);

        assertThat(result.get(0).getDisplayOrder()).isEqualTo(1);
        assertThat(result.get(1).getDisplayOrder()).isEqualTo(2);
        assertThat(result.get(2).getDisplayOrder()).isEqualTo(3);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_savesNewFaqAndReturnsIt() {
        Map<String, Object> body = Map.of(
                "question", "How do I request leave?",
                "answer", "Go to the Leaves module and submit a request.",
                "displayOrder", 1
        );
        when(faqRepository.save(any(FAQ.class))).thenReturn(activeFaq);

        FAQ result = service.create(body, "admin@company.com");

        assertThat(result).isNotNull();
        ArgumentCaptor<FAQ> captor = ArgumentCaptor.forClass(FAQ.class);
        verify(faqRepository).save(captor.capture());
        FAQ saved = captor.getValue();
        assertThat(saved.getQuestion()).isEqualTo("How do I request leave?");
        assertThat(saved.getAnswer()).isEqualTo("Go to the Leaves module and submit a request.");
        assertThat(saved.getDisplayOrder()).isEqualTo(1);
        assertThat(saved.getCreatedBy()).isEqualTo("admin@company.com");
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void create_withNullDisplayOrder_storesNull() {
        Map<String, Object> body = Map.of(
                "question", "Q without order",
                "answer", "Answer"
        );
        when(faqRepository.save(any(FAQ.class))).thenAnswer(inv -> inv.getArgument(0));

        FAQ result = service.create(body, "hr@company.com");

        assertThat(result.getDisplayOrder()).isNull();
    }

    @Test
    void create_withNonNumericDisplayOrder_storesNull() {
        Map<String, Object> body = Map.of(
                "question", "Q", "answer", "A", "displayOrder", "not-a-number"
        );
        when(faqRepository.save(any(FAQ.class))).thenAnswer(inv -> inv.getArgument(0));

        FAQ result = service.create(body, "admin@company.com");
        assertThat(result.getDisplayOrder()).isNull();
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_updatesAllFields() {
        when(faqRepository.findById(1L)).thenReturn(Optional.of(activeFaq));
        when(faqRepository.save(any(FAQ.class))).thenReturn(activeFaq);

        Map<String, Object> body = Map.of(
                "question", "Updated question?",
                "answer", "Updated answer.",
                "displayOrder", 5
        );
        FAQ result = service.update(1L, body);

        assertThat(result).isNotNull();
        assertThat(activeFaq.getQuestion()).isEqualTo("Updated question?");
        assertThat(activeFaq.getAnswer()).isEqualTo("Updated answer.");
        assertThat(activeFaq.getDisplayOrder()).isEqualTo(5);
    }

    @Test
    void update_partialBody_onlyUpdatesProvidedFields() {
        activeFaq.setQuestion("Original Q");
        activeFaq.setAnswer("Original A");
        when(faqRepository.findById(1L)).thenReturn(Optional.of(activeFaq));
        when(faqRepository.save(any(FAQ.class))).thenAnswer(inv -> inv.getArgument(0));

        FAQ result = service.update(1L, Map.of("answer", "New Answer"));

        assertThat(result.getQuestion()).isEqualTo("Original Q");
        assertThat(result.getAnswer()).isEqualTo("New Answer");
    }

    @Test
    void update_notFound_throwsResourceNotFoundException() {
        when(faqRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, Map.of("question", "Q")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── toggleActive ──────────────────────────────────────────────────────────

    @Test
    void toggleActive_flipsTrueToFalse() {
        activeFaq.setActive(true);
        when(faqRepository.findById(1L)).thenReturn(Optional.of(activeFaq));
        when(faqRepository.save(any(FAQ.class))).thenAnswer(inv -> inv.getArgument(0));

        FAQ result = service.toggleActive(1L);

        assertThat(activeFaq.isActive()).isFalse();
        verify(faqRepository).save(activeFaq);
    }

    @Test
    void toggleActive_flipsFalseToTrue() {
        inactiveFaq.setActive(false);
        when(faqRepository.findById(2L)).thenReturn(Optional.of(inactiveFaq));
        when(faqRepository.save(any(FAQ.class))).thenAnswer(inv -> inv.getArgument(0));

        service.toggleActive(2L);

        assertThat(inactiveFaq.isActive()).isTrue();
    }

    @Test
    void toggleActive_notFound_throwsResourceNotFoundException() {
        when(faqRepository.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggleActive(77L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_removesExistingFaq() {
        when(faqRepository.findById(1L)).thenReturn(Optional.of(activeFaq));

        service.delete(1L);

        verify(faqRepository).delete(activeFaq);
    }

    @Test
    void delete_notFound_throwsResourceNotFoundException() {
        when(faqRepository.findById(88L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(88L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── edge cases ────────────────────────────────────────────────────────────

    @Test
    void getAll_emptyRepository_returnsEmptyList() {
        when(faqRepository.findByActiveTrueOrderByDisplayOrderAscCreatedAtAsc())
                .thenReturn(List.of());

        List<FAQ> result = service.getAll(false);
        assertThat(result).isEmpty();
    }

    @Test
    void create_specialCharactersInQuestion_storedCorrectly() {
        Map<String, Object> body = Map.of(
                "question", "What is <b>overtime</b> & how is it calculated?",
                "answer", "Over 8 hrs/day = overtime"
        );
        when(faqRepository.save(any(FAQ.class))).thenAnswer(inv -> inv.getArgument(0));

        FAQ result = service.create(body, "admin@company.com");
        assertThat(result.getQuestion()).contains("<b>overtime</b>");
    }
}

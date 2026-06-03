package com.emp.management.service;

import com.emp.management.entity.FAQ;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.FaqRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FaqService {

    private final FaqRepository faqRepository;

    public List<FAQ> getAll(boolean adminView) {
        return adminView
                ? faqRepository.findAllByOrderByDisplayOrderAscCreatedAtAsc()
                : faqRepository.findByActiveTrueOrderByDisplayOrderAscCreatedAtAsc();
    }

    @Transactional
    public FAQ create(Map<String, Object> body, String createdBy) {
        return faqRepository.save(FAQ.builder()
                .question(str(body, "question"))
                .answer(str(body, "answer"))
                .displayOrder(intVal(body, "displayOrder"))
                .active(true)
                .createdBy(createdBy)
                .build());
    }

    @Transactional
    public FAQ update(Long id, Map<String, Object> body) {
        FAQ faq = find(id);
        if (body.containsKey("question"))     faq.setQuestion(str(body, "question"));
        if (body.containsKey("answer"))       faq.setAnswer(str(body, "answer"));
        if (body.containsKey("displayOrder")) faq.setDisplayOrder(intVal(body, "displayOrder"));
        return faqRepository.save(faq);
    }

    @Transactional
    public FAQ toggleActive(Long id) {
        FAQ faq = find(id);
        faq.setActive(!faq.isActive());
        return faqRepository.save(faq);
    }

    @Transactional
    public void delete(Long id) {
        faqRepository.delete(find(id));
    }

    private FAQ find(Long id) {
        return faqRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FAQ", id));
    }

    private String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? "" : v.toString().trim();
    }

    private Integer intVal(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) return null;
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }
}

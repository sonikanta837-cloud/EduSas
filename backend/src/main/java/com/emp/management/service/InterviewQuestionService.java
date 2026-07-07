package com.emp.management.service;

import com.emp.management.dto.InterviewQuestionDTO;
import com.emp.management.entity.*;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.InterviewQuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewQuestionService {

    private final InterviewQuestionRepository questionRepo;

    // ── Mapping ───────────────────────────────────────────────────────────────

    private InterviewQuestionDTO toDTO(InterviewQuestion q) {
        return InterviewQuestionDTO.builder()
                .id(q.getId())
                .questionText(q.getQuestionText())
                .category(q.getCategory())
                .technology(q.getTechnology())
                .difficulty(q.getDifficulty())
                .questionType(q.getQuestionType())
                .optionA(q.getOptionA())
                .optionB(q.getOptionB())
                .optionC(q.getOptionC())
                .optionD(q.getOptionD())
                .correctAnswer(q.getCorrectAnswer())
                .expectedKeywords(q.getExpectedKeywords())
                .marks(q.getMarks())
                .status(q.getStatus())
                .createdBy(q.getCreatedBy())
                .createdAt(q.getCreatedAt())
                .updatedAt(q.getUpdatedAt())
                .build();
    }

    private void applyFromDTO(InterviewQuestion q, InterviewQuestionDTO dto) {
        if (dto.getQuestionText() == null || dto.getQuestionText().isBlank())
            throw new BadRequestException("Question text is required");

        q.setQuestionText(dto.getQuestionText().trim());
        q.setCategory(dto.getCategory() != null ? dto.getCategory().trim() : null);
        q.setTechnology(dto.getTechnology() != null ? dto.getTechnology().trim() : null);
        q.setDifficulty(dto.getDifficulty() != null ? dto.getDifficulty() : QuestionDifficulty.MEDIUM);
        q.setQuestionType(dto.getQuestionType() != null ? dto.getQuestionType() : QuestionType.TEXT);
        q.setOptionA(dto.getOptionA());
        q.setOptionB(dto.getOptionB());
        q.setOptionC(dto.getOptionC());
        q.setOptionD(dto.getOptionD());
        q.setCorrectAnswer(dto.getCorrectAnswer());
        q.setExpectedKeywords(dto.getExpectedKeywords());
        q.setMarks(dto.getMarks() != null ? dto.getMarks() : 5);

        if (q.getQuestionType() == QuestionType.MCQ) {
            if (q.getOptionA() == null || q.getOptionA().isBlank() ||
                q.getOptionB() == null || q.getOptionB().isBlank())
                throw new BadRequestException("MCQ questions must have at least Option A and Option B");
        }
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    public List<InterviewQuestionDTO> getAll() {
        return questionRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDTO).collect(Collectors.toList());
    }

    public InterviewQuestionDTO getById(Long id) {
        return toDTO(questionRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + id)));
    }

    @Transactional
    public InterviewQuestionDTO create(InterviewQuestionDTO dto, String createdBy) {
        InterviewQuestion q = InterviewQuestion.builder().createdBy(createdBy).build();
        applyFromDTO(q, dto);
        return toDTO(questionRepo.save(q));
    }

    @Transactional
    public InterviewQuestionDTO update(Long id, InterviewQuestionDTO dto) {
        InterviewQuestion q = questionRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + id));
        applyFromDTO(q, dto);
        return toDTO(questionRepo.save(q));
    }

    @Transactional
    public void delete(Long id) {
        if (!questionRepo.existsById(id))
            throw new ResourceNotFoundException("Question not found: " + id);
        questionRepo.deleteById(id);
    }

    @Transactional
    public InterviewQuestionDTO toggleStatus(Long id) {
        InterviewQuestion q = questionRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found: " + id));
        q.setStatus(q.getStatus() == QuestionStatus.ACTIVE ? QuestionStatus.INACTIVE : QuestionStatus.ACTIVE);
        return toDTO(questionRepo.save(q));
    }

    // ── Metadata ─────────────────────────────────────────────────────────────

    public List<String> getTechnologies() {
        return questionRepo.findDistinctTechnologies();
    }

    public List<String> getCategories() {
        return questionRepo.findDistinctCategories();
    }

    public Map<String, Long> getStats() {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("total",    questionRepo.count());
        stats.put("active",   questionRepo.countByStatus(QuestionStatus.ACTIVE));
        stats.put("inactive", questionRepo.countByStatus(QuestionStatus.INACTIVE));
        return stats;
    }

    // ── Excel Import ─────────────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> importFromExcel(MultipartFile file, String createdBy) {
        if (file.isEmpty()) throw new BadRequestException("Upload file is empty");
        String fname = file.getOriginalFilename();
        if (fname == null || !fname.toLowerCase().endsWith(".xlsx"))
            throw new BadRequestException("Only .xlsx files are supported");

        int saved  = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();

        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) throw new BadRequestException("Excel file has no sheets");

            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null || isRowBlank(row)) continue;

                try {
                    String questionText = cell(row, 0);
                    if (questionText.isBlank()) { errors.add("Row " + (rowIdx + 1) + ": Question text is empty"); failed++; continue; }

                    QuestionDifficulty diff = parseDifficulty(cell(row, 3), rowIdx + 1, errors);
                    if (diff == null) { failed++; continue; }

                    QuestionType type = parseType(cell(row, 4), rowIdx + 1, errors);
                    if (type == null) { failed++; continue; }

                    InterviewQuestion q = InterviewQuestion.builder()
                            .questionText(questionText.trim())
                            .category(cell(row, 1))
                            .technology(cell(row, 2))
                            .difficulty(diff)
                            .questionType(type)
                            .optionA(cell(row, 5))
                            .optionB(cell(row, 6))
                            .optionC(cell(row, 7))
                            .optionD(cell(row, 8))
                            .correctAnswer(cell(row, 9).toUpperCase().replaceAll("[^ABCD]", ""))
                            .expectedKeywords(cell(row, 10))
                            .marks(parseMarks(cell(row, 11)))
                            .status(QuestionStatus.ACTIVE)
                            .createdBy(createdBy)
                            .build();

                    questionRepo.save(q);
                    saved++;
                } catch (Exception e) {
                    errors.add("Row " + (rowIdx + 1) + ": " + e.getMessage());
                    failed++;
                }
            }
        } catch (IOException e) {
            throw new BadRequestException("Failed to read Excel file: " + e.getMessage());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("saved", saved);
        result.put("failed", failed);
        result.put("errors", errors);
        return result;
    }

    // ── Excel Export ─────────────────────────────────────────────────────────

    public byte[] exportToExcel() throws IOException {
        List<InterviewQuestion> questions = questionRepo.findAllByOrderByCreatedAtDesc();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Questions");

            String[] headers = { "Question", "Category", "Technology", "Difficulty", "Type",
                                  "Option A", "Option B", "Option C", "Option D",
                                  "Correct Answer", "Expected Keywords", "Marks", "Status" };
            Row hdr = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) hdr.createCell(i).setCellValue(headers[i]);

            int r = 1;
            for (InterviewQuestion q : questions) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(q.getQuestionText());
                row.createCell(1).setCellValue(q.getCategory() != null ? q.getCategory() : "");
                row.createCell(2).setCellValue(q.getTechnology() != null ? q.getTechnology() : "");
                row.createCell(3).setCellValue(q.getDifficulty().name());
                row.createCell(4).setCellValue(q.getQuestionType().name());
                row.createCell(5).setCellValue(q.getOptionA() != null ? q.getOptionA() : "");
                row.createCell(6).setCellValue(q.getOptionB() != null ? q.getOptionB() : "");
                row.createCell(7).setCellValue(q.getOptionC() != null ? q.getOptionC() : "");
                row.createCell(8).setCellValue(q.getOptionD() != null ? q.getOptionD() : "");
                row.createCell(9).setCellValue(q.getCorrectAnswer() != null ? q.getCorrectAnswer() : "");
                row.createCell(10).setCellValue(q.getExpectedKeywords() != null ? q.getExpectedKeywords() : "");
                row.createCell(11).setCellValue(q.getMarks() != null ? q.getMarks() : 5);
                row.createCell(12).setCellValue(q.getStatus().name());
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String cell(Row row, int col) {
        Cell c = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (c == null) return "";
        return switch (c.getCellType()) {
            case STRING  -> c.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((int) c.getNumericCellValue());
            case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
            default      -> "";
        };
    }

    private boolean isRowBlank(Row row) {
        for (int i = 0; i <= 4; i++) {
            String v = cell(row, i);
            if (!v.isBlank()) return false;
        }
        return true;
    }

    private QuestionDifficulty parseDifficulty(String raw, int rowNum, List<String> errors) {
        try {
            return QuestionDifficulty.valueOf(raw.toUpperCase().trim());
        } catch (Exception e) {
            errors.add("Row " + rowNum + ": Invalid difficulty '" + raw + "' — must be EASY, MEDIUM, or HARD");
            return null;
        }
    }

    private QuestionType parseType(String raw, int rowNum, List<String> errors) {
        try {
            return QuestionType.valueOf(raw.toUpperCase().trim());
        } catch (Exception e) {
            errors.add("Row " + rowNum + ": Invalid type '" + raw + "' — must be TEXT or MCQ");
            return null;
        }
    }

    private int parseMarks(String raw) {
        try { return Integer.parseInt(raw.trim()); } catch (Exception e) { return 5; }
    }
}

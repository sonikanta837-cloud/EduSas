package com.emp.management.service;

import com.emp.management.dto.InterviewQuestionDTO;
import com.emp.management.entity.InterviewQuestion;
import com.emp.management.entity.QuestionDifficulty;
import com.emp.management.entity.QuestionStatus;
import com.emp.management.entity.QuestionType;
import com.emp.management.exception.BadRequestException;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.InterviewQuestionRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterviewQuestionServiceTest {

    @Mock private InterviewQuestionRepository questionRepo;

    @InjectMocks private InterviewQuestionService service;

    // ── create ─────────────────────────────────────────────────────────────────

    @Test
    void create_validTextQuestion_savesAndReturnsDTO() {
        InterviewQuestionDTO dto = InterviewQuestionDTO.builder()
                .questionText("Explain dependency injection").technology("Spring").category("Backend").build();
        when(questionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InterviewQuestionDTO result = service.create(dto, "hr@company.com");

        assertThat(result.getQuestionText()).isEqualTo("Explain dependency injection");
        assertThat(result.getDifficulty()).isEqualTo(QuestionDifficulty.MEDIUM); // default
        assertThat(result.getQuestionType()).isEqualTo(QuestionType.TEXT); // default
        assertThat(result.getMarks()).isEqualTo(5); // default
    }

    @Test
    void create_blankQuestionText_throwsBadRequestException() {
        InterviewQuestionDTO dto = InterviewQuestionDTO.builder().questionText("   ").build();

        assertThatThrownBy(() -> service.create(dto, "hr@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Question text is required");
    }

    @Test
    void create_mcqMissingOptionB_throwsBadRequestException() {
        InterviewQuestionDTO dto = InterviewQuestionDTO.builder()
                .questionText("Pick one").questionType(QuestionType.MCQ).optionA("A only").build();

        assertThatThrownBy(() -> service.create(dto, "hr@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at least Option A and Option B");
    }

    @Test
    void create_mcqWithOptionAAndB_succeeds() {
        InterviewQuestionDTO dto = InterviewQuestionDTO.builder()
                .questionText("Pick one").questionType(QuestionType.MCQ)
                .optionA("Choice A").optionB("Choice B").correctAnswer("A").build();
        when(questionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InterviewQuestionDTO result = service.create(dto, "hr@company.com");

        assertThat(result.getOptionA()).isEqualTo("Choice A");
    }

    @Test
    void create_explicitDifficultyAndMarks_arePreserved() {
        InterviewQuestionDTO dto = InterviewQuestionDTO.builder()
                .questionText("Hard one").difficulty(QuestionDifficulty.HARD).marks(10).build();
        when(questionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InterviewQuestionDTO result = service.create(dto, "hr@company.com");

        assertThat(result.getDifficulty()).isEqualTo(QuestionDifficulty.HARD);
        assertThat(result.getMarks()).isEqualTo(10);
    }

    // ── update ─────────────────────────────────────────────────────────────────

    @Test
    void update_existingQuestion_updatesFields() {
        InterviewQuestion existing = InterviewQuestion.builder().id(1L).questionText("Old text").build();
        InterviewQuestionDTO dto = InterviewQuestionDTO.builder().questionText("New text").build();

        when(questionRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(questionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InterviewQuestionDTO result = service.update(1L, dto);

        assertThat(result.getQuestionText()).isEqualTo("New text");
    }

    @Test
    void update_notFound_throwsResourceNotFoundException() {
        when(questionRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, InterviewQuestionDTO.builder().questionText("x").build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── delete ─────────────────────────────────────────────────────────────────

    @Test
    void delete_existing_deletesById() {
        when(questionRepo.existsById(1L)).thenReturn(true);

        service.delete(1L);

        verify(questionRepo).deleteById(1L);
    }

    @Test
    void delete_notFound_throwsResourceNotFoundException() {
        when(questionRepo.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(questionRepo, never()).deleteById(any());
    }

    // ── toggleStatus ───────────────────────────────────────────────────────────

    @Test
    void toggleStatus_activeQuestion_becomesInactive() {
        InterviewQuestion q = InterviewQuestion.builder().id(1L).questionText("q").status(QuestionStatus.ACTIVE).build();
        when(questionRepo.findById(1L)).thenReturn(Optional.of(q));
        when(questionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InterviewQuestionDTO result = service.toggleStatus(1L);

        assertThat(result.getStatus()).isEqualTo(QuestionStatus.INACTIVE);
    }

    @Test
    void toggleStatus_inactiveQuestion_becomesActive() {
        InterviewQuestion q = InterviewQuestion.builder().id(1L).questionText("q").status(QuestionStatus.INACTIVE).build();
        when(questionRepo.findById(1L)).thenReturn(Optional.of(q));
        when(questionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InterviewQuestionDTO result = service.toggleStatus(1L);

        assertThat(result.getStatus()).isEqualTo(QuestionStatus.ACTIVE);
    }

    @Test
    void toggleStatus_notFound_throwsResourceNotFoundException() {
        when(questionRepo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.toggleStatus(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── metadata ───────────────────────────────────────────────────────────────

    @Test
    void getTechnologies_delegatesToRepository() {
        when(questionRepo.findDistinctTechnologies()).thenReturn(List.of("Java", "React"));

        assertThat(service.getTechnologies()).containsExactly("Java", "React");
    }

    @Test
    void getCategories_delegatesToRepository() {
        when(questionRepo.findDistinctCategories()).thenReturn(List.of("Backend", "Frontend"));

        assertThat(service.getCategories()).containsExactly("Backend", "Frontend");
    }

    @Test
    void getStats_returnsTotalActiveInactiveCounts() {
        when(questionRepo.count()).thenReturn(50L);
        when(questionRepo.countByStatus(QuestionStatus.ACTIVE)).thenReturn(40L);
        when(questionRepo.countByStatus(QuestionStatus.INACTIVE)).thenReturn(10L);

        Map<String, Long> stats = service.getStats();

        assertThat(stats).containsEntry("total", 50L).containsEntry("active", 40L).containsEntry("inactive", 10L);
    }

    // ── Excel import ───────────────────────────────────────────────────────────

    @Test
    void importFromExcel_emptyFile_throwsBadRequestException() {
        MockMultipartFile file = new MockMultipartFile("file", "questions.xlsx", "application/vnd.ms-excel", new byte[0]);

        assertThatThrownBy(() -> service.importFromExcel(file, "hr@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void importFromExcel_nonXlsxFilename_throwsBadRequestException() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "questions.csv", "text/csv", "data".getBytes());

        assertThatThrownBy(() -> service.importFromExcel(file, "hr@company.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only .xlsx files are supported");
    }

    @Test
    void importFromExcel_validAndInvalidRows_reportsCountsAndErrors() throws Exception {
        byte[] xlsx = buildWorkbook(
                row("Explain SOLID", "Backend", "Java", "MEDIUM", "TEXT", "", "", "", "", "", "keywords", "5"),
                row("Pick correct", "Backend", "Java", "EASY", "MCQ", "A", "B", "C", "D", "b", "", "3"),
                row("Bad difficulty row", "Backend", "Java", "SUPERHARD", "TEXT", "", "", "", "", "", "", "5")
        );
        MockMultipartFile file = new MockMultipartFile("file", "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);
        when(questionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.importFromExcel(file, "hr@company.com");

        assertThat(result.get("saved")).isEqualTo(2);
        assertThat(result.get("failed")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<String> errors = (List<String>) result.get("errors");
        assertThat(errors).anyMatch(e -> e.contains("Invalid difficulty"));

        ArgumentCaptor<InterviewQuestion> captor = ArgumentCaptor.forClass(InterviewQuestion.class);
        verify(questionRepo, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).getCorrectAnswer()).isEqualTo("B"); // uppercased
    }

    @Test
    void importFromExcel_blankRow_isSkippedWithoutError() throws Exception {
        byte[] xlsx = buildWorkbook(
                row("", "", "", "", "", "", "", "", "", "", "", ""), // fully blank row
                row("Valid question", "Cat", "Tech", "EASY", "TEXT", "", "", "", "", "", "", "5")
        );
        MockMultipartFile file = new MockMultipartFile("file", "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);
        when(questionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.importFromExcel(file, "hr@company.com");

        assertThat(result.get("saved")).isEqualTo(1);
        assertThat(result.get("failed")).isEqualTo(0);
    }

    @Test
    void importFromExcel_invalidQuestionType_reportsErrorAndSkipsRow() throws Exception {
        byte[] xlsx = buildWorkbook(
                row("Some question", "Cat", "Tech", "EASY", "ESSAY", "", "", "", "", "", "", "5")
        );
        MockMultipartFile file = new MockMultipartFile("file", "questions.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);

        Map<String, Object> result = service.importFromExcel(file, "hr@company.com");

        assertThat(result.get("saved")).isEqualTo(0);
        assertThat(result.get("failed")).isEqualTo(1);
        verify(questionRepo, never()).save(any());
    }

    // ── Excel export ───────────────────────────────────────────────────────────

    @Test
    void exportToExcel_producesWorkbookWithHeaderAndDataRows() throws Exception {
        InterviewQuestion q = InterviewQuestion.builder()
                .id(1L).questionText("Explain SOLID").category("Backend").technology("Java")
                .difficulty(QuestionDifficulty.MEDIUM).questionType(QuestionType.TEXT)
                .marks(5).status(QuestionStatus.ACTIVE).build();
        when(questionRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(q));

        byte[] data = service.exportToExcel();

        assertThat(data).isNotEmpty();
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Question");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Explain SOLID");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("MEDIUM");
        }
    }

    // ── test helpers ───────────────────────────────────────────────────────────

    private String[] row(String... cells) { return cells; }

    private byte[] buildWorkbook(String[]... rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Questions");
            Row header = sheet.createRow(0);
            for (int i = 0; i < 12; i++) header.createCell(i).setCellValue("header" + i);

            int r = 1;
            for (String[] cells : rows) {
                Row row = sheet.createRow(r++);
                for (int i = 0; i < cells.length; i++) row.createCell(i).setCellValue(cells[i]);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }
}

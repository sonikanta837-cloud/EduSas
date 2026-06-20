package com.emp.management.service;

import com.emp.management.dto.LeaveUploadResult;
import com.emp.management.entity.*;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.repository.LeaveRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveUploadServiceTest {

    @Mock private LeaveRepository leaveRepository;
    @Mock private EmployeeDetailsRepository employeeDetailsRepository;

    @InjectMocks private LeaveUploadService leaveUploadService;

    private EmployeeDetails emp1;
    private EmployeeDetails emp2;

    @BeforeEach
    void setUp() {
        User u1 = User.builder().id(1L).email("emp1@company.com").role(Role.EMPLOYEE).build();
        emp1 = EmployeeDetails.builder().id(1L).user(u1)
                .firstName("John").lastName("Doe").seatingLocation("Ahmedabad")
                .active(true).build();

        User u2 = User.builder().id(2L).email("emp2@company.com").role(Role.EMPLOYEE).build();
        emp2 = EmployeeDetails.builder().id(2L).user(u2)
                .firstName("Jane").lastName("Smith").seatingLocation("Mumbai")
                .active(true).build();
    }

    // ── addSingleHoliday ──────────────────────────────────────────────────────

    @Test
    void addSingleHoliday_noOverlap_createsLeaveForAllEmployees() {
        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(emp1, emp2));
        when(leaveRepository.existsOverlappingLeave(anyLong(), any(), any(), any())).thenReturn(false);
        when(leaveRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LeaveUploadResult result = leaveUploadService.addSingleHoliday(
                "Independence Day", LocalDate.of(2026, 8, 15), null);

        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getSkipped()).isEqualTo(0);
        verify(leaveRepository, times(2)).save(any(Leave.class));
    }

    @Test
    void addSingleHoliday_withLocation_filtersEmployees() {
        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(emp1, emp2));
        when(leaveRepository.existsOverlappingLeave(anyLong(), any(), any(), any())).thenReturn(false);
        when(leaveRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LeaveUploadResult result = leaveUploadService.addSingleHoliday(
                "Independence Day", LocalDate.of(2026, 8, 15), "Ahmedabad");

        // Only emp1 is in Ahmedabad
        verify(leaveRepository, times(1)).save(any(Leave.class));
        assertThat(result.getImported()).isEqualTo(1);
    }

    @Test
    void addSingleHoliday_allOverlapping_skipsAll() {
        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(emp1, emp2));
        when(leaveRepository.existsOverlappingLeave(anyLong(), any(), any(), any())).thenReturn(true);

        LeaveUploadResult result = leaveUploadService.addSingleHoliday(
                "Independence Day", LocalDate.of(2026, 8, 15), null);

        assertThat(result.getImported()).isEqualTo(0);
        assertThat(result.getSkipped()).isEqualTo(1);
        assertThat(result.getErrors()).isNotEmpty();
        verify(leaveRepository, never()).save(any());
    }

    @Test
    void addSingleHoliday_noEmployees_returnsError() {
        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of());

        LeaveUploadResult result = leaveUploadService.addSingleHoliday(
                "Holiday", LocalDate.of(2026, 8, 15), null);

        assertThat(result.getImported()).isEqualTo(0);
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0)).contains("No active employees found");
    }

    @Test
    void addSingleHoliday_locationWithNoEmployees_returnsError() {
        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(emp1)); // only Ahmedabad

        LeaveUploadResult result = leaveUploadService.addSingleHoliday(
                "Holiday", LocalDate.of(2026, 8, 15), "Jamnagar");

        assertThat(result.getImported()).isEqualTo(0);
        assertThat(result.getErrors().get(0)).contains("No active employees found in Jamnagar");
    }

    @Test
    void addSingleHoliday_partialOverlap_createsForNonOverlapping() {
        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(emp1, emp2));
        when(leaveRepository.existsOverlappingLeave(eq(1L), any(), any(), any())).thenReturn(true);
        when(leaveRepository.existsOverlappingLeave(eq(2L), any(), any(), any())).thenReturn(false);
        when(leaveRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LeaveUploadResult result = leaveUploadService.addSingleHoliday(
                "Independence Day", LocalDate.of(2026, 8, 15), null);

        verify(leaveRepository, times(1)).save(any(Leave.class));
        assertThat(result.getImported()).isEqualTo(1); // 1 employee created
    }

    // ── generateTemplate ──────────────────────────────────────────────────────

    @Test
    void generateTemplate_returnsNonEmptyExcelBytes() throws IOException {
        byte[] bytes = leaveUploadService.generateTemplate();

        assertThat(bytes).isNotNull();
        assertThat(bytes.length).isGreaterThan(0);

        // Verify it's a valid XLSX file
        try (Workbook wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            assertThat(wb.getNumberOfSheets()).isGreaterThanOrEqualTo(1);
            assertThat(wb.getSheetAt(0).getSheetName()).isEqualTo("Company Holidays");
        }
    }

    @Test
    void generateTemplate_hasHeaderRow() throws IOException {
        byte[] bytes = leaveUploadService.generateTemplate();

        try (Workbook wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertThat(header).isNotNull();
            assertThat(header.getCell(0).getStringCellValue()).contains("Holiday Name");
            assertThat(header.getCell(1).getStringCellValue()).contains("Date");
        }
    }

    @Test
    void generateTemplate_hasSampleRows() throws IOException {
        byte[] bytes = leaveUploadService.generateTemplate();

        try (Workbook wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheetAt(0);
            // Should have at least 2 rows (header + 1 sample)
            assertThat(sheet.getLastRowNum()).isGreaterThanOrEqualTo(1);
        }
    }

    // ── processUpload ─────────────────────────────────────────────────────────

    @Test
    void processUpload_validExcel_importsHolidays() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
                {"Holiday Name*", "Date* (e.g. 15-June-2026)", "Location"},
                {"Independence Day", "15-August-2026", ""}
        });

        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(emp1, emp2));
        when(leaveRepository.existsOverlappingLeave(anyLong(), any(), any(), any())).thenReturn(false);
        when(leaveRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LeaveUploadResult result = leaveUploadService.processUpload(file, null);

        assertThat(result.getTotalRows()).isEqualTo(1);
        assertThat(result.getImported()).isEqualTo(1);
        verify(leaveRepository, times(2)).save(any(Leave.class));
    }

    @Test
    void processUpload_noEmployees_returnsError() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
                {"Holiday Name*", "Date*", "Location"},
                {"Independence Day", "15-August-2026", ""}
        });

        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of());

        LeaveUploadResult result = leaveUploadService.processUpload(file, null);

        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0)).contains("No active employees found");
    }

    @Test
    void processUpload_missingHolidayName_skipsRow() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
                {"Holiday Name*", "Date*", "Location"},
                {"", "15-August-2026", ""}
        });

        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(emp1));

        LeaveUploadResult result = leaveUploadService.processUpload(file, null);

        assertThat(result.getSkipped()).isEqualTo(1);
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Holiday name is required"));
    }

    @Test
    void processUpload_missingDate_skipsRow() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
                {"Holiday Name*", "Date*", "Location"},
                {"Independence Day", "", ""}
        });

        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(emp1));

        LeaveUploadResult result = leaveUploadService.processUpload(file, null);

        assertThat(result.getSkipped()).isEqualTo(1);
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Date is required"));
    }

    @Test
    void processUpload_invalidDateFormat_skipsRow() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
                {"Holiday Name*", "Date*", "Location"},
                {"Independence Day", "not-a-date", ""}
        });

        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(emp1));

        LeaveUploadResult result = leaveUploadService.processUpload(file, null);

        assertThat(result.getSkipped()).isEqualTo(1);
        assertThat(result.getErrors()).anyMatch(e -> e.contains("Cannot parse date"));
    }

    @Test
    void processUpload_withLocationFilter_filtersEmployees() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
                {"Holiday Name*", "Date*", "Location"},
                {"Independence Day", "15-August-2026", ""}
        });

        // Only Ahmedabad employees match the location filter
        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(emp1, emp2));
        when(leaveRepository.existsOverlappingLeave(anyLong(), any(), any(), any())).thenReturn(false);
        when(leaveRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LeaveUploadResult result = leaveUploadService.processUpload(file, "Ahmedabad");

        // Only emp1 (Ahmedabad) gets a leave record
        verify(leaveRepository, times(1)).save(any(Leave.class));
    }

    @Test
    void processUpload_rowLevelLocation_filtersFromAllEmployeesWhenNoUiFilter() throws IOException {
        // Row says "Mumbai" with no UI filter — only Mumbai employees get leave
        MockMultipartFile file = buildExcelFile(new String[][]{
                {"Holiday Name*", "Date*", "Location"},
                {"Independence Day", "15-August-2026", "Mumbai"}
        });

        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(emp1, emp2));
        when(leaveRepository.existsOverlappingLeave(anyLong(), any(), any(), any())).thenReturn(false);
        when(leaveRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LeaveUploadResult result = leaveUploadService.processUpload(file, null);

        // Row-level location "Mumbai" — only emp2 (Mumbai) gets leave
        verify(leaveRepository, times(1)).save(argThat(l ->
                ((Leave) l).getEmployee().getId().equals(2L)));
    }

    @Test
    void processUpload_rowLocationNoMatchingEmployees_skipsRow() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
                {"Holiday Name*", "Date*", "Location"},
                {"Independence Day", "15-August-2026", "Jamnagar"}
        });

        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(emp1, emp2));

        LeaveUploadResult result = leaveUploadService.processUpload(file, null);

        assertThat(result.getSkipped()).isEqualTo(1);
        assertThat(result.getErrors()).anyMatch(e -> e.contains("No active employees found in 'Jamnagar'"));
    }

    @Test
    void processUpload_allEmployeesHaveLeave_addsErrorNote() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
                {"Holiday Name*", "Date*", "Location"},
                {"Independence Day", "15-August-2026", ""}
        });

        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(emp1));
        when(leaveRepository.existsOverlappingLeave(anyLong(), any(), any(), any())).thenReturn(true);

        LeaveUploadResult result = leaveUploadService.processUpload(file, null);

        assertThat(result.getErrors()).anyMatch(e -> e.contains("All matching employees already have a leave"));
    }

    @Test
    void processUpload_blankRows_ignored() throws IOException {
        MockMultipartFile file = buildExcelFile(new String[][]{
                {"Holiday Name*", "Date*", "Location"},
                {"Independence Day", "15-August-2026", ""},
                {"", "", ""}  // blank row
        });

        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(emp1));
        when(leaveRepository.existsOverlappingLeave(anyLong(), any(), any(), any())).thenReturn(false);
        when(leaveRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LeaveUploadResult result = leaveUploadService.processUpload(file, null);

        // Only 1 data row processed
        assertThat(result.getTotalRows()).isEqualTo(1);
    }

    @Test
    void processUpload_multipleDateFormats_parsedCorrectly() throws IOException {
        // Test all supported date formats
        MockMultipartFile file = buildExcelFile(new String[][]{
                {"Holiday Name*", "Date*", "Location"},
                {"Holiday1", "15-June-2026", ""},   // dd-MMMM-yyyy
                {"Holiday2", "15-Jun-2026", ""},    // dd-MMM-yyyy
                {"Holiday3", "15/06/2026", ""},     // dd/MM/yyyy
                {"Holiday4", "2026-06-15", ""},     // yyyy-MM-dd
                {"Holiday5", "15-06-2026", ""},     // dd-MM-yyyy
        });

        when(employeeDetailsRepository.findByActive(true)).thenReturn(List.of(emp1));
        when(leaveRepository.existsOverlappingLeave(anyLong(), any(), any(), any())).thenReturn(false);
        when(leaveRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LeaveUploadResult result = leaveUploadService.processUpload(file, null);

        // All 5 should be imported
        assertThat(result.getImported()).isEqualTo(5);
        assertThat(result.getSkipped()).isEqualTo(0);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private MockMultipartFile buildExcelFile(String[][] rows) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sheet1");
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < rows[r].length; c++) {
                    row.createCell(c).setCellValue(rows[r][c]);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return new MockMultipartFile(
                    "file", "test.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    out.toByteArray());
        }
    }
}

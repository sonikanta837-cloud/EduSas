package com.emp.management.service;

import com.emp.management.dto.LeaveUploadResult;
import com.emp.management.entity.Leave;
import com.emp.management.entity.LeaveStatus;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.repository.LeaveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveUploadService {

    private final LeaveRepository           leaveRepository;
    private final EmployeeDetailsRepository employeeDetailsRepository;

    // Supported date formats
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd-MMMM-yyyy",  Locale.ENGLISH),   // 15-June-2026
            DateTimeFormatter.ofPattern("dd-MMM-yyyy",   Locale.ENGLISH),   // 15-Jun-2026
            DateTimeFormatter.ofPattern("d-MMMM-yyyy",   Locale.ENGLISH),   // 5-June-2026
            DateTimeFormatter.ofPattern("d-MMM-yyyy",    Locale.ENGLISH),   // 5-Jun-2026
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),                       // 15/06/2026
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),                       // 2026-06-15
            DateTimeFormatter.ofPattern("dd-MM-yyyy")                        // 15-06-2026
    );

    // ── Upload ────────────────────────────────────────────────────────────────

    public LeaveUploadResult processUpload(MultipartFile file, String location) throws IOException {
        List<String> errors = new ArrayList<>();
        int holidaysProcessed = 0, recordsCreated = 0, skipped = 0, totalRows = 0;

        // Load active employees, filtered by location when specified
        var allEmployees = employeeDetailsRepository.findByActive(true);
        if (location != null && !location.isBlank()) {
            final String loc = location.trim();
            allEmployees = allEmployees.stream()
                    .filter(e -> loc.equalsIgnoreCase(e.getSeatingLocation()))
                    .collect(java.util.stream.Collectors.toList());
        }
        if (allEmployees.isEmpty()) {
            String where = (location != null && !location.isBlank()) ? " in " + location : " in the system";
            errors.add("No active employees found" + where);
            return LeaveUploadResult.builder()
                    .totalRows(0).imported(0).skipped(0).errors(errors).build();
        }

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);

            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null || isRowBlank(row)) continue;
                totalRows++;

                String holidayName  = cellStr(row, 0);
                String dateStr      = cellStr(row, 1);
                String rowLocation  = cellStr(row, 2); // Column C — optional per-row location

                if (holidayName.isBlank()) {
                    errors.add("Row " + (rowIdx + 1) + ": Holiday name is required");
                    skipped++;
                    continue;
                }
                if (dateStr.isBlank()) {
                    errors.add("Row " + (rowIdx + 1) + ": Date is required for '" + holidayName + "'");
                    skipped++;
                    continue;
                }

                LocalDate date = parseDate(dateStr);
                if (date == null) {
                    errors.add("Row " + (rowIdx + 1) + ": Cannot parse date '" + dateStr
                            + "' for holiday '" + holidayName + "'");
                    skipped++;
                    continue;
                }

                // Column C overrides the UI-level location filter; UI filter is the fallback
                String effectiveLocation = !rowLocation.isBlank() ? rowLocation.trim()
                        : (location != null && !location.isBlank() ? location.trim() : null);

                // Determine the employee set for this row
                var rowEmployees = allEmployees;
                if (effectiveLocation != null) {
                    final String loc = effectiveLocation;
                    rowEmployees = allEmployees.stream()
                            .filter(e -> loc.equalsIgnoreCase(e.getSeatingLocation()))
                            .collect(java.util.stream.Collectors.toList());
                    if (rowEmployees.isEmpty()) {
                        errors.add("Row " + (rowIdx + 1) + ": No active employees found in '"
                                + effectiveLocation + "' for '" + holidayName + "' — skipped");
                        skipped++;
                        continue;
                    }
                }

                holidaysProcessed++;
                int createdForThisHoliday = 0;

                for (var emp : rowEmployees) {
                    boolean overlaps = leaveRepository.existsOverlappingLeave(
                            emp.getId(), date, date,
                            List.of(LeaveStatus.APPROVED, LeaveStatus.PENDING));
                    if (overlaps) continue;

                    leaveRepository.save(Leave.builder()
                            .employee(emp)
                            .leaveType("Public Holiday")
                            .startDate(date)
                            .endDate(date)
                            .totalDays(1)
                            .reason(holidayName.trim())
                            .status(LeaveStatus.APPROVED)
                            .build());

                    createdForThisHoliday++;
                    recordsCreated++;
                }

                if (createdForThisHoliday == 0) {
                    errors.add("'" + holidayName + "' on " + date
                            + ": All matching employees already have a leave on this date — skipped");
                }
            }
        }

        return LeaveUploadResult.builder()
                .totalRows(totalRows)           // holidays processed
                .imported(holidaysProcessed)    // holidays successfully applied
                .skipped(skipped)               // holidays that failed validation
                .errors(errors)
                // store employee records count in a note via errors or extend DTO
                .build();
    }

    // ── Add Single Holiday ────────────────────────────────────────────────────

    public LeaveUploadResult addSingleHoliday(String holidayName, LocalDate date, String location) {
        List<String> errors = new ArrayList<>();
        var allEmployees = employeeDetailsRepository.findByActive(true);

        // Filter by location when specified
        if (location != null && !location.isBlank()) {
            final String loc = location.trim();
            allEmployees = allEmployees.stream()
                    .filter(e -> loc.equalsIgnoreCase(e.getSeatingLocation()))
                    .collect(java.util.stream.Collectors.toList());
        }

        if (allEmployees.isEmpty()) {
            String where = (location != null && !location.isBlank()) ? " in " + location : " in the system";
            errors.add("No active employees found" + where);
            return LeaveUploadResult.builder().totalRows(0).imported(0).skipped(0).errors(errors).build();
        }

        int created = 0;
        for (var emp : allEmployees) {
            boolean overlaps = leaveRepository.existsOverlappingLeave(
                    emp.getId(), date, date,
                    List.of(LeaveStatus.APPROVED, LeaveStatus.PENDING));
            if (overlaps) continue;

            leaveRepository.save(Leave.builder()
                    .employee(emp)
                    .leaveType("Public Holiday")
                    .startDate(date)
                    .endDate(date)
                    .totalDays(1)
                    .reason(holidayName.trim())
                    .status(LeaveStatus.APPROVED)
                    .build());
            created++;
        }

        if (created == 0) {
            errors.add("All employees already have a leave on " + date + " — no records created");
        }

        return LeaveUploadResult.builder()
                .totalRows(1)
                .imported(created > 0 ? 1 : 0)
                .skipped(created == 0 ? 1 : 0)
                .errors(errors)
                .build();
    }

    // ── Template ──────────────────────────────────────────────────────────────

    public byte[] generateTemplate() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Company Holidays");

            // Header style
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row headerRow = sheet.createRow(0);
            String[] headers = { "Holiday Name*", "Date* (e.g. 15-June-2026)", "Location (optional)" };
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, i == 0 ? 8000 : i == 1 ? 9000 : 7000);
            }

            // Sample rows
            String[][] samples = {
                { "New Year's Day",    "01-January-2026",  ""           },
                { "Holi",             "15-June-2026",     "Mandsaur"   },
                { "Independence Day", "15-August-2026",   "Ahmedabad"  },
                { "Diwali",           "20-October-2026",  ""           },
                { "Christmas",        "25-December-2026", "Jamnagar"   },
            };
            for (int r = 0; r < samples.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < samples[r].length; c++) {
                    row.createCell(c).setCellValue(samples[r][c]);
                }
            }

            // Visible "Locations" sheet — users can add new locations in the empty rows below
            Sheet locSheet = wb.createSheet("Locations");
            locSheet.setColumnWidth(0, 10000);

            // Style for the header row
            CellStyle locHeaderStyle = wb.createCellStyle();
            Font locHeaderFont = wb.createFont();
            locHeaderFont.setBold(true);
            locHeaderFont.setColor(IndexedColors.WHITE.getIndex());
            locHeaderStyle.setFont(locHeaderFont);
            locHeaderStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
            locHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Cell locHeaderCell = locSheet.createRow(0).createCell(0);
            locHeaderCell.setCellValue("Locations  (add new locations below ↓)");
            locHeaderCell.setCellStyle(locHeaderStyle);

            // Pre-filled default locations (data starts at row 2, index 1)
            String[] locationOptions = {"Mandsaur", "Ahmedabad", "Jamnagar"};
            for (int i = 0; i < locationOptions.length; i++) {
                locSheet.createRow(i + 1).createCell(0).setCellValue(locationOptions[i]);
            }
            // Reserve 20 more rows for user-added locations; formula covers all of them
            int maxLocations = locationOptions.length + 20;

            // Data validation on Column C — references Locations!$A$2:$A$N (skips header)
            org.apache.poi.xssf.usermodel.XSSFSheet xssfSheet =
                    (org.apache.poi.xssf.usermodel.XSSFSheet) sheet;
            org.apache.poi.xssf.usermodel.XSSFDataValidationHelper dvHelper =
                    new org.apache.poi.xssf.usermodel.XSSFDataValidationHelper(xssfSheet);
            DataValidationConstraint dvConstraint = dvHelper.createFormulaListConstraint(
                    "Locations!$A$2:$A$" + (maxLocations + 1));
            CellRangeAddressList locationRange = new CellRangeAddressList(1, 200, 2, 2);
            DataValidation locationValidation = dvHelper.createValidation(dvConstraint, locationRange);
            locationValidation.setSuppressDropDownArrow(true); // true → showDropDown="false" in XML → Excel shows the arrow
            locationValidation.setShowErrorBox(false);
            locationValidation.createPromptBox("Location (optional)",
                    "Pick a location or add a new one in the 'Locations' sheet. Leave blank for all employees.");
            locationValidation.setShowPromptBox(true);
            xssfSheet.addValidationData(locationValidation);

            // Notes sheet
            Sheet notes = wb.createSheet("Instructions");
            String[] lines = {
                "INSTRUCTIONS:",
                "",
                "Column A — Holiday Name (required)",
                "  Example: Holi, Christmas, Independence Day",
                "",
                "Column B — Date (required)",
                "  Accepted formats:",
                "    15-June-2026   (recommended)",
                "    15-Jun-2026",
                "    15/06/2026",
                "    2026-06-15",
                "",
                "Column C — Location (optional)",
                "  Leave blank to apply to ALL active employees.",
                "  Select a location from the dropdown to apply only to employees there.",
                "  To add a new location: go to the 'Locations' sheet and type it in an empty row.",
                "  The new location will instantly appear in the Column C dropdown.",
                "  Each row can have a different location.",
                "",
                "HOW IT WORKS:",
                "  - Each holiday is automatically applied to matching active employees",
                "  - Leave type is set to 'Public Holiday' and status is 'APPROVED'",
                "  - If an employee already has a leave on that date, they are skipped",
                "  - You can see imported leaves in each employee's Leave section",
            };
            for (int i = 0; i < lines.length; i++) {
                notes.createRow(i).createCell(0).setCellValue(lines[i]);
            }
            notes.setColumnWidth(0, 18000);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String cellStr(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                double d = cell.getNumericCellValue();
                yield (d == Math.floor(d)) ? String.valueOf((long) d) : String.valueOf(d);
            }
            default -> "";
        };
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String cleaned = raw.trim();
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try { return LocalDate.parse(cleaned, fmt); }
            catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private boolean isRowBlank(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK
                    && !cell.toString().isBlank()) return false;
        }
        return true;
    }
}

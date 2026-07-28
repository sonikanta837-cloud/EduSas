package com.emp.management.service;

import com.emp.management.dto.PerformanceEmployeeSummaryRowDTO;
import com.emp.management.dto.PerformanceExportAuditLogDTO;
import com.emp.management.dto.PerformanceExportRowDTO;
import com.emp.management.dto.PerformanceReviewDTO;
import com.emp.management.entity.EmployeeDetails;
import com.emp.management.entity.PerformanceExportAuditLog;
import com.emp.management.entity.PerformanceImprovementPlan;
import com.emp.management.entity.PipStatus;
import com.emp.management.exception.ResourceNotFoundException;
import com.emp.management.repository.EmployeeDetailsRepository;
import com.emp.management.repository.PerformanceExportAuditLogRepository;
import com.emp.management.repository.PerformanceReviewRepository;
import com.emp.management.repository.PipRepository;
import com.itextpdf.kernel.colors.Color;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PerformanceExportService {

    private final PerformanceService performanceService;
    private final PerformanceReviewRepository reviewRepository;
    private final EmployeeDetailsRepository employeeDetailsRepository;
    private final PipRepository pipRepository;
    private final SystemSettingService systemSettingService;
    private final PerformanceExportAuditLogRepository auditLogRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

    @Transactional
    public byte[] export(String format, String scope, String reportType, String search, String reviewPeriod, Integer ratingMin,
                         String department, String sortBy, boolean belowThresholdOnly, boolean pipOnly,
                         String callerEmail) throws IOException {

        EmployeeDetails caller = employeeDetailsRepository.findByUserEmail(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found for user: " + callerEmail));

        double threshold = systemSettingService.getLowRatingThreshold();
        boolean filtered = "FILTERED".equalsIgnoreCase(scope);
        boolean employeeSummary = "EMPLOYEES".equalsIgnoreCase(reportType);

        byte[] data;
        int recordCount;

        if (employeeSummary) {
            List<PerformanceEmployeeSummaryRowDTO> rows = buildEmployeeSummaryRows(filtered, search, ratingMin, department, sortBy, threshold);
            if (belowThresholdOnly) rows = rows.stream().filter(PerformanceEmployeeSummaryRowDTO::isBelowThreshold).collect(Collectors.toList());
            if (pipOnly) rows = rows.stream().filter(PerformanceEmployeeSummaryRowDTO::isActivePip).collect(Collectors.toList());
            recordCount = rows.size();
            switch (format.toUpperCase()) {
                case "EXCEL": data = buildEmployeeExcel(rows, threshold, scope, caller); break;
                case "PDF":   data = buildEmployeePdf(rows, threshold, scope, caller); break;
                case "CSV":   data = buildEmployeeCsv(rows); break;
                default: throw new IllegalArgumentException("Unsupported export format: " + format);
            }
        } else {
            List<PerformanceReviewDTO> all = performanceService.getAllReviews();
            List<PerformanceReviewDTO> filteredList = filtered ? applyFilters(all, search, reviewPeriod, ratingMin, department) : new ArrayList<>(all);
            applySort(filteredList, sortBy);

            List<PerformanceExportRowDTO> rows = enrich(filteredList, threshold);
            if (belowThresholdOnly) rows = rows.stream().filter(PerformanceExportRowDTO::isBelowThreshold).collect(Collectors.toList());
            if (pipOnly) rows = rows.stream().filter(PerformanceExportRowDTO::isActivePip).collect(Collectors.toList());
            recordCount = rows.size();
            switch (format.toUpperCase()) {
                case "EXCEL": data = buildExcel(rows, threshold, scope, caller); break;
                case "PDF":   data = buildPdf(rows, threshold, scope, caller); break;
                case "CSV":   data = buildCsv(rows); break;
                default: throw new IllegalArgumentException("Unsupported export format: " + format);
            }
        }

        String filterSummary = (employeeSummary ? "reportType=EMPLOYEES, " : "reportType=REVIEWS, ")
                + buildFilterSummary(filtered, search, reviewPeriod, ratingMin, department, sortBy, belowThresholdOnly, pipOnly);
        auditLogRepository.save(PerformanceExportAuditLog.builder()
                .performedBy(caller)
                .format(format.toUpperCase())
                .scope(scope.toUpperCase())
                .recordCount(recordCount)
                .filterSummary(filterSummary)
                .build());

        return data;
    }

    @Transactional(readOnly = true)
    public List<PerformanceExportAuditLogDTO> getRecentAuditLogs() {
        return auditLogRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .map(log -> PerformanceExportAuditLogDTO.builder()
                        .id(log.getId())
                        .performedByName(log.getPerformedBy() != null ? log.getPerformedBy().getFullName() : "—")
                        .format(log.getFormat())
                        .scope(log.getScope())
                        .recordCount(log.getRecordCount())
                        .filterSummary(log.getFilterSummary())
                        .createdAt(log.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    // ── Filtering / sorting (mirrors Performance.jsx client-side filter semantics) ──

    private List<PerformanceReviewDTO> applyFilters(List<PerformanceReviewDTO> reviews, String search, String reviewPeriod,
                                                       Integer ratingMin, String department) {
        Map<Long, EmployeeDetails> empMap = department != null && !department.isBlank() ? loadEmployeeMap(reviews) : Map.of();
        String q = search != null ? search.toLowerCase() : null;
        return reviews.stream().filter(r -> {
            if (q != null && !q.isBlank()
                    && !(r.getEmployeeName() != null && r.getEmployeeName().toLowerCase().contains(q))
                    && !(r.getReviewerName() != null && r.getReviewerName().toLowerCase().contains(q))) {
                return false;
            }
            if (reviewPeriod != null && !reviewPeriod.isBlank() && !reviewPeriod.equals(r.getReviewPeriod())) return false;
            if (ratingMin != null && (r.getRating() == null || r.getRating() < ratingMin)) return false;
            if (department != null && !department.isBlank()) {
                EmployeeDetails emp = empMap.get(r.getEmployeeId());
                if (emp == null || !department.equals(emp.getDepartment())) return false;
            }
            return true;
        }).collect(Collectors.toList());
    }

    private void applySort(List<PerformanceReviewDTO> reviews, String sortBy) {
        if ("highest".equalsIgnoreCase(sortBy)) {
            reviews.sort((a, b) -> Integer.compare(b.getRating() == null ? 0 : b.getRating(), a.getRating() == null ? 0 : a.getRating()));
        } else if ("lowest".equalsIgnoreCase(sortBy)) {
            reviews.sort(Comparator.comparingInt(a -> a.getRating() == null ? 0 : a.getRating()));
        } else {
            reviews.sort((a, b) -> {
                if (a.getReviewDate() == null) return 1;
                if (b.getReviewDate() == null) return -1;
                return b.getReviewDate().compareTo(a.getReviewDate());
            });
        }
    }

    private Map<Long, EmployeeDetails> loadEmployeeMap(List<PerformanceReviewDTO> reviews) {
        List<Long> ids = reviews.stream().map(PerformanceReviewDTO::getEmployeeId).distinct().collect(Collectors.toList());
        return employeeDetailsRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(EmployeeDetails::getId, e -> e));
    }

    // ── Enrichment: department/code, average rating, threshold flag, active PIP ──

    private List<PerformanceExportRowDTO> enrich(List<PerformanceReviewDTO> reviews, double threshold) {
        List<Long> employeeIds = reviews.stream().map(PerformanceReviewDTO::getEmployeeId).distinct().collect(Collectors.toList());
        Map<Long, EmployeeDetails> empMap = employeeDetailsRepository.findAllById(employeeIds).stream()
                .collect(Collectors.toMap(EmployeeDetails::getId, e -> e));

        Map<Long, Double> avgRatingMap = new HashMap<>();
        for (Long id : employeeIds) avgRatingMap.put(id, reviewRepository.getAverageRatingByEmployee(id));

        Map<Long, PerformanceImprovementPlan> activePipMap = employeeIds.isEmpty() ? Map.of()
                : pipRepository.findByEmployeeIdInAndStatus(employeeIds, PipStatus.ACTIVE).stream()
                    .collect(Collectors.toMap(p -> p.getEmployee().getId(), p -> p, (a, b) -> a));

        return reviews.stream().map(r -> {
            EmployeeDetails emp = empMap.get(r.getEmployeeId());
            PerformanceImprovementPlan pip = activePipMap.get(r.getEmployeeId());
            return PerformanceExportRowDTO.builder()
                    .employeeName(r.getEmployeeName())
                    .employeeCode(emp != null ? emp.getEmployeeCode() : null)
                    .department(emp != null ? emp.getDepartment() : null)
                    .reviewerName(r.getReviewerName())
                    .reviewerRole(r.getReviewerRole())
                    .reviewPeriod(r.getReviewPeriod())
                    .reviewDate(r.getReviewDate())
                    .rating(r.getRating())
                    .ratingLabel(ratingLabel(r.getRating() != null ? r.getRating() : 0))
                    .belowThreshold(r.getRating() != null && r.getRating() < threshold)
                    .employeeAverageRating(avgRatingMap.get(r.getEmployeeId()))
                    .activePip(pip != null)
                    .pipTitle(pip != null ? pip.getTitle() : null)
                    .pipStatus(pip != null ? pip.getStatus().name() : null)
                    .pipStartDate(pip != null ? pip.getStartDate() : null)
                    .pipEndDate(pip != null ? pip.getEndDate() : null)
                    .pipReason(pip != null ? pip.getReason() : null)
                    .pipGoalsAchieved(pip != null ? pipRepository.countAchievedGoals(pip.getId()) : null)
                    .pipGoalsTotal(pip != null ? pipRepository.countTotalGoals(pip.getId()) : null)
                    .comments(r.getComments())
                    .strengths(r.getStrengths())
                    .areasOfImprovement(r.getAreasOfImprovement())
                    .build();
        }).collect(Collectors.toList());
    }

    // ── Employee summary rows (every active employee, including zero-review ones) ──

    private List<PerformanceEmployeeSummaryRowDTO> buildEmployeeSummaryRows(boolean filtered, String search, Integer ratingMin,
                                                                             String department, String sortBy, double threshold) {
        List<EmployeeDetails> employees = employeeDetailsRepository.findByActive(true);

        if (filtered) {
            String q = search != null ? search.toLowerCase() : null;
            employees = employees.stream().filter(e -> {
                if (q != null && !q.isBlank() && !e.getFullName().toLowerCase().contains(q)) return false;
                if (department != null && !department.isBlank() && !department.equals(e.getDepartment())) return false;
                return true;
            }).collect(Collectors.toList());
        }

        List<Long> employeeIds = employees.stream().map(EmployeeDetails::getId).collect(Collectors.toList());

        Map<Long, List<PerformanceReviewDTO>> reviewsByEmployee = performanceService.getAllReviews().stream()
                .collect(Collectors.groupingBy(PerformanceReviewDTO::getEmployeeId));

        Map<Long, PerformanceImprovementPlan> activePipMap = employeeIds.isEmpty() ? Map.of()
                : pipRepository.findByEmployeeIdInAndStatus(employeeIds, PipStatus.ACTIVE).stream()
                    .collect(Collectors.toMap(p -> p.getEmployee().getId(), p -> p, (a, b) -> a));

        List<PerformanceEmployeeSummaryRowDTO> rows = employees.stream().map(emp -> {
            List<PerformanceReviewDTO> empReviews = reviewsByEmployee.getOrDefault(emp.getId(), List.of());
            int total = empReviews.size();
            Double avg = total > 0
                    ? empReviews.stream().mapToInt(r -> r.getRating() != null ? r.getRating() : 0).average().orElse(0)
                    : null;
            PerformanceReviewDTO latest = empReviews.stream()
                    .filter(r -> r.getReviewDate() != null)
                    .max(Comparator.comparing(PerformanceReviewDTO::getReviewDate))
                    .orElse(null);
            PerformanceImprovementPlan pip = activePipMap.get(emp.getId());

            return PerformanceEmployeeSummaryRowDTO.builder()
                    .employeeName(emp.getFullName())
                    .employeeCode(emp.getEmployeeCode())
                    .department(emp.getDepartment())
                    .position(emp.getPosition())
                    .totalReviews(total)
                    .averageRating(avg)
                    .latestRating(latest != null ? latest.getRating() : null)
                    .latestRatingLabel(latest != null ? ratingLabel(latest.getRating() != null ? latest.getRating() : 0) : "No Reviews")
                    .latestReviewDate(latest != null ? latest.getReviewDate() : null)
                    .latestReviewerName(latest != null ? latest.getReviewerName() : null)
                    .belowThreshold(latest != null && latest.getRating() != null && latest.getRating() < threshold)
                    .activePip(pip != null)
                    .pipTitle(pip != null ? pip.getTitle() : null)
                    .pipStatus(pip != null ? pip.getStatus().name() : null)
                    .pipStartDate(pip != null ? pip.getStartDate() : null)
                    .pipEndDate(pip != null ? pip.getEndDate() : null)
                    .pipReason(pip != null ? pip.getReason() : null)
                    .pipGoalsAchieved(pip != null ? pipRepository.countAchievedGoals(pip.getId()) : null)
                    .pipGoalsTotal(pip != null ? pipRepository.countTotalGoals(pip.getId()) : null)
                    .build();
        }).collect(Collectors.toList());

        if (filtered && ratingMin != null) {
            rows = rows.stream().filter(r -> r.getAverageRating() != null && r.getAverageRating() >= ratingMin).collect(Collectors.toList());
        }

        if ("highest".equalsIgnoreCase(sortBy)) {
            rows.sort((a, b) -> Double.compare(
                    b.getAverageRating() != null ? b.getAverageRating() : -1,
                    a.getAverageRating() != null ? a.getAverageRating() : -1));
        } else if ("lowest".equalsIgnoreCase(sortBy)) {
            rows.sort(Comparator.comparingDouble(a -> a.getAverageRating() != null ? a.getAverageRating() : 999));
        } else {
            rows.sort((a, b) -> {
                if (a.getLatestReviewDate() == null) return 1;
                if (b.getLatestReviewDate() == null) return -1;
                return b.getLatestReviewDate().compareTo(a.getLatestReviewDate());
            });
        }

        return rows;
    }

    private static String ratingLabel(double r) {
        if (r >= 4.5) return "Excellent";
        if (r >= 4)   return "Very Good";
        if (r >= 3.5) return "Good";
        if (r >= 2.5) return "Average";
        if (r >= 1.5) return "Below Avg";
        return "Poor";
    }

    private String buildFilterSummary(boolean filtered, String search, String reviewPeriod, Integer ratingMin,
                                       String department, String sortBy, boolean belowThresholdOnly, boolean pipOnly) {
        if (!filtered && !belowThresholdOnly && !pipOnly) return "All records, no filters";
        List<String> parts = new ArrayList<>();
        if (filtered) {
            if (search != null && !search.isBlank()) parts.add("search=" + search);
            if (reviewPeriod != null && !reviewPeriod.isBlank()) parts.add("period=" + reviewPeriod);
            if (ratingMin != null) parts.add("minRating=" + ratingMin);
            if (department != null && !department.isBlank()) parts.add("department=" + department);
            if (sortBy != null && !sortBy.isBlank()) parts.add("sort=" + sortBy);
        }
        if (belowThresholdOnly) parts.add("belowThresholdOnly=true");
        if (pipOnly) parts.add("pipOnly=true");
        return parts.isEmpty() ? "All records, no filters" : String.join(", ", parts);
    }

    // ── Excel export ─────────────────────────────────────────────────────────

    private byte[] buildExcel(List<PerformanceExportRowDTO> rows, double threshold, String scope, EmployeeDetails caller) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            CellStyle headerStyle = headerStyle(wb);
            CellStyle belowThresholdStyle = belowThresholdStyle(wb);

            buildReviewsSheet(wb, rows, headerStyle, belowThresholdStyle);
            buildSummarySheet(wb, rows, threshold, scope, caller, headerStyle);
            buildActivePipsSheet(wb, rows, headerStyle);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private CellStyle headerStyle(Workbook wb) {
        CellStyle headerStyle = wb.createCellStyle();
        Font headerFont = wb.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return headerStyle;
    }

    private CellStyle belowThresholdStyle(Workbook wb) {
        CellStyle belowThresholdStyle = wb.createCellStyle();
        belowThresholdStyle.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
        belowThresholdStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return belowThresholdStyle;
    }

    private void buildReviewsSheet(Workbook wb, List<PerformanceExportRowDTO> rows, CellStyle headerStyle, CellStyle belowThresholdStyle) {
        Sheet sheet = wb.createSheet("Reviews");
        String[] headers = { "Employee", "Employee Code", "Department", "Reviewer", "Reviewer Role",
                "Review Period", "Review Date", "Rating", "Rating Label", "Below Threshold",
                "Employee Avg Rating", "Active PIP", "PIP Status", "PIP Title", "Comments", "Strengths", "Areas of Improvement" };
        Row hdr = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = hdr.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }
        int r = 1;
        for (PerformanceExportRowDTO row : rows) {
            Row xr = sheet.createRow(r++);
            xr.createCell(0).setCellValue(nvl(row.getEmployeeName()));
            xr.createCell(1).setCellValue(nvl(row.getEmployeeCode()));
            xr.createCell(2).setCellValue(nvl(row.getDepartment()));
            xr.createCell(3).setCellValue(nvl(row.getReviewerName()));
            xr.createCell(4).setCellValue(nvl(row.getReviewerRole()));
            xr.createCell(5).setCellValue(nvl(row.getReviewPeriod()));
            xr.createCell(6).setCellValue(row.getReviewDate() != null ? row.getReviewDate().format(DATE_FMT) : "");
            xr.createCell(7).setCellValue(row.getRating() != null ? row.getRating() : 0);
            xr.createCell(8).setCellValue(nvl(row.getRatingLabel()));
            xr.createCell(9).setCellValue(row.isBelowThreshold() ? "Yes" : "No");
            xr.createCell(10).setCellValue(row.getEmployeeAverageRating() != null ? row.getEmployeeAverageRating() : 0);
            xr.createCell(11).setCellValue(row.isActivePip() ? "Yes" : "No");
            xr.createCell(12).setCellValue(nvl(row.getPipStatus()));
            xr.createCell(13).setCellValue(nvl(row.getPipTitle()));
            xr.createCell(14).setCellValue(nvl(row.getComments()));
            xr.createCell(15).setCellValue(nvl(row.getStrengths()));
            xr.createCell(16).setCellValue(nvl(row.getAreasOfImprovement()));
            if (row.isBelowThreshold()) {
                for (int i = 0; i < headers.length; i++) xr.getCell(i).setCellStyle(belowThresholdStyle);
            }
        }
        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
    }

    private void buildSummarySheet(Workbook wb, List<PerformanceExportRowDTO> rows, double threshold, String scope,
                                    EmployeeDetails caller, CellStyle headerStyle) {
        Sheet sheet = wb.createSheet("Summary Statistics");
        int r = 0;
        Row title = sheet.createRow(r++);
        Cell tc = title.createCell(0);
        tc.setCellValue("Performance Review Export Summary");
        tc.setCellStyle(headerStyle);
        title.createCell(1).setCellStyle(headerStyle);

        r = writeStatRow(sheet, r, "Generated By", caller.getFullName());
        r = writeStatRow(sheet, r, "Generated At", LocalDateTime.now().format(DATETIME_FMT));
        r = writeStatRow(sheet, r, "Scope", scope.toUpperCase());
        r = writeStatRow(sheet, r, "Rating Threshold", String.valueOf(threshold));
        r++;

        double avg = rows.stream().mapToInt(x -> x.getRating() != null ? x.getRating() : 0).average().orElse(0);
        r = writeStatRow(sheet, r, "Total Records", String.valueOf(rows.size()));
        r = writeStatRow(sheet, r, "Average Rating", String.format("%.2f", avg));

        Map<String, Long> distribution = rows.stream()
                .collect(Collectors.groupingBy(PerformanceExportRowDTO::getRatingLabel, Collectors.counting()));
        for (String label : List.of("Excellent", "Very Good", "Good", "Average", "Below Avg", "Poor")) {
            r = writeStatRow(sheet, r, label + " Reviews", String.valueOf(distribution.getOrDefault(label, 0L)));
        }
        r++;

        long belowThresholdReviews = rows.stream().filter(PerformanceExportRowDTO::isBelowThreshold).count();
        long distinctBelowThresholdEmployees = rows.stream().filter(PerformanceExportRowDTO::isBelowThreshold)
                .map(PerformanceExportRowDTO::getEmployeeName).distinct().count();
        long distinctActivePipEmployees = rows.stream().filter(PerformanceExportRowDTO::isActivePip)
                .map(PerformanceExportRowDTO::getEmployeeName).distinct().count();

        r = writeStatRow(sheet, r, "Reviews Below Threshold", String.valueOf(belowThresholdReviews));
        r = writeStatRow(sheet, r, "Employees Below Threshold", String.valueOf(distinctBelowThresholdEmployees));
        r = writeStatRow(sheet, r, "Employees in Active PIP", String.valueOf(distinctActivePipEmployees));

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private int writeStatRow(Sheet sheet, int rowIdx, String label, String value) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
        return rowIdx + 1;
    }

    private void buildActivePipsSheet(Workbook wb, List<PerformanceExportRowDTO> rows, CellStyle headerStyle) {
        Sheet sheet = wb.createSheet("Active PIPs");
        String[] headers = { "Employee", "PIP Title", "Start Date", "End Date", "Status",
                "Goals Achieved", "Goals Total", "Reason" };
        Row hdr = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = hdr.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }
        int r = 1;
        Set<String> seen = new HashSet<>();
        for (PerformanceExportRowDTO row : rows) {
            if (!row.isActivePip() || !seen.add(row.getEmployeeName())) continue;
            Row xr = sheet.createRow(r++);
            xr.createCell(0).setCellValue(nvl(row.getEmployeeName()));
            xr.createCell(1).setCellValue(nvl(row.getPipTitle()));
            xr.createCell(2).setCellValue(row.getPipStartDate() != null ? row.getPipStartDate().format(DATE_FMT) : "");
            xr.createCell(3).setCellValue(row.getPipEndDate() != null ? row.getPipEndDate().format(DATE_FMT) : "");
            xr.createCell(4).setCellValue(nvl(row.getPipStatus()));
            xr.createCell(5).setCellValue(row.getPipGoalsAchieved() != null ? row.getPipGoalsAchieved() : 0);
            xr.createCell(6).setCellValue(row.getPipGoalsTotal() != null ? row.getPipGoalsTotal() : 0);
            xr.createCell(7).setCellValue(nvl(row.getPipReason()));
        }
        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
    }

    // ── Employee summary Excel (all employees, including zero-review ones) ────

    private byte[] buildEmployeeExcel(List<PerformanceEmployeeSummaryRowDTO> rows, double threshold, String scope, EmployeeDetails caller) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            CellStyle headerStyle = headerStyle(wb);
            CellStyle belowThresholdStyle = belowThresholdStyle(wb);

            buildAllEmployeesSheet(wb, rows, headerStyle, belowThresholdStyle);
            buildEmployeeSummaryStatsSheet(wb, rows, threshold, scope, caller, headerStyle);
            buildEmployeeActivePipsSheet(wb, rows, headerStyle);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void buildAllEmployeesSheet(Workbook wb, List<PerformanceEmployeeSummaryRowDTO> rows, CellStyle headerStyle, CellStyle belowThresholdStyle) {
        Sheet sheet = wb.createSheet("All Employees");
        String[] headers = { "Employee", "Employee Code", "Department", "Position", "Total Reviews",
                "Average Rating", "Latest Rating", "Latest Rating Label", "Latest Review Date", "Latest Reviewer",
                "Below Threshold", "Active PIP", "PIP Status", "PIP Title" };
        Row hdr = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = hdr.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }
        int r = 1;
        for (PerformanceEmployeeSummaryRowDTO row : rows) {
            Row xr = sheet.createRow(r++);
            xr.createCell(0).setCellValue(nvl(row.getEmployeeName()));
            xr.createCell(1).setCellValue(nvl(row.getEmployeeCode()));
            xr.createCell(2).setCellValue(nvl(row.getDepartment()));
            xr.createCell(3).setCellValue(nvl(row.getPosition()));
            xr.createCell(4).setCellValue(row.getTotalReviews());
            xr.createCell(5).setCellValue(row.getAverageRating() != null ? row.getAverageRating() : 0);
            xr.createCell(6).setCellValue(row.getLatestRating() != null ? row.getLatestRating() : 0);
            xr.createCell(7).setCellValue(nvl(row.getLatestRatingLabel()));
            xr.createCell(8).setCellValue(row.getLatestReviewDate() != null ? row.getLatestReviewDate().format(DATE_FMT) : "");
            xr.createCell(9).setCellValue(nvl(row.getLatestReviewerName()));
            xr.createCell(10).setCellValue(row.isBelowThreshold() ? "Yes" : "No");
            xr.createCell(11).setCellValue(row.isActivePip() ? "Yes" : "No");
            xr.createCell(12).setCellValue(nvl(row.getPipStatus()));
            xr.createCell(13).setCellValue(nvl(row.getPipTitle()));
            if (row.isBelowThreshold()) {
                for (int i = 0; i < headers.length; i++) xr.getCell(i).setCellStyle(belowThresholdStyle);
            }
        }
        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
    }

    private void buildEmployeeSummaryStatsSheet(Workbook wb, List<PerformanceEmployeeSummaryRowDTO> rows, double threshold,
                                                 String scope, EmployeeDetails caller, CellStyle headerStyle) {
        Sheet sheet = wb.createSheet("Summary Statistics");
        int r = 0;
        Row title = sheet.createRow(r++);
        Cell tc = title.createCell(0);
        tc.setCellValue("All Employees Performance Summary");
        tc.setCellStyle(headerStyle);
        title.createCell(1).setCellStyle(headerStyle);

        r = writeStatRow(sheet, r, "Generated By", caller.getFullName());
        r = writeStatRow(sheet, r, "Generated At", LocalDateTime.now().format(DATETIME_FMT));
        r = writeStatRow(sheet, r, "Scope", scope.toUpperCase());
        r = writeStatRow(sheet, r, "Rating Threshold", String.valueOf(threshold));
        r++;

        long withReviews = rows.stream().filter(x -> x.getTotalReviews() > 0).count();
        long withoutReviews = rows.size() - withReviews;
        double avg = rows.stream().filter(x -> x.getAverageRating() != null).mapToDouble(PerformanceEmployeeSummaryRowDTO::getAverageRating).average().orElse(0);

        r = writeStatRow(sheet, r, "Total Employees", String.valueOf(rows.size()));
        r = writeStatRow(sheet, r, "Employees With Reviews", String.valueOf(withReviews));
        r = writeStatRow(sheet, r, "Employees With No Reviews", String.valueOf(withoutReviews));
        r = writeStatRow(sheet, r, "Average Rating (reviewed employees)", String.format("%.2f", avg));
        r++;

        long belowThresholdCount = rows.stream().filter(PerformanceEmployeeSummaryRowDTO::isBelowThreshold).count();
        long activePipCount = rows.stream().filter(PerformanceEmployeeSummaryRowDTO::isActivePip).count();

        r = writeStatRow(sheet, r, "Employees Below Threshold", String.valueOf(belowThresholdCount));
        r = writeStatRow(sheet, r, "Employees in Active PIP", String.valueOf(activePipCount));

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void buildEmployeeActivePipsSheet(Workbook wb, List<PerformanceEmployeeSummaryRowDTO> rows, CellStyle headerStyle) {
        Sheet sheet = wb.createSheet("Active PIPs");
        String[] headers = { "Employee", "PIP Title", "Start Date", "End Date", "Status",
                "Goals Achieved", "Goals Total", "Reason" };
        Row hdr = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell c = hdr.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(headerStyle);
        }
        int r = 1;
        for (PerformanceEmployeeSummaryRowDTO row : rows) {
            if (!row.isActivePip()) continue;
            Row xr = sheet.createRow(r++);
            xr.createCell(0).setCellValue(nvl(row.getEmployeeName()));
            xr.createCell(1).setCellValue(nvl(row.getPipTitle()));
            xr.createCell(2).setCellValue(row.getPipStartDate() != null ? row.getPipStartDate().format(DATE_FMT) : "");
            xr.createCell(3).setCellValue(row.getPipEndDate() != null ? row.getPipEndDate().format(DATE_FMT) : "");
            xr.createCell(4).setCellValue(nvl(row.getPipStatus()));
            xr.createCell(5).setCellValue(row.getPipGoalsAchieved() != null ? row.getPipGoalsAchieved() : 0);
            xr.createCell(6).setCellValue(row.getPipGoalsTotal() != null ? row.getPipGoalsTotal() : 0);
            xr.createCell(7).setCellValue(nvl(row.getPipReason()));
        }
        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
    }

    // ── PDF export ───────────────────────────────────────────────────────────

    private byte[] buildPdf(List<PerformanceExportRowDTO> rows, double threshold, String scope, EmployeeDetails caller) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfDocument pdfDoc = new PdfDocument(new PdfWriter(out));
        pdfDoc.setDefaultPageSize(PageSize.A4.rotate());
        try (Document doc = new Document(pdfDoc)) {
            doc.add(new Paragraph("Performance Review Report").setBold().setFontSize(16));
            doc.add(new Paragraph(String.format("Generated by %s on %s  |  Scope: %s  |  Rating Threshold: %.1f  |  Records: %d",
                    caller.getFullName(), LocalDateTime.now().format(DATETIME_FMT), scope.toUpperCase(), threshold, rows.size()))
                    .setFontSize(9).setFontColor(new DeviceRgb(100, 100, 100)));

            double avg = rows.stream().mapToInt(x -> x.getRating() != null ? x.getRating() : 0).average().orElse(0);
            long belowCount = rows.stream().filter(PerformanceExportRowDTO::isBelowThreshold).count();
            long pipCount = rows.stream().filter(PerformanceExportRowDTO::isActivePip).map(PerformanceExportRowDTO::getEmployeeName).distinct().count();
            doc.add(new Paragraph(String.format("Average Rating: %.2f   |   Reviews Below Threshold: %d   |   Employees in Active PIP: %d",
                    avg, belowCount, pipCount)).setFontSize(9).setBold());

            String[] headers = { "Employee", "Department", "Reviewer", "Period", "Date", "Rating",
                    "Below Threshold", "Active PIP", "Comments", "Strengths", "Areas of Improvement" };
            float[] widths = { 10, 8, 10, 6, 7, 6, 7, 7, 15, 12, 12 };
            Table table = new Table(UnitValue.createPercentArray(widths)).useAllAvailableWidth();
            for (String h : headers) {
                table.addHeaderCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(h).setBold().setFontSize(8))
                        .setBackgroundColor(new DeviceRgb(30, 58, 95)).setFontColor(ColorConstants.WHITE));
            }
            for (PerformanceExportRowDTO row : rows) {
                Color bg = row.isBelowThreshold() ? new DeviceRgb(255, 237, 213) : ColorConstants.WHITE;
                table.addCell(cell(row.getEmployeeName() + (row.getEmployeeCode() != null ? " (" + row.getEmployeeCode() + ")" : ""), bg));
                table.addCell(cell(row.getDepartment(), bg));
                table.addCell(cell(row.getReviewerName() + (row.getReviewerRole() != null ? " (" + row.getReviewerRole() + ")" : ""), bg));
                table.addCell(cell(row.getReviewPeriod(), bg));
                table.addCell(cell(row.getReviewDate() != null ? row.getReviewDate().format(DATE_FMT) : "", bg));
                table.addCell(cell((row.getRating() != null ? row.getRating() : 0) + " (" + row.getRatingLabel() + ")", bg));
                table.addCell(cell(row.isBelowThreshold() ? "Yes" : "No", bg));
                table.addCell(cell(row.isActivePip() ? "Yes (" + row.getPipStatus() + ")" : "No", bg));
                table.addCell(cell(row.getComments(), bg));
                table.addCell(cell(row.getStrengths(), bg));
                table.addCell(cell(row.getAreasOfImprovement(), bg));
            }
            doc.add(table);
        }
        return out.toByteArray();
    }

    private com.itextpdf.layout.element.Cell cell(String value, Color bg) {
        return new com.itextpdf.layout.element.Cell().add(new Paragraph(nvl(value)).setFontSize(7)).setBackgroundColor(bg);
    }

    // ── Employee summary PDF (all employees, including zero-review ones) ──────

    private byte[] buildEmployeePdf(List<PerformanceEmployeeSummaryRowDTO> rows, double threshold, String scope, EmployeeDetails caller) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfDocument pdfDoc = new PdfDocument(new PdfWriter(out));
        pdfDoc.setDefaultPageSize(PageSize.A4.rotate());
        try (Document doc = new Document(pdfDoc)) {
            doc.add(new Paragraph("All Employees Performance Report").setBold().setFontSize(16));
            doc.add(new Paragraph(String.format("Generated by %s on %s  |  Scope: %s  |  Rating Threshold: %.1f  |  Employees: %d",
                    caller.getFullName(), LocalDateTime.now().format(DATETIME_FMT), scope.toUpperCase(), threshold, rows.size()))
                    .setFontSize(9).setFontColor(new DeviceRgb(100, 100, 100)));

            long withReviews = rows.stream().filter(x -> x.getTotalReviews() > 0).count();
            long belowCount = rows.stream().filter(PerformanceEmployeeSummaryRowDTO::isBelowThreshold).count();
            long pipCount = rows.stream().filter(PerformanceEmployeeSummaryRowDTO::isActivePip).count();
            doc.add(new Paragraph(String.format("With Reviews: %d   |   No Reviews Yet: %d   |   Below Threshold: %d   |   Active PIP: %d",
                    withReviews, rows.size() - withReviews, belowCount, pipCount)).setFontSize(9).setBold());

            String[] headers = { "Employee", "Department", "Position", "Total Reviews", "Avg Rating",
                    "Latest Rating", "Latest Review Date", "Latest Reviewer", "Below Threshold", "Active PIP" };
            float[] widths = { 14, 11, 11, 8, 8, 10, 10, 12, 8, 8 };
            Table table = new Table(UnitValue.createPercentArray(widths)).useAllAvailableWidth();
            for (String h : headers) {
                table.addHeaderCell(new com.itextpdf.layout.element.Cell().add(new Paragraph(h).setBold().setFontSize(8))
                        .setBackgroundColor(new DeviceRgb(30, 58, 95)).setFontColor(ColorConstants.WHITE));
            }
            for (PerformanceEmployeeSummaryRowDTO row : rows) {
                Color bg = row.isBelowThreshold() ? new DeviceRgb(255, 237, 213) : ColorConstants.WHITE;
                table.addCell(cell(row.getEmployeeName() + (row.getEmployeeCode() != null ? " (" + row.getEmployeeCode() + ")" : ""), bg));
                table.addCell(cell(row.getDepartment(), bg));
                table.addCell(cell(row.getPosition(), bg));
                table.addCell(cell(String.valueOf(row.getTotalReviews()), bg));
                table.addCell(cell(row.getAverageRating() != null ? String.format("%.2f", row.getAverageRating()) : "—", bg));
                table.addCell(cell(row.getTotalReviews() > 0 ? row.getLatestRating() + " (" + row.getLatestRatingLabel() + ")" : "No Reviews", bg));
                table.addCell(cell(row.getLatestReviewDate() != null ? row.getLatestReviewDate().format(DATE_FMT) : "—", bg));
                table.addCell(cell(row.getLatestReviewerName(), bg));
                table.addCell(cell(row.isBelowThreshold() ? "Yes" : "No", bg));
                table.addCell(cell(row.isActivePip() ? "Yes (" + row.getPipStatus() + ")" : "No", bg));
            }
            doc.add(table);
        }
        return out.toByteArray();
    }

    // ── CSV export ───────────────────────────────────────────────────────────

    private byte[] buildCsv(List<PerformanceExportRowDTO> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("Employee,Employee Code,Department,Reviewer,Reviewer Role,Review Period,Review Date,Rating,")
          .append("Rating Label,Below Threshold,Employee Avg Rating,Active PIP,PIP Status,PIP Title,Comments,Strengths,Areas of Improvement\n");
        for (PerformanceExportRowDTO row : rows) {
            sb.append(String.join(",",
                    csvEscape(row.getEmployeeName()),
                    csvEscape(row.getEmployeeCode()),
                    csvEscape(row.getDepartment()),
                    csvEscape(row.getReviewerName()),
                    csvEscape(row.getReviewerRole()),
                    csvEscape(row.getReviewPeriod()),
                    row.getReviewDate() != null ? row.getReviewDate().toString() : "",
                    String.valueOf(row.getRating() != null ? row.getRating() : 0),
                    csvEscape(row.getRatingLabel()),
                    row.isBelowThreshold() ? "Yes" : "No",
                    row.getEmployeeAverageRating() != null ? String.format("%.2f", row.getEmployeeAverageRating()) : "0.00",
                    row.isActivePip() ? "Yes" : "No",
                    csvEscape(row.getPipStatus()),
                    csvEscape(row.getPipTitle()),
                    csvEscape(row.getComments()),
                    csvEscape(row.getStrengths()),
                    csvEscape(row.getAreasOfImprovement())
            )).append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ── Employee summary CSV (all employees, including zero-review ones) ──────

    private byte[] buildEmployeeCsv(List<PerformanceEmployeeSummaryRowDTO> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("Employee,Employee Code,Department,Position,Total Reviews,Average Rating,Latest Rating,")
          .append("Latest Rating Label,Latest Review Date,Latest Reviewer,Below Threshold,Active PIP,PIP Status,PIP Title\n");
        for (PerformanceEmployeeSummaryRowDTO row : rows) {
            sb.append(String.join(",",
                    csvEscape(row.getEmployeeName()),
                    csvEscape(row.getEmployeeCode()),
                    csvEscape(row.getDepartment()),
                    csvEscape(row.getPosition()),
                    String.valueOf(row.getTotalReviews()),
                    row.getAverageRating() != null ? String.format("%.2f", row.getAverageRating()) : "",
                    row.getLatestRating() != null ? String.valueOf(row.getLatestRating()) : "",
                    csvEscape(row.getLatestRatingLabel()),
                    row.getLatestReviewDate() != null ? row.getLatestReviewDate().toString() : "",
                    csvEscape(row.getLatestReviewerName()),
                    row.isBelowThreshold() ? "Yes" : "No",
                    row.isActivePip() ? "Yes" : "No",
                    csvEscape(row.getPipStatus()),
                    csvEscape(row.getPipTitle())
            )).append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private String nvl(String s) { return s != null ? s : ""; }
}

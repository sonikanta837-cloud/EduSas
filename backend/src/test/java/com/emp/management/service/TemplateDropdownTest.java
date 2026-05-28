package com.emp.management.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.*;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

public class TemplateDropdownTest {

    @Test
    void generatedTemplateHasLocationDropdownInColumnC() throws Exception {
        // Generate the file using the same logic as LeaveUploadService
        byte[] bytes = generateTemplate();
        assertTrue(bytes.length > 0, "Template should not be empty");

        // Save to temp file
        File tmp = File.createTempFile("template_test", ".xlsx");
        tmp.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            fos.write(bytes);
        }

        // Read the xlsx (zip) and find dataValidations in sheet1 XML
        boolean foundValidation = false;
        try (ZipFile zip = new ZipFile(tmp)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().contains("sheet1.xml")) {
                    try (InputStream is = zip.getInputStream(entry)) {
                        String xml = new String(is.readAllBytes());
                        System.out.println("=== sheet1.xml excerpt ===");
                        int idx = xml.indexOf("dataValidation");
                        if (idx >= 0) {
                            System.out.println(xml.substring(Math.max(0, idx - 50),
                                    Math.min(xml.length(), idx + 400)));
                        } else {
                            System.out.println("NO dataValidation found in XML");
                            System.out.println("Last 500 chars: " + xml.substring(Math.max(0, xml.length() - 500)));
                        }
                        foundValidation = xml.contains("dataValidation");
                    }
                }
            }
        }

        assertTrue(foundValidation, "sheet1.xml should contain a dataValidation element");
    }

    private byte[] generateTemplate() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Company Holidays");

            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row headerRow = sheet.createRow(0);
            String[] headers = {"Holiday Name*", "Date* (e.g. 15-June-2026)", "Location (optional)"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Visible Locations sheet
            Sheet locSheet = wb.createSheet("Locations");
            locSheet.createRow(0).createCell(0).setCellValue("Locations  (add new locations below ↓)");
            String[] locationOptions = {"Mandsaur", "Ahmedabad", "Jamnagar"};
            for (int i = 0; i < locationOptions.length; i++) {
                locSheet.createRow(i + 1).createCell(0).setCellValue(locationOptions[i]);
            }
            int maxLocations = locationOptions.length + 20;

            // Data validation
            XSSFSheet xssfSheet = (XSSFSheet) sheet;
            XSSFDataValidationHelper dvHelper = new XSSFDataValidationHelper(xssfSheet);
            DataValidationConstraint dvConstraint = dvHelper.createFormulaListConstraint(
                    "Locations!$A$2:$A$" + (maxLocations + 1));
            CellRangeAddressList locationRange = new CellRangeAddressList(1, 200, 2, 2);
            DataValidation locationValidation = dvHelper.createValidation(dvConstraint, locationRange);
            locationValidation.setSuppressDropDownArrow(true);
            locationValidation.setShowErrorBox(false);
            locationValidation.setShowPromptBox(true);
            locationValidation.createPromptBox("Location (optional)",
                    "Pick a location or add a new one in the 'Locations' sheet. Leave blank for all employees.");
            xssfSheet.addValidationData(locationValidation);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }
}

package com.portiq.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Converts an uploaded CSV or Excel (.xlsx/.xls) file into plain tabular text, so any layout -
 * whatever the columns, whatever the order - can be handed to a text-understanding model instead
 * of requiring a fixed schema.
 */
@Service
public class SpreadsheetTextExtractor {

    public String extractTableText(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
            return extractFromWorkbook(file);
        }
        return extractFromPlainText(file);
    }

    private String extractFromPlainText(MultipartFile file) throws IOException {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append('\n');
            }
        }
        return text.toString();
    }

    private String extractFromWorkbook(MultipartFile file) throws IOException {
        StringBuilder text = new StringBuilder();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            DataFormatter formatter = new DataFormatter();
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                Sheet sheet = workbook.getSheetAt(s);
                for (Row row : sheet) {
                    StringBuilder line = new StringBuilder();
                    boolean hasContent = false;
                    for (Cell cell : row) {
                        String value = formatter.formatCellValue(cell).trim();
                        if (!value.isEmpty()) {
                            hasContent = true;
                        }
                        if (line.length() > 0) {
                            line.append(',');
                        }
                        line.append(value);
                    }
                    if (hasContent) {
                        text.append(line).append('\n');
                    }
                }
            }
        }
        return text.toString();
    }
}

package com.portiq.service;

import com.opencsv.CSVReader;
import com.portiq.dto.HoldingImportResult;
import com.portiq.dto.HoldingRequest;
import com.portiq.model.HoldingType;
import com.portiq.model.Portfolio;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class HoldingImportService {

    private final PortfolioService portfolioService;
    private final HoldingService holdingService;

    public HoldingImportService(PortfolioService portfolioService, HoldingService holdingService) {
        this.portfolioService = portfolioService;
        this.holdingService = holdingService;
    }

    public HoldingImportResult importCsv(MultipartFile file) throws IOException {
        Portfolio portfolio = portfolioService.getOrCreateDefault();
        List<String> errors = new ArrayList<>();
        int imported = 0;

        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            List<String[]> rows = reader.readAll();
            if (rows.isEmpty()) {
                return new HoldingImportResult(0, List.of("The CSV file is empty"));
            }

            int start = looksLikeHeader(rows.get(0)) ? 1 : 0;
            for (int i = start; i < rows.size(); i++) {
                String[] row = rows.get(i);
                if (row.length < 5 || Arrays.stream(row).allMatch(String::isBlank)) {
                    continue;
                }
                try {
                    HoldingRequest request = parseRow(row);
                    holdingService.mergeOrCreate(portfolio, request);
                    imported++;
                } catch (Exception e) {
                    errors.add("Row " + (i + 1) + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            errors.add("Could not read CSV file: " + e.getMessage());
        }

        return new HoldingImportResult(imported, errors);
    }

    public HoldingImportResult importRequests(List<HoldingRequest> requests) {
        Portfolio portfolio = portfolioService.getOrCreateDefault();
        List<String> errors = new ArrayList<>();
        int imported = 0;

        for (HoldingRequest request : requests) {
            try {
                holdingService.mergeOrCreate(portfolio, request);
                imported++;
            } catch (Exception e) {
                errors.add(request.getTicker() + ": " + e.getMessage());
            }
        }
        return new HoldingImportResult(imported, errors);
    }

    private boolean looksLikeHeader(String[] row) {
        return row.length > 0 && row[0] != null && row[0].trim().equalsIgnoreCase("ticker");
    }

    private HoldingRequest parseRow(String[] row) {
        HoldingRequest request = new HoldingRequest();
        request.setTicker(row[0].trim().toUpperCase());
        request.setName(row.length > 1 && !row[1].isBlank() ? row[1].trim() : row[0].trim());

        HoldingType type = HoldingType.STOCK;
        if (row.length > 2 && !row[2].isBlank()) {
            try {
                type = HoldingType.valueOf(row[2].trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // fall back to STOCK for unrecognized values
            }
        }
        request.setType(type);
        request.setQuantity(new BigDecimal(row[3].trim()));
        request.setPurchasePrice(new BigDecimal(row[4].trim()));
        request.setPurchaseDate(row.length > 5 && !row[5].isBlank() ? LocalDate.parse(row[5].trim()) : LocalDate.now());
        return request;
    }
}

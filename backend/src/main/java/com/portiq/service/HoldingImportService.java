package com.portiq.service;

import com.opencsv.CSVReader;
import com.portiq.dto.HoldingImportResult;
import com.portiq.dto.HoldingRequest;
import com.portiq.exception.InvalidRequestException;
import com.portiq.model.HoldingType;
import com.portiq.model.Portfolio;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class HoldingImportService {

    /**
     * Guards against an import that would take the whole request thread with it. A broker export
     * is a few hundred rows; a hundred thousand is either a mistake or an attack.
     */
    private static final int MAX_ROWS = 5000;

    private final PortfolioService portfolioService;
    private final HoldingService holdingService;
    private final Validator validator;

    public HoldingImportService(PortfolioService portfolioService, HoldingService holdingService,
                                 Validator validator) {
        this.portfolioService = portfolioService;
        this.holdingService = holdingService;
        this.validator = validator;
    }

    /**
     * Applies the same bean constraints a hand-typed holding goes through.
     *
     * <p>Imported rows reach the database on a path that skips {@code @Valid} entirely: a CSV is
     * parsed here, and the smart and image importers build their requests from whatever a language
     * model returned after reading an attacker-supplied file. Treating either as trusted would put
     * an unvalidated ticker into the database - and from there into an outbound URL - so both go
     * through the constraints explicitly before anything is saved.
     */
    private void validate(HoldingRequest request) {
        Set<ConstraintViolation<HoldingRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String detail = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .sorted()
                    .collect(Collectors.joining("; "));
            throw new InvalidRequestException(detail);
        }
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

            if (rows.size() > MAX_ROWS) {
                return new HoldingImportResult(0,
                        List.of("That file has " + rows.size() + " rows. The limit is " + MAX_ROWS + "."));
            }

            // Rows are parsed and validated first, then imported as one batch. Doing it row by row
            // meant a full table read per row; more importantly, a row that failed halfway through
            // left the earlier rows already committed, so a retry double-counted them.
            List<HoldingRequest> parsed = new ArrayList<>();
            int start = looksLikeHeader(rows.get(0)) ? 1 : 0;
            for (int i = start; i < rows.size(); i++) {
                String[] row = rows.get(i);
                if (row.length < 5 || Arrays.stream(row).allMatch(String::isBlank)) {
                    continue;
                }
                try {
                    HoldingRequest request = parseRow(row);
                    validate(request);
                    parsed.add(request);
                } catch (InvalidRequestException e) {
                    errors.add("Row " + (i + 1) + ": " + e.getMessage());
                }
            }

            if (!parsed.isEmpty()) {
                holdingService.mergeAll(portfolio, parsed);
                imported = parsed.size();
            }
        } catch (Exception e) {
            errors.add("Could not read CSV file: " + e.getMessage());
        }

        return new HoldingImportResult(imported, errors);
    }

    public HoldingImportResult importRequests(List<HoldingRequest> requests) {
        if (requests.size() > MAX_ROWS) {
            return new HoldingImportResult(0,
                    List.of("That file produced " + requests.size() + " holdings. The limit is " + MAX_ROWS + "."));
        }

        Portfolio portfolio = portfolioService.getOrCreateDefault();
        List<String> errors = new ArrayList<>();
        int imported = 0;

        List<HoldingRequest> valid = new ArrayList<>();
        for (HoldingRequest request : requests) {
            try {
                validate(request);
                valid.add(request);
            } catch (InvalidRequestException e) {
                errors.add(request.getTicker() + ": " + e.getMessage());
            }
        }

        if (!valid.isEmpty()) {
            holdingService.mergeAll(portfolio, valid);
            imported = valid.size();
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
        request.setQuantity(parseNumber(row[3], "Quantity"));
        request.setPurchasePrice(parseNumber(row[4], "Purchase price"));
        request.setPurchaseDate(parseDate(row.length > 5 ? row[5] : null));
        return request;
    }

    /**
     * Reads a numeric cell, saying which column was wrong and what was in it.
     *
     * <p>A bare {@code new BigDecimal(cell)} surfaced as
     * {@code Row 7: Character array is missing "exponent" mark} - a message about BigDecimal's
     * internals that tells the person holding the spreadsheet nothing about which cell to fix.
     *
     * <p>The tolerated formats are the ones real broker exports actually contain: thousands
     * separators, a currency symbol, and parenthesised negatives. Stripping them here is not
     * laxness - the alternative is a user hand-editing a 400-row export to remove commas.
     */
    private BigDecimal parseNumber(String cell, String columnName) {
        String raw = cell == null ? "" : cell.trim();
        if (raw.isEmpty()) {
            throw new InvalidRequestException(columnName + " is empty.");
        }

        boolean parenthesisedNegative = raw.startsWith("(") && raw.endsWith(")");
        String cleaned = raw.replaceAll("[,\\s ]", "")
                .replaceAll("^[(]|[)]$", "")
                .replaceAll("^[\\p{Sc}]", "");

        try {
            BigDecimal value = new BigDecimal(cleaned);
            return parenthesisedNegative ? value.negate() : value;
        } catch (NumberFormatException e) {
            throw new InvalidRequestException(columnName + " '" + raw + "' is not a number.");
        }
    }

    /** Reads the optional date cell, defaulting to today and naming the column when it is wrong. */
    private LocalDate parseDate(String cell) {
        String raw = cell == null ? "" : cell.trim();
        if (raw.isEmpty()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            throw new InvalidRequestException(
                    "Purchase date '" + raw + "' is not a date. Use the YYYY-MM-DD format.");
        }
    }
}

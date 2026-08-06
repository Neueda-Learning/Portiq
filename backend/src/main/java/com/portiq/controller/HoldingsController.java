package com.portiq.controller;

import com.portiq.dto.BulkDeleteRequest;
import com.portiq.dto.HoldingRequest;
import com.portiq.dto.PerformanceSummary;
import com.portiq.dto.PortfolioHistoryPoint;
import com.portiq.model.Holding;
import com.portiq.model.Portfolio;
import com.portiq.service.ExportService;
import com.portiq.service.HoldingImportService;
import com.portiq.service.HoldingService;
import com.portiq.service.PortfolioService;
import com.portiq.service.PriceHistoryService;
import com.portiq.service.SmartFileImportService;
import com.portiq.service.StatementScanService;
import com.portiq.service.UploadValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.client.RestClientException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Flat, cross-portfolio view of holdings backing the Dashboard and Reports pages, so the UI
 * does not need to navigate per-portfolio.
 */
@RestController
@RequestMapping("/api/holdings")
@Tag(name = "Holdings (flat)", description = "Cross-portfolio holdings, import, export, and history")
public class HoldingsController {

    private static final Logger log = LoggerFactory.getLogger(HoldingsController.class);

    private final HoldingService holdingService;
    private final PortfolioService portfolioService;
    private final HoldingImportService holdingImportService;
    private final StatementScanService statementScanService;
    private final SmartFileImportService smartFileImportService;
    private final ExportService exportService;
    private final PriceHistoryService priceHistoryService;
    private final UploadValidator uploadValidator;

    public HoldingsController(HoldingService holdingService, PortfolioService portfolioService,
                               HoldingImportService holdingImportService, StatementScanService statementScanService,
                               SmartFileImportService smartFileImportService, ExportService exportService,
                               PriceHistoryService priceHistoryService, UploadValidator uploadValidator) {
        this.holdingService = holdingService;
        this.portfolioService = portfolioService;
        this.holdingImportService = holdingImportService;
        this.statementScanService = statementScanService;
        this.smartFileImportService = smartFileImportService;
        this.exportService = exportService;
        this.priceHistoryService = priceHistoryService;
        this.uploadValidator = uploadValidator;
    }

    @GetMapping
    @Operation(summary = "Get performance for every holding across all portfolios")
    public PerformanceSummary getAllHoldings() {
        return holdingService.getAggregatePerformance();
    }

    @PostMapping
    @Operation(summary = "Add a holding (merges into an existing holding with the same ticker)")
    public ResponseEntity<Holding> addHolding(@Valid @RequestBody HoldingRequest request) {
        Portfolio portfolio = portfolioService.getOrCreateDefault();
        Holding created = holdingService.mergeOrCreate(portfolio, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a holding")
    public Holding updateHolding(@PathVariable Long id, @Valid @RequestBody HoldingRequest request) {
        return holdingService.updateHoldingById(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a holding")
    public ResponseEntity<Void> deleteHolding(@PathVariable Long id) {
        holdingService.removeHoldingById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk-delete")
    @Operation(summary = "Delete multiple holdings at once")
    public ResponseEntity<Map<String, Object>> bulkDeleteHoldings(@Valid @RequestBody BulkDeleteRequest request) {
        int deleted = holdingService.removeHoldingsByIds(request.getIds());
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }

    @PostMapping(value = "/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import holdings from a CSV or Excel file (any column layout), merging duplicate tickers")
    public ResponseEntity<?> importCsv(@RequestParam("file") MultipartFile file) {
        uploadValidator.validate(file, UploadValidator.Kind.SPREADSHEET);

        String filename = UploadValidator.safeFilename(file.getOriginalFilename()).toLowerCase();
        boolean isSpreadsheet = filename.endsWith(".xlsx") || filename.endsWith(".xls");

        if (smartFileImportService.isAvailable()) {
            try {
                List<HoldingRequest> extracted = smartFileImportService.extractHoldings(file);
                return ResponseEntity.ok(holdingImportService.importRequests(extracted));
            } catch (IOException e) {
                // The upload itself could not be read - the client's problem to retry.
                log.warn("Could not read the uploaded file for smart import", e);
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "That file could not be read. Try re-exporting it."));
            } catch (RestClientException e) {
                // The extraction model is unreachable or erroring. That is this server's
                // dependency failing, not a bad upload, and 400 sent the user off re-saving a
                // perfectly good spreadsheet.
                log.warn("The import model call failed", e);
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(Map.of("message", "The import service is unavailable right now. Try again shortly."));
            } catch (IllegalStateException e) {
                // Raised by the extractor for content it understood but could not use - an empty
                // file, or output that carried no transaction list. The message is written for
                // the user, so it is passed through as-is.
                return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
            }
        }

        if (isSpreadsheet) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message",
                    "Excel import needs the AI features configured on this server. Set "
                            + smartFileImportService.missingConfiguration()
                            + " and restart the backend, or upload a CSV instead."));
        }

        try {
            return ResponseEntity.ok(holdingImportService.importCsv(file));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Could not read the CSV file: " + e.getMessage()));
        }
    }

    @GetMapping("/import/csv/sample")
    @Operation(summary = "Download a sample CSV template for holding import")
    public ResponseEntity<org.springframework.core.io.Resource> sampleCsv() {
        org.springframework.core.io.Resource resource = new ClassPathResource("sample-holdings.csv");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=sample-holdings.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(resource);
    }

    @PostMapping(value = "/import/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import holdings by reading a statement screenshot")
    public ResponseEntity<?> importImage(@RequestParam("file") MultipartFile file) {
        if (!statementScanService.isAvailable()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("message",
                    "Statement image import is not configured on this server. Set "
                            + statementScanService.missingConfiguration() + " and restart the backend."));
        }
        uploadValidator.validate(file, UploadValidator.Kind.IMAGE);
        try {
            List<HoldingRequest> extracted = statementScanService.extractHoldings(file);
            return ResponseEntity.ok(holdingImportService.importRequests(extracted));
        } catch (IOException e) {
            log.warn("Could not read the uploaded statement image", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "That image could not be read. Try uploading it again."));
        } catch (RestClientException e) {
            log.warn("The statement scanning model call failed", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", "The scanning service is unavailable right now. Try again shortly."));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/export/csv")
    @Operation(summary = "Export the holdings report as CSV")
    public ResponseEntity<byte[]> exportCsv() {
        byte[] csv = exportService.toCsv(holdingService.getAggregatePerformance());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=portiq-holdings.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @GetMapping("/export/pdf")
    @Operation(summary = "Export the holdings report as PDF")
    public ResponseEntity<byte[]> exportPdf() {
        byte[] pdf = exportService.toPdf(holdingService.getAggregatePerformance());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=portiq-holdings.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/history")
    @Operation(summary = "Get portfolio value over time (range=1d|1w|1m|all)")
    public List<PortfolioHistoryPoint> getHistory(@RequestParam(defaultValue = "1m") String range) {
        List<Holding> holdings = holdingService.getAllHoldings();
        return priceHistoryService.getPortfolioHistory(holdings, range);
    }
}

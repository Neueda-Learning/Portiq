package com.portiq.controller;

import com.portiq.dto.PerformanceSummary;
import com.portiq.dto.PortfolioRequest;
import com.portiq.model.Portfolio;
import com.portiq.service.HoldingService;
import com.portiq.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portfolios")
@Tag(name = "Portfolios", description = "Portfolio management operations")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final HoldingService holdingService;

    public PortfolioController(PortfolioService portfolioService, HoldingService holdingService) {
        this.portfolioService = portfolioService;
        this.holdingService = holdingService;
    }

    @GetMapping
    @Operation(summary = "List all portfolios")
    public List<Portfolio> getAllPortfolios() {
        return portfolioService.getAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a portfolio by ID")
    public Portfolio getPortfolio(@PathVariable Long id) {
        return portfolioService.getById(id);
    }

    @PostMapping
    @Operation(summary = "Create a new portfolio")
    public ResponseEntity<Portfolio> createPortfolio(@Valid @RequestBody PortfolioRequest request) {
        Portfolio created = portfolioService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a portfolio")
    public Portfolio updatePortfolio(@PathVariable Long id, @Valid @RequestBody PortfolioRequest request) {
        return portfolioService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a portfolio")
    public ResponseEntity<Void> deletePortfolio(@PathVariable Long id) {
        portfolioService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/performance")
    @Operation(summary = "Get portfolio performance summary")
    public PerformanceSummary getPerformance(@PathVariable Long id) {
        return holdingService.getPerformance(id);
    }
}

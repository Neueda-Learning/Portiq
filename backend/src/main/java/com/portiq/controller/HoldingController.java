package com.portiq.controller;

import com.portiq.dto.HoldingRequest;
import com.portiq.model.Holding;
import com.portiq.service.HoldingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/portfolios/{portfolioId}/holdings")
@Tag(name = "Holdings", description = "Portfolio holding operations")
public class HoldingController {

    private final HoldingService holdingService;

    public HoldingController(HoldingService holdingService) {
        this.holdingService = holdingService;
    }

    @GetMapping
    @Operation(summary = "List all holdings in a portfolio")
    public List<Holding> getHoldings(@PathVariable Long portfolioId) {
        return holdingService.getHoldingsByPortfolio(portfolioId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a specific holding")
    public Holding getHolding(@PathVariable Long portfolioId, @PathVariable Long id) {
        return holdingService.getHolding(portfolioId, id);
    }

    @PostMapping
    @Operation(summary = "Add a holding to a portfolio")
    public ResponseEntity<Holding> addHolding(
            @PathVariable Long portfolioId,
            @Valid @RequestBody HoldingRequest request) {
        Holding created = holdingService.addHolding(portfolioId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a holding")
    public Holding updateHolding(
            @PathVariable Long portfolioId,
            @PathVariable Long id,
            @Valid @RequestBody HoldingRequest request) {
        return holdingService.updateHolding(portfolioId, id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove a holding from a portfolio")
    public ResponseEntity<Void> removeHolding(@PathVariable Long portfolioId, @PathVariable Long id) {
        holdingService.removeHolding(portfolioId, id);
        return ResponseEntity.noContent().build();
    }
}

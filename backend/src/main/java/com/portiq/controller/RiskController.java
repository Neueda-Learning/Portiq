package com.portiq.controller;

import com.portiq.dto.PortfolioRiskReport;
import com.portiq.dto.StockRisk;
import com.portiq.service.RiskAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Risk scoring for the portfolio and for individual stocks. Needs no API key - the numbers come
 * from Yahoo Finance price history and are computed locally.
 */
@RestController
@RequestMapping("/api/risk")
@Tag(name = "Risk", description = "Portfolio and per-stock risk analysis with scores and metrics")
public class RiskController {

    private final RiskAnalysisService riskAnalysisService;

    public RiskController(RiskAnalysisService riskAnalysisService) {
        this.riskAnalysisService = riskAnalysisService;
    }

    @GetMapping
    @Operation(summary = "Risk report for the whole portfolio, with a per-holding breakdown")
    public PortfolioRiskReport getPortfolioRisk() {
        return riskAnalysisService.getPortfolioRisk();
    }

    @GetMapping("/{ticker}")
    @Operation(summary = "Risk score and metrics for a single stock, held or not")
    public StockRisk getStockRisk(@PathVariable String ticker) {
        return riskAnalysisService.analyseTicker(ticker);
    }
}

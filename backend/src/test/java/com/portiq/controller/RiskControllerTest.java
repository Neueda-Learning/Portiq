package com.portiq.controller;

import com.portiq.dto.PortfolioRiskReport;
import com.portiq.dto.RiskMetrics;
import com.portiq.dto.StockRisk;
import com.portiq.exception.GlobalExceptionHandler;
import com.portiq.model.DataQuality;
import com.portiq.model.HoldingType;
import com.portiq.model.RiskLevel;
import com.portiq.service.RiskAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RiskControllerTest {

    private MockMvc mockMvc;

    @Mock
    private RiskAnalysisService riskAnalysisService;

    @InjectMocks
    private RiskController riskController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(riskController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getPortfolioRisk_returns200WithScoreAndBreakdown() throws Exception {
        when(riskAnalysisService.getPortfolioRisk()).thenReturn(report());

        mockMvc.perform(get("/api/risk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallRiskScore").value(48.5))
                .andExpect(jsonPath("$.riskLevel").value("MODERATE"))
                .andExpect(jsonPath("$.diversificationScore").value(72.0))
                .andExpect(jsonPath("$.benchmark").value("^NSEI"))
                .andExpect(jsonPath("$.holdings[0].ticker").value("AAA"))
                .andExpect(jsonPath("$.holdings[0].metrics.annualisedVolatilityPercent").value(28.4))
                .andExpect(jsonPath("$.allocationByType.STOCK").value(100.0))
                .andExpect(jsonPath("$.disclaimer").exists());
    }

    @Test
    void getStockRisk_returns200WithMetrics() throws Exception {
        when(riskAnalysisService.analyseTicker("AAA")).thenReturn(stockRisk());

        mockMvc.perform(get("/api/risk/AAA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAA"))
                .andExpect(jsonPath("$.riskScore").value(52.0))
                .andExpect(jsonPath("$.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.dataQuality").value("SUFFICIENT"))
                .andExpect(jsonPath("$.drivers[0]").exists())
                .andExpect(jsonPath("$.metrics.beta").value(1.15));
    }

    @Test
    void getStockRisk_unresolvableTicker_returns200MarkedUnavailable() throws Exception {
        StockRisk unknown = new StockRisk();
        unknown.setTicker("NOPE");
        unknown.setDataQuality(DataQuality.UNAVAILABLE);
        unknown.setDrivers(List.of("No price history available for NOPE"));
        unknown.setMetrics(new RiskMetrics());
        when(riskAnalysisService.analyseTicker("NOPE")).thenReturn(unknown);

        mockMvc.perform(get("/api/risk/NOPE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataQuality").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.riskScore").doesNotExist());
    }

    private static PortfolioRiskReport report() {
        PortfolioRiskReport report = new PortfolioRiskReport();
        report.setOverallRiskScore(new BigDecimal("48.50"));
        report.setRiskLevel(RiskLevel.MODERATE);
        report.setPortfolioVolatilityPercent(new BigDecimal("19.20"));
        report.setPortfolioBeta(new BigDecimal("1.05"));
        report.setDiversificationScore(new BigDecimal("72.00"));
        report.setConcentrationHhi(new BigDecimal("0.31"));
        report.setTopHoldingTicker("AAA");
        report.setTopHoldingWeightPercent(new BigDecimal("42.00"));
        report.setHoldingsCount(3);
        report.setAllocationByType(Map.of(HoldingType.STOCK, new BigDecimal("100.00")));
        report.setHoldings(List.of(stockRisk()));
        report.setHighestRiskHoldings(List.of(stockRisk()));
        report.setWarnings(List.of("AAA is 42.00% of your portfolio"));
        report.setBenchmark("^NSEI");
        report.setGeneratedAt(Instant.parse("2026-08-06T00:00:00Z"));
        report.setDisclaimer(RiskAnalysisService.DISCLAIMER);
        return report;
    }

    private static StockRisk stockRisk() {
        RiskMetrics metrics = new RiskMetrics();
        metrics.setAnnualisedVolatilityPercent(new BigDecimal("28.40"));
        metrics.setBeta(new BigDecimal("1.15"));
        metrics.setMaxDrawdownPercent(new BigDecimal("22.10"));
        metrics.setValueAtRisk95Percent(new BigDecimal("2.60"));
        metrics.setDataPoints(248);

        StockRisk risk = new StockRisk();
        risk.setTicker("AAA");
        risk.setName("Alpha");
        risk.setCurrentPrice(new BigDecimal("110.00"));
        risk.setWeightPercent(new BigDecimal("42.00"));
        risk.setRiskScore(new BigDecimal("52.00"));
        risk.setRiskLevel(RiskLevel.HIGH);
        risk.setDataQuality(DataQuality.SUFFICIENT);
        risk.setDrivers(List.of("Beta of 1.15 means it amplifies market moves"));
        risk.setMetrics(metrics);
        return risk;
    }
}

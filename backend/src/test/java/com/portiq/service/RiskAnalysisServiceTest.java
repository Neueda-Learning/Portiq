package com.portiq.service;

import com.portiq.dto.DailySeries;
import com.portiq.dto.HoldingPerformance;
import com.portiq.dto.PerformanceSummary;
import com.portiq.dto.PortfolioRiskReport;
import com.portiq.dto.StockRisk;
import com.portiq.model.DataQuality;
import com.portiq.model.HoldingType;
import com.portiq.model.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RiskAnalysisServiceTest {

    private static final String BENCHMARK = "^NSEI";

    @Mock
    private HoldingService holdingService;

    @Mock
    private MarketDataFetcher marketDataFetcher;

    private RiskAnalysisService riskAnalysisService;

    @BeforeEach
    void setUp() {
        // The calculator is a pure function object - mocking it would test nothing, so it is real.
        riskAnalysisService = new RiskAnalysisService(holdingService, marketDataFetcher, new MetricsCalculator());
        ReflectionTestUtils.setField(riskAnalysisService, "benchmarkTicker", BENCHMARK);
        ReflectionTestUtils.setField(riskAnalysisService, "riskFreeRatePercent", 6.5);

        when(marketDataFetcher.fetchDailySeries(BENCHMARK))
                .thenReturn(series(BENCHMARK, walk(100, 0.01, 260)));
    }

    @Test
    void getPortfolioRisk_emptyPortfolio_returnsZeroedReportNotNaN() {
        when(holdingService.getAggregatePerformance()).thenReturn(summary(List.of()));

        PortfolioRiskReport report = riskAnalysisService.getPortfolioRisk();

        assertThat(report.getHoldingsCount()).isZero();
        assertThat(report.getOverallRiskScore()).isEqualByComparingTo("0.00");
        assertThat(report.getRiskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(report.getHoldings()).isEmpty();
        assertThat(report.getWarnings()).hasSize(1);
        assertThat(report.getDisclaimer()).isEqualTo(RiskAnalysisService.DISCLAIMER);
    }

    @Test
    void getPortfolioRisk_weightsSumToOneHundred() {
        stubHoldings(
                holding("AAA", "Alpha", "6000"),
                holding("BBB", "Beta", "3000"),
                holding("CCC", "Gamma", "1000"));

        PortfolioRiskReport report = riskAnalysisService.getPortfolioRisk();

        double total = report.getHoldings().stream()
                .mapToDouble(risk -> risk.getWeightPercent().doubleValue())
                .sum();

        assertThat(total).isCloseTo(100, org.assertj.core.data.Offset.offset(0.05));
        assertThat(report.getTopHoldingTicker()).isEqualTo("AAA");
        assertThat(report.getTopHoldingWeightPercent().doubleValue()).isCloseTo(60, org.assertj.core.data.Offset.offset(0.05));
    }

    @Test
    void getPortfolioRisk_producesScoresAndLevelsForEveryHolding() {
        stubHoldings(holding("AAA", "Alpha", "5000"), holding("BBB", "Beta", "5000"));

        PortfolioRiskReport report = riskAnalysisService.getPortfolioRisk();

        assertThat(report.getHoldings()).allSatisfy(risk -> {
            assertThat(risk.getRiskScore()).isNotNull();
            assertThat(risk.getRiskScore().doubleValue()).isBetween(0.0, 100.0);
            assertThat(risk.getRiskLevel()).isNotNull();
            assertThat(risk.getDataQuality()).isEqualTo(DataQuality.SUFFICIENT);
            assertThat(risk.getDrivers()).isNotEmpty();
        });
        assertThat(report.getOverallRiskScore()).isNotNull();
        assertThat(report.getBenchmark()).isEqualTo(BENCHMARK);
    }

    /**
     * The claim the portfolio volatility figure exists to make: two holdings that move against each
     * other are jointly calmer than either alone. A weighted average of their volatilities could
     * never show this, which is why the report builds a synthetic return series instead.
     */
    @Test
    void getPortfolioRisk_offsettingHoldings_portfolioVolatilityBeatsEitherHolding() {
        double[] up = alternatingCloses(100, 0.02, 260);
        double[] down = alternatingCloses(100, -0.02, 260);

        when(marketDataFetcher.fetchDailySeries("AAA")).thenReturn(series("AAA", up));
        when(marketDataFetcher.fetchDailySeries("BBB")).thenReturn(series("BBB", down));
        when(holdingService.getAggregatePerformance()).thenReturn(
                summary(List.of(holding("AAA", "Alpha", "5000"), holding("BBB", "Beta", "5000"))));

        PortfolioRiskReport report = riskAnalysisService.getPortfolioRisk();

        double worstHolding = report.getHoldings().stream()
                .mapToDouble(r -> r.getMetrics().getAnnualisedVolatilityPercent().doubleValue())
                .max()
                .orElseThrow();

        assertThat(worstHolding).isGreaterThan(20);
        assertThat(report.getPortfolioVolatilityPercent().doubleValue()).isLessThan(worstHolding);
    }

    @Test
    void getPortfolioRisk_evenSplit_scoresDiversificationHighly() {
        stubHoldings(holding("AAA", "Alpha", "5000"), holding("BBB", "Beta", "5000"));

        PortfolioRiskReport report = riskAnalysisService.getPortfolioRisk();

        assertThat(report.getDiversificationScore().doubleValue()).isGreaterThan(95);
        assertThat(report.getConcentrationHhi().doubleValue()).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void getPortfolioRisk_oversizedPosition_raisesConcentrationWarning() {
        stubHoldings(holding("AAA", "Alpha", "9000"), holding("BBB", "Beta", "1000"));

        PortfolioRiskReport report = riskAnalysisService.getPortfolioRisk();

        assertThat(report.getWarnings()).anyMatch(warning -> warning.startsWith("AAA is 90.00%"));
        assertThat(report.getDiversificationScore().doubleValue()).isLessThan(50);
    }

    @Test
    void getPortfolioRisk_allocationByType_reflectsValueSplit() {
        HoldingPerformance stock = holding("AAA", "Alpha", "7500");
        HoldingPerformance cash = holding("CASH", "Cash", "2500");
        cash.setType(HoldingType.CASH);
        stubHoldings(stock, cash);

        PortfolioRiskReport report = riskAnalysisService.getPortfolioRisk();

        assertThat(report.getAllocationByType())
                .containsEntry(HoldingType.STOCK, new BigDecimal("75.00"))
                .containsEntry(HoldingType.CASH, new BigDecimal("25.00"));
    }

    @Test
    void buildStockRisk_cash_scoresZeroWithoutNeedingPriceHistory() {
        StockRisk risk = riskAnalysisService.buildStockRisk("CASH", "Cash", HoldingType.CASH,
                new BigDecimal("1"), 20, DailySeries.empty("CASH"), DailySeries.empty(BENCHMARK));

        assertThat(risk.getRiskScore()).isEqualByComparingTo("0.00");
        assertThat(risk.getRiskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(risk.getDataQuality()).isEqualTo(DataQuality.SUFFICIENT);
        assertThat(risk.getDrivers()).containsExactly("Cash carries no market risk");
    }

    @Test
    void buildStockRisk_noPriceHistory_reportsUnavailableInsteadOfAFakeScore() {
        StockRisk risk = riskAnalysisService.buildStockRisk("NOPE", "Unknown", HoldingType.STOCK,
                null, 0, DailySeries.empty("NOPE"), DailySeries.empty(BENCHMARK));

        assertThat(risk.getDataQuality()).isEqualTo(DataQuality.UNAVAILABLE);
        assertThat(risk.getRiskScore()).isNull();
        assertThat(risk.getRiskLevel()).isNull();
        assertThat(risk.getDrivers()).containsExactly("No price history available for NOPE");
    }

    @Test
    void buildStockRisk_shortHistory_reportsLimitedButStillScores() {
        StockRisk risk = riskAnalysisService.buildStockRisk("NEW", "Newly Listed", HoldingType.STOCK,
                new BigDecimal("120"), 10, series("NEW", walk(100, 0.02, 12)), DailySeries.empty(BENCHMARK));

        assertThat(risk.getDataQuality()).isEqualTo(DataQuality.LIMITED);
        assertThat(risk.getRiskScore()).isNotNull();
        // Volatility and beta need 30 observations; drawdown and concentration still apply.
        assertThat(risk.getMetrics().getAnnualisedVolatilityPercent()).isNull();
        assertThat(risk.getMetrics().getBeta()).isNull();
        assertThat(risk.getMetrics().getMaxDrawdownPercent()).isNotNull();
        assertThat(risk.getDrivers()).anyMatch(driver -> driver.contains("12 days of price history"));
    }

    @Test
    void buildStockRisk_calmStockScoresLowerThanWildOne() {
        StockRisk calm = riskAnalysisService.buildStockRisk("CALM", "Calm Co", HoldingType.STOCK,
                new BigDecimal("100"), 10, series("CALM", alternatingCloses(100, 0.002, 260)), DailySeries.empty(BENCHMARK));

        StockRisk wild = riskAnalysisService.buildStockRisk("WILD", "Wild Co", HoldingType.STOCK,
                new BigDecimal("100"), 10, series("WILD", walk(100, 0.06, 260)), DailySeries.empty(BENCHMARK));

        assertThat(calm.getRiskScore().doubleValue()).isLessThan(wild.getRiskScore().doubleValue());
    }

    @Test
    void analyseTicker_unheldTicker_hasZeroWeight() {
        when(holdingService.getAggregatePerformance()).thenReturn(summary(List.of()));
        when(marketDataFetcher.fetchDailySeries("ZZZ")).thenReturn(series("ZZZ", walk(100, 0.015, 260)));

        StockRisk risk = riskAnalysisService.analyseTicker("zzz");

        assertThat(risk.getTicker()).isEqualTo("ZZZ");
        assertThat(risk.getWeightPercent()).isEqualByComparingTo("0.00");
        assertThat(risk.getRiskScore()).isNotNull();
    }

    private void stubHoldings(HoldingPerformance... holdings) {
        for (HoldingPerformance holding : holdings) {
            when(marketDataFetcher.fetchDailySeries(holding.getTicker()))
                    .thenReturn(series(holding.getTicker(), walk(100, 0.015, 260)));
        }
        when(holdingService.getAggregatePerformance()).thenReturn(summary(List.of(holdings)));
    }

    private static PerformanceSummary summary(List<HoldingPerformance> holdings) {
        BigDecimal total = holdings.stream()
                .map(HoldingPerformance::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        PerformanceSummary summary = new PerformanceSummary();
        summary.setPortfolioName("All Holdings");
        summary.setTotalCurrentValue(total);
        summary.setTotalCostBasis(total);
        summary.setTotalGainLoss(BigDecimal.ZERO);
        summary.setGainLossPercent(BigDecimal.ZERO);
        summary.setHoldings(holdings);
        return summary;
    }

    private static HoldingPerformance holding(String ticker, String name, String currentValue) {
        HoldingPerformance holding = new HoldingPerformance();
        holding.setTicker(ticker);
        holding.setName(name);
        holding.setType(HoldingType.STOCK);
        holding.setQuantity(new BigDecimal("10"));
        holding.setPurchasePrice(new BigDecimal("100"));
        holding.setCurrentPrice(new BigDecimal("110"));
        holding.setCostBasis(new BigDecimal(currentValue));
        holding.setCurrentValue(new BigDecimal(currentValue));
        holding.setGainLoss(BigDecimal.ZERO);
        holding.setGainLossPercent(BigDecimal.ZERO);
        return holding;
    }

    private static DailySeries series(String ticker, double[] closes) {
        List<Long> timestamps = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        List<Long> volumes = new ArrayList<>();

        for (int i = 0; i < closes.length; i++) {
            timestamps.add(1_700_000_000L + (i * 86_400L));
            values.add(BigDecimal.valueOf(closes[i]));
            volumes.add(1_000_000L);
        }
        BigDecimal current = values.isEmpty() ? null : values.get(values.size() - 1);
        return new DailySeries(ticker, timestamps, values, volumes, current);
    }

    /** Deterministic pseudo-random walk, so tests never flake on a real random seed. */
    private static double[] walk(double start, double magnitude, int length) {
        double[] closes = new double[length];
        closes[0] = start;
        for (int i = 1; i < length; i++) {
            double move = Math.sin(i * 1.7) * magnitude;
            closes[i] = Math.max(1, closes[i - 1] * (1 + move));
        }
        return closes;
    }

    private static double[] alternatingCloses(double start, double magnitude, int length) {
        double[] closes = new double[length];
        closes[0] = start;
        for (int i = 1; i < length; i++) {
            closes[i] = closes[i - 1] * (1 + (i % 2 == 0 ? -magnitude : magnitude));
        }
        return closes;
    }
}

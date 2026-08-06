package com.portiq.service;

import com.portiq.dto.DailySeries;
import com.portiq.dto.HoldingPerformance;
import com.portiq.dto.PerformanceSummary;
import com.portiq.dto.RecommendationResponse;
import com.portiq.dto.StockRecommendation;
import com.portiq.model.DataQuality;
import com.portiq.model.HoldingType;
import com.portiq.model.RecommendationAction;
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
class RecommendationServiceTest {

    private static final String BENCHMARK = "^NSEI";

    @Mock
    private HoldingService holdingService;

    @Mock
    private MarketDataFetcher marketDataFetcher;

    @Mock
    private RecommendationNarrator narrator;

    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        // Real calculator and real risk service - the point of this test is that the two features
        // agree with each other, which a mocked RiskAnalysisService would hide.
        MetricsCalculator calculator = new MetricsCalculator();
        RiskAnalysisService riskAnalysisService =
                new RiskAnalysisService(holdingService, marketDataFetcher, calculator);
        ReflectionTestUtils.setField(riskAnalysisService, "benchmarkTicker", BENCHMARK);
        ReflectionTestUtils.setField(riskAnalysisService, "riskFreeRatePercent", 6.5);

        recommendationService = new RecommendationService(
                holdingService, marketDataFetcher, calculator, riskAnalysisService, narrator);
        ReflectionTestUtils.setField(recommendationService, "universeProperty", "IDEA1,IDEA2,IDEA3");
        ReflectionTestUtils.setField(recommendationService, "maxIdeas", 5);

        when(marketDataFetcher.fetchDailySeries(BENCHMARK))
                .thenReturn(series(BENCHMARK, rising(100, 260)));
    }

    @Test
    void getRecommendations_stockInStrongUptrend_recommendsAccumulate() {
        // Five evenly sized positions, so no single one trips the concentration override and the
        // action reflects the chart rather than the position size.
        stubSeries("AAA", rising(100, 260));
        List<HoldingPerformance> holdings = new ArrayList<>(List.of(holding("AAA", "Alpha", "2000", "15")));
        for (String filler : List.of("F1", "F2", "F3", "F4")) {
            stubSeries(filler, drifting(100, 260));
            holdings.add(holding(filler, filler, "2000", "0"));
        }
        when(holdingService.getAggregatePerformance()).thenReturn(summary(holdings));

        RecommendationResponse response = recommendationService.getRecommendations(false, false);

        StockRecommendation rec = find(response.getHoldings(), "AAA");
        assertThat(rec.getWeightPercent().doubleValue()).isLessThan(25);
        assertThat(rec.getTrend()).isEqualTo("UPTREND");
        assertThat(rec.getAction()).isIn(RecommendationAction.ACCUMULATE, RecommendationAction.HOLD);
        assertThat(rec.getOpportunityScore().doubleValue()).isGreaterThan(15);
        assertThat(rec.isHeld()).isTrue();
    }

    @Test
    void getRecommendations_stockInSustainedDowntrend_recommendsTrimOrSell() {
        stubSeries("AAA", falling(200, 260));
        when(holdingService.getAggregatePerformance())
                .thenReturn(summary(List.of(holding("AAA", "Alpha", "5000", "-30"))));

        RecommendationResponse response = recommendationService.getRecommendations(false, false);

        StockRecommendation rec = response.getHoldings().get(0);
        assertThat(rec.getTrend()).isEqualTo("DOWNTREND");
        assertThat(rec.getAction()).isIn(RecommendationAction.TRIM, RecommendationAction.SELL);
        assertThat(rec.getOpportunityScore().doubleValue()).isNegative();
    }

    /**
     * The concentration override: a position past the comfort line gets trimmed even when the
     * stock itself is screening reasonably, because position sizing is its own decision.
     */
    @Test
    void getRecommendations_oversizedPosition_forcesTrimDespiteDecentSignals() {
        stubSeries("BIG", drifting(100, 260));
        stubSeries("SMALL", drifting(100, 260));
        when(holdingService.getAggregatePerformance()).thenReturn(summary(List.of(
                holding("BIG", "Big Co", "9000", "5"),
                holding("SMALL", "Small Co", "1000", "5"))));

        RecommendationResponse response = recommendationService.getRecommendations(false, false);

        StockRecommendation big = find(response.getHoldings(), "BIG");
        assertThat(big.getWeightPercent().doubleValue()).isGreaterThan(25);
        assertThat(big.getAction()).isEqualTo(RecommendationAction.TRIM);
        assertThat(big.getSignals()).anyMatch(signal -> signal.contains("concentration comfort line"));
        assertThat(big.getSuggestedWeightPercent().doubleValue()).isLessThan(big.getWeightPercent().doubleValue());
    }

    @Test
    void trimAlwaysSuggestsASmallerPositionThanCurrentlyHeld() {
        // A small position in a falling stock still gets TRIM. Capping the suggestion at the
        // equal-weight baseline alone would leave it unchanged, which is not a trim at all.
        stubSeries("SMALLFALL", falling(200, 260));
        for (String filler : List.of("F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8")) {
            stubSeries(filler, drifting(100, 260));
        }
        List<HoldingPerformance> holdings = new ArrayList<>(List.of(holding("SMALLFALL", "Small Faller", "400", "-40")));
        for (String filler : List.of("F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8")) {
            holdings.add(holding(filler, filler, "1200", "0"));
        }
        when(holdingService.getAggregatePerformance()).thenReturn(summary(holdings));

        StockRecommendation rec = find(recommendationService.getRecommendations(false, false).getHoldings(),
                "SMALLFALL");

        assertThat(rec.getAction()).isIn(RecommendationAction.TRIM, RecommendationAction.SELL);
        assertThat(rec.getWeightPercent().doubleValue()).isLessThan(12.5);
        assertThat(rec.getSuggestedWeightPercent().doubleValue())
                .isLessThan(rec.getWeightPercent().doubleValue());
    }

    @Test
    void unavailableHistory_reasonSaysSoRatherThanReadingLikeAConsideredHold() {
        when(marketDataFetcher.fetchDailySeries("GONE")).thenReturn(DailySeries.empty("GONE"));
        when(holdingService.getAggregatePerformance())
                .thenReturn(summary(List.of(holding("GONE", "Delisted Co", "5000", "0"))));

        StockRecommendation rec = recommendationService.getRecommendations(false, false).getHoldings().get(0);

        assertThat(rec.getDataQuality()).isEqualTo(DataQuality.UNAVAILABLE);
        assertThat(rec.getConfidence()).isEqualByComparingTo("0.00");
        assertThat(rec.getReason()).startsWith("No price data is available for");
        assertThat(rec.getReason()).doesNotContain("Nothing in the numbers");
        // The lone signal would only restate the sentence, so it is not appended.
        assertThat(rec.getReason()).doesNotContain("cannot form a view");
    }

    @Test
    void getRecommendations_excludesAlreadyHeldTickersFromIdeas() {
        ReflectionTestUtils.setField(recommendationService, "universeProperty", "AAA,IDEA1");
        stubSeries("AAA", rising(100, 260));
        stubSeries("IDEA1", rising(100, 260));
        when(holdingService.getAggregatePerformance())
                .thenReturn(summary(List.of(holding("AAA", "Alpha", "5000", "10"))));

        RecommendationResponse response = recommendationService.getRecommendations(true, false);

        assertThat(response.getIdeas()).extracting(StockRecommendation::getTicker).containsExactly("IDEA1");
        assertThat(response.getIdeas()).allSatisfy(idea -> assertThat(idea.isHeld()).isFalse());
    }

    @Test
    void getRecommendations_ideasOnlyContainBuys() {
        stubSeries("AAA", drifting(100, 260));
        stubSeries("IDEA1", rising(100, 260));
        stubSeries("IDEA2", falling(200, 260));
        stubSeries("IDEA3", falling(200, 260));
        when(holdingService.getAggregatePerformance())
                .thenReturn(summary(List.of(holding("AAA", "Alpha", "5000", "0"))));

        RecommendationResponse response = recommendationService.getRecommendations(true, false);

        assertThat(response.getIdeas()).isNotEmpty();
        assertThat(response.getIdeas())
                .allSatisfy(idea -> assertThat(idea.getAction()).isEqualTo(RecommendationAction.BUY));
        assertThat(response.getIdeas()).extracting(StockRecommendation::getTicker).doesNotContain("IDEA2", "IDEA3");
    }

    @Test
    void getRecommendations_respectsMaxIdeasLimit() {
        ReflectionTestUtils.setField(recommendationService, "maxIdeas", 2);
        stubSeries("IDEA1", rising(100, 260));
        stubSeries("IDEA2", rising(110, 260));
        stubSeries("IDEA3", rising(120, 260));
        when(holdingService.getAggregatePerformance()).thenReturn(summary(List.of()));

        RecommendationResponse response = recommendationService.getRecommendations(true, false);

        assertThat(response.getIdeas()).hasSize(2);
    }

    @Test
    void getRecommendations_ideasSkipped_whenNotRequested() {
        stubSeries("AAA", rising(100, 260));
        when(holdingService.getAggregatePerformance())
                .thenReturn(summary(List.of(holding("AAA", "Alpha", "5000", "10"))));

        RecommendationResponse response = recommendationService.getRecommendations(false, false);

        assertThat(response.getIdeas()).isEmpty();
    }

    @Test
    void getRecommendations_withoutLlm_stillProducesReadableReasons() {
        stubSeries("AAA", rising(100, 260));
        when(holdingService.getAggregatePerformance())
                .thenReturn(summary(List.of(holding("AAA", "Alpha", "5000", "12"))));

        RecommendationResponse response = recommendationService.getRecommendations(false, true);

        assertThat(response.isLlmNarrated()).isFalse();
        assertThat(response.getHoldings().get(0).getReason()).isNotBlank();
        assertThat(response.getHoldings().get(0).getSignals()).isNotEmpty();
        assertThat(response.getDisclaimer()).isEqualTo(RiskAnalysisService.DISCLAIMER);
    }

    @Test
    void getRecommendations_cashHoldings_areExcluded() {
        HoldingPerformance cash = holding("CASH", "Cash", "5000", "0");
        cash.setType(HoldingType.CASH);
        stubSeries("AAA", rising(100, 260));
        when(holdingService.getAggregatePerformance())
                .thenReturn(summary(List.of(holding("AAA", "Alpha", "5000", "10"), cash)));

        RecommendationResponse response = recommendationService.getRecommendations(false, false);

        assertThat(response.getHoldings()).extracting(StockRecommendation::getTicker).containsExactly("AAA");
    }

    @Test
    void getRecommendations_emptyPortfolio_returnsEmptyHoldingsNotAnError() {
        when(holdingService.getAggregatePerformance()).thenReturn(summary(List.of()));

        RecommendationResponse response = recommendationService.getRecommendations(false, false);

        assertThat(response.getHoldings()).isEmpty();
        assertThat(response.getGeneratedAt()).isNotNull();
    }

    @Test
    void recommendTicker_unknownTicker_avoidsWithZeroConfidence() {
        when(holdingService.getAggregatePerformance()).thenReturn(summary(List.of()));
        when(marketDataFetcher.fetchDailySeries("NOPE")).thenReturn(DailySeries.empty("NOPE"));

        StockRecommendation rec = recommendationService.recommendTicker("nope");

        assertThat(rec.getTicker()).isEqualTo("NOPE");
        assertThat(rec.getDataQuality()).isEqualTo(DataQuality.UNAVAILABLE);
        assertThat(rec.getAction()).isEqualTo(RecommendationAction.AVOID);
        assertThat(rec.getConfidence()).isEqualByComparingTo("0.00");
        assertThat(rec.getSignals()).contains("No price history available - cannot form a view");
    }

    @Test
    void recommendTicker_heldTicker_carriesPortfolioContext() {
        stubSeries("AAA", rising(100, 260));
        when(holdingService.getAggregatePerformance())
                .thenReturn(summary(List.of(holding("AAA", "Alpha", "5000", "20"))));

        StockRecommendation rec = recommendationService.recommendTicker("aaa");

        assertThat(rec.isHeld()).isTrue();
        assertThat(rec.getWeightPercent()).isEqualByComparingTo("100.00");
        assertThat(rec.getCurrentValue()).isEqualByComparingTo("5000");
    }

    @Test
    void recommendations_riskFieldsMatchTheRiskAnalysisFeature() {
        stubSeries("AAA", drifting(100, 260));
        when(holdingService.getAggregatePerformance())
                .thenReturn(summary(List.of(holding("AAA", "Alpha", "5000", "3"))));

        StockRecommendation rec = recommendationService.getRecommendations(false, false).getHoldings().get(0);

        assertThat(rec.getRiskScore()).isNotNull();
        assertThat(rec.getRiskLevel()).isNotNull();
        assertThat(rec.getDataQuality()).isEqualTo(DataQuality.SUFFICIENT);
        assertThat(rec.getAnnualisedVolatilityPercent()).isNotNull();
    }

    private void stubSeries(String ticker, double[] closes) {
        when(marketDataFetcher.fetchDailySeries(ticker)).thenReturn(series(ticker, closes));
    }

    private static StockRecommendation find(List<StockRecommendation> recommendations, String ticker) {
        return recommendations.stream()
                .filter(rec -> rec.getTicker().equals(ticker))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No recommendation for " + ticker));
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

    private static HoldingPerformance holding(String ticker, String name, String value, String gainLossPercent) {
        HoldingPerformance holding = new HoldingPerformance();
        holding.setTicker(ticker);
        holding.setName(name);
        holding.setType(HoldingType.STOCK);
        holding.setQuantity(new BigDecimal("10"));
        holding.setPurchasePrice(new BigDecimal("100"));
        holding.setCurrentPrice(new BigDecimal("110"));
        holding.setCostBasis(new BigDecimal(value));
        holding.setCurrentValue(new BigDecimal(value));
        holding.setGainLoss(BigDecimal.ZERO);
        holding.setGainLossPercent(new BigDecimal(gainLossPercent));
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

    /** Steady climb with mild noise: price above both averages, positive momentum. */
    private static double[] rising(double start, int length) {
        double[] closes = new double[length];
        closes[0] = start;
        for (int i = 1; i < length; i++) {
            closes[i] = closes[i - 1] * (1 + 0.003 + (Math.sin(i * 1.3) * 0.004));
        }
        return closes;
    }

    /** Steady decline: price below the 200-day average, negative momentum. */
    private static double[] falling(double start, int length) {
        double[] closes = new double[length];
        closes[0] = start;
        for (int i = 1; i < length; i++) {
            closes[i] = Math.max(1, closes[i - 1] * (1 - 0.003 + (Math.sin(i * 1.3) * 0.004)));
        }
        return closes;
    }

    /** Goes nowhere in particular - used when the test cares about position size, not the chart. */
    private static double[] drifting(double start, int length) {
        double[] closes = new double[length];
        closes[0] = start;
        for (int i = 1; i < length; i++) {
            closes[i] = closes[i - 1] * (1 + (Math.sin(i * 1.1) * 0.006));
        }
        return closes;
    }
}

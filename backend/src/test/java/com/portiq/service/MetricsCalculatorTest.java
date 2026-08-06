package com.portiq.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Fixtures here are hand-computed on purpose - if one of these fails the maths is wrong, not the
 * wiring, which is the whole reason this class has no Spring dependencies.
 */
class MetricsCalculatorTest {

    private static final double TOLERANCE = 1e-6;

    private final MetricsCalculator calculator = new MetricsCalculator();

    @Test
    void dailyReturns_computesDayOverDayChange() {
        double[] returns = calculator.dailyReturns(new double[]{100, 110, 99});

        assertThat(returns).hasSize(2);
        assertThat(returns[0]).isCloseTo(0.10, within(TOLERANCE));
        assertThat(returns[1]).isCloseTo(-0.10, within(TOLERANCE));
    }

    @Test
    void dailyReturns_tooShortSeries_returnsEmpty() {
        assertThat(calculator.dailyReturns(new double[]{100})).isEmpty();
        assertThat(calculator.dailyReturns(null)).isEmpty();
    }

    @Test
    void mean_and_variance_useSampleDenominator() {
        double[] values = {1, 2, 3, 4, 5};

        assertThat(calculator.mean(values)).isCloseTo(3, within(TOLERANCE));
        // deviations 4+1+0+1+4 = 10, divided by n-1 = 4
        assertThat(calculator.variance(values)).isCloseTo(2.5, within(TOLERANCE));
        assertThat(calculator.standardDeviation(values)).isCloseTo(Math.sqrt(2.5), within(TOLERANCE));
    }

    @Test
    void annualisedVolatility_flatReturns_isZero() {
        double[] returns = new double[40];
        java.util.Arrays.fill(returns, 0.01);

        assertThat(calculator.annualisedVolatilityPercent(returns)).isCloseTo(0, within(TOLERANCE));
    }

    @Test
    void annualisedVolatility_scalesBySqrtOfTradingDays() {
        // Alternating +-2% gives a sample stddev of very nearly 0.02.
        double[] returns = alternating(0.02, 100);

        double expected = calculator.standardDeviation(returns) * Math.sqrt(MetricsCalculator.TRADING_DAYS) * 100;
        assertThat(calculator.annualisedVolatilityPercent(returns)).isCloseTo(expected, within(TOLERANCE));
        assertThat(calculator.annualisedVolatilityPercent(returns)).isGreaterThan(25);
    }

    @Test
    void beta_identicalSeries_isOne() {
        double[] benchmark = varyingReturns(60);

        assertThat(calculator.beta(benchmark.clone(), benchmark)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void beta_doubleAmplitudeSeries_isTwo() {
        double[] benchmark = varyingReturns(60);
        double[] asset = new double[benchmark.length];
        for (int i = 0; i < benchmark.length; i++) asset[i] = benchmark[i] * 2;

        assertThat(calculator.beta(asset, benchmark)).isCloseTo(2.0, within(1e-9));
    }

    @Test
    void beta_inverseSeries_isNegativeOne() {
        double[] benchmark = varyingReturns(60);
        double[] asset = new double[benchmark.length];
        for (int i = 0; i < benchmark.length; i++) asset[i] = -benchmark[i];

        assertThat(calculator.beta(asset, benchmark)).isCloseTo(-1.0, within(1e-9));
    }

    @Test
    void beta_alignsSeriesOfDifferentLengthsOnTheirCommonTail() {
        double[] benchmark = varyingReturns(80);
        double[] asset = java.util.Arrays.copyOfRange(benchmark, 20, 80);

        assertThat(calculator.beta(asset, benchmark)).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void beta_tooFewObservations_isNaN() {
        assertThat(calculator.beta(varyingReturns(10), varyingReturns(10))).isNaN();
    }

    @Test
    void alignSeriesOnCommonDays_keepsOnlyDaysPresentInEverySeries() {
        long[] tsA = {day(1), day(2), day(3), day(4)};
        double[] closesA = {10, 20, 30, 40};
        long[] tsB = {day(2), day(4), day(5)};
        double[] closesB = {200, 400, 500};

        double[][] aligned = calculator.alignSeriesOnCommonDays(
                new long[][]{tsA, tsB}, new double[][]{closesA, closesB});

        assertThat(aligned).hasDimensions(2, 2);
        assertThat(aligned[0]).containsExactly(20, 40);
        assertThat(aligned[1]).containsExactly(200, 400);
    }

    @Test
    void alignSeriesOnCommonDays_noOverlap_returnsEmpty() {
        double[][] aligned = calculator.alignSeriesOnCommonDays(
                new long[][]{{day(1)}, {day(9)}}, new double[][]{{10}, {90}});

        assertThat(aligned).isEmpty();
    }

    /**
     * Bars for the same session can differ by a few minutes between two Yahoo responses. Anything
     * inside the same UTC day still matches - see the note on alignSeriesOnCommonDays about why
     * that bound is acceptable for the exchanges this app covers.
     */
    @Test
    void alignSeriesOnCommonDays_toleratesSmallTimeOfDayDifferencesWithinAUtcDay() {
        long[] tsA = {utcMidnight(1) + 13_500, utcMidnight(2) + 13_500};
        long[] tsB = {utcMidnight(1) + 13_800, utcMidnight(2) + 14_100};

        double[][] aligned = calculator.alignSeriesOnCommonDays(
                new long[][]{tsA, tsB}, new double[][]{{10, 20}, {100, 200}});

        assertThat(aligned).hasDimensions(2, 2);
        assertThat(aligned[0]).containsExactly(10, 20);
    }

    /**
     * The regression this alignment exists for: Yahoo returns 250 daily bars for an NSE stock but
     * only 246 for the NIFTY index over the same year. Matching by array position instead of by
     * date offsets the two series for the whole window and collapses beta toward zero, which is
     * exactly what the live endpoint reported before {@code betaFromSeries} was introduced.
     */
    @Test
    void betaFromSeries_survivesABenchmarkMissingAFewTradingDays() {
        int length = 250;
        long[] assetTimestamps = new long[length];
        double[] assetCloses = new double[length];
        for (int i = 0; i < length; i++) {
            assetTimestamps[i] = day(i);
            assetCloses[i] = 100 * (1 + (((i % 5) - 2) / 100.0 * i * 0.01));
        }

        // The benchmark tracks the asset exactly, but is missing four scattered days.
        List<Integer> missing = List.of(20, 77, 140, 201);
        List<Long> benchmarkTimestamps = new ArrayList<>();
        List<Double> benchmarkCloses = new ArrayList<>();
        for (int i = 0; i < length; i++) {
            if (missing.contains(i)) continue;
            benchmarkTimestamps.add(assetTimestamps[i]);
            benchmarkCloses.add(assetCloses[i]);
        }

        double beta = calculator.betaFromSeries(assetTimestamps, assetCloses,
                toLongArray(benchmarkTimestamps), toDoubleArray(benchmarkCloses));

        assertThat(beta).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void betaFromSeries_noOverlappingHistory_isNaN() {
        assertThat(calculator.betaFromSeries(
                new long[]{day(1)}, new double[]{10},
                new long[]{day(500)}, new double[]{20})).isNaN();
    }

    private static long day(int index) {
        return 1_700_000_000L + (index * 86_400L);
    }

    /** Exact UTC midnight, so a test can control where a timestamp sits inside its day. */
    private static long utcMidnight(int index) {
        return ((1_700_000_000L / 86_400L) + index) * 86_400L;
    }

    private static long[] toLongArray(List<Long> values) {
        long[] result = new long[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
        return result;
    }

    private static double[] toDoubleArray(List<Double> values) {
        double[] result = new double[values.size()];
        for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
        return result;
    }

    @Test
    void maxDrawdown_measuresWorstPeakToTrough() {
        // Peak 120 down to 60 is a 50% fall, deeper than the later 80 -> 72 dip.
        assertThat(calculator.maxDrawdownPercent(new double[]{100, 120, 60, 80, 72}))
                .isCloseTo(50, within(TOLERANCE));
    }

    @Test
    void maxDrawdown_monotonicRise_isZero() {
        assertThat(calculator.maxDrawdownPercent(new double[]{100, 110, 120, 130}))
                .isCloseTo(0, within(TOLERANCE));
    }

    @Test
    void sma_averagesTheLastNCloses() {
        double[] closes = {1, 2, 3, 4, 5};

        assertThat(calculator.sma(closes, 5)).isCloseTo(3, within(TOLERANCE));
        assertThat(calculator.sma(closes, 2)).isCloseTo(4.5, within(TOLERANCE));
    }

    @Test
    void sma_periodLongerThanSeries_isNaN() {
        assertThat(calculator.sma(new double[]{1, 2, 3}, 50)).isNaN();
    }

    @Test
    void rsi_uninterruptedGains_pegsAt100() {
        double[] closes = new double[30];
        for (int i = 0; i < closes.length; i++) closes[i] = 100 + i;

        assertThat(calculator.rsi14(closes)).isCloseTo(100, within(TOLERANCE));
    }

    @Test
    void rsi_uninterruptedLosses_pegsAtZero() {
        double[] closes = new double[30];
        for (int i = 0; i < closes.length; i++) closes[i] = 200 - i;

        assertThat(calculator.rsi14(closes)).isCloseTo(0, within(TOLERANCE));
    }

    @Test
    void rsi_balancedMoves_sitsNearFifty() {
        assertThat(calculator.rsi14(alternatingCloses(100, 0.01, 60))).isBetween(40.0, 60.0);
    }

    @Test
    void momentum_measuresChangeOverTheWindow() {
        assertThat(calculator.momentumPercent(new double[]{100, 105, 110}, 2))
                .isCloseTo(10, within(TOLERANCE));
    }

    @Test
    void momentum_windowLongerThanSeries_isNaN() {
        assertThat(calculator.momentumPercent(new double[]{100, 105}, 90)).isNaN();
    }

    @Test
    void pricePosition_mapsIntoThe52WeekRange() {
        assertThat(calculator.pricePositionPercent(50, 0, 100)).isCloseTo(50, within(TOLERANCE));
        assertThat(calculator.pricePositionPercent(100, 0, 100)).isCloseTo(100, within(TOLERANCE));
        assertThat(calculator.pricePositionPercent(0, 0, 100)).isCloseTo(0, within(TOLERANCE));
    }

    @Test
    void pricePosition_flatRange_reportsMidpoint() {
        assertThat(calculator.pricePositionPercent(50, 50, 50)).isCloseTo(50, within(TOLERANCE));
    }

    @Test
    void var95_reportsTheFifthPercentileLossAsAPositiveNumber() {
        // 100 observations: the 5th percentile is index 5 of the sorted array.
        double[] returns = new double[100];
        for (int i = 0; i < 100; i++) returns[i] = (i - 50) / 1000.0;

        assertThat(calculator.historicalVar95Percent(returns)).isCloseTo(4.5, within(1e-9));
    }

    @Test
    void var95_allPositiveReturns_isZeroNotNegative() {
        double[] returns = new double[40];
        java.util.Arrays.fill(returns, 0.01);

        assertThat(calculator.historicalVar95Percent(returns)).isCloseTo(0, within(TOLERANCE));
    }

    @Test
    void sharpeRatio_tooFewObservations_isNaN() {
        assertThat(calculator.sharpeRatio(new double[]{0.01, 0.02}, 6.5)).isNaN();
    }

    @Test
    void weightedPortfolioReturns_perfectlyOffsettingHoldings_cancelToZero() {
        double[] up = alternating(0.02, 50);
        double[] down = new double[up.length];
        for (int i = 0; i < up.length; i++) down[i] = -up[i];

        double[] portfolio = calculator.weightedPortfolioReturns(
                new double[][]{up, down}, new double[]{0.5, 0.5});

        assertThat(portfolio).hasSize(50);
        for (double value : portfolio) {
            assertThat(value).isCloseTo(0, within(TOLERANCE));
        }
    }

    @Test
    void weightedPortfolioReturns_trimsToTheShortestCommonTail() {
        double[] longSeries = varyingReturns(80);
        double[] shortSeries = varyingReturns(30);

        double[] portfolio = calculator.weightedPortfolioReturns(
                new double[][]{longSeries, shortSeries}, new double[]{0.5, 0.5});

        assertThat(portfolio).hasSize(30);
    }

    @Test
    void herfindahlIndex_evenSplit_equalsOneOverN() {
        assertThat(calculator.herfindahlIndex(new double[]{0.25, 0.25, 0.25, 0.25}))
                .isCloseTo(0.25, within(TOLERANCE));
    }

    @Test
    void diversificationScore_evenSplit_isHundred() {
        assertThat(calculator.diversificationScore(new double[]{0.5, 0.5}))
                .isCloseTo(100, within(TOLERANCE));
    }

    @Test
    void diversificationScore_singleHolding_isZero() {
        assertThat(calculator.diversificationScore(new double[]{1.0})).isCloseTo(0, within(TOLERANCE));
    }

    @Test
    void diversificationScore_lopsidedSplit_fallsBetween() {
        // hhi = 0.82, floor = 0.5 -> (1 - 0.64) * 100
        assertThat(calculator.diversificationScore(new double[]{0.9, 0.1}))
                .isCloseTo(36, within(1e-9));
    }

    @Test
    void scaleToScore_mapsBetweenFloorAndCeilingAndClamps() {
        assertThat(calculator.scaleToScore(35, 10, 60)).isCloseTo(50, within(TOLERANCE));
        assertThat(calculator.scaleToScore(5, 10, 60)).isCloseTo(0, within(TOLERANCE));
        assertThat(calculator.scaleToScore(200, 10, 60)).isCloseTo(100, within(TOLERANCE));
    }

    @Test
    void isUsable_rejectsNaNAndInfinity() {
        assertThat(calculator.isUsable(1.5)).isTrue();
        assertThat(calculator.isUsable(Double.NaN)).isFalse();
        assertThat(calculator.isUsable(Double.POSITIVE_INFINITY)).isFalse();
    }

    private static double[] alternating(double magnitude, int length) {
        double[] values = new double[length];
        for (int i = 0; i < length; i++) values[i] = i % 2 == 0 ? magnitude : -magnitude;
        return values;
    }

    private static double[] alternatingCloses(double start, double magnitude, int length) {
        double[] closes = new double[length];
        closes[0] = start;
        for (int i = 1; i < length; i++) {
            closes[i] = closes[i - 1] * (1 + (i % 2 == 0 ? -magnitude : magnitude));
        }
        return closes;
    }

    /** A repeating but non-constant return series, so variance is never zero. */
    private static double[] varyingReturns(int length) {
        double[] values = new double[length];
        for (int i = 0; i < length; i++) values[i] = ((i % 5) - 2) / 100.0;
        return values;
    }
}

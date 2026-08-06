package com.portiq.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Pure statistics over price series - no Spring dependencies, no I/O, no BigDecimal.
 *
 * <p>Everything works in {@code double} and converts to BigDecimal only at the DTO boundary, the
 * same way HoldingService rounds to scale 2 on the way out. Series shorter than the minimum a
 * metric needs return {@link Double#NaN} rather than throwing, because a recently listed ticker
 * with 12 trading days is a normal input, not an error - callers check with {@link #isUsable}.
 */
@Component
public class MetricsCalculator {

    /** Trading days in a year, the standard annualisation factor. */
    public static final int TRADING_DAYS = 252;

    /** Below this many closes a series cannot support volatility or beta with a straight face. */
    public static final int MIN_OBSERVATIONS = 30;

    private static final int RSI_PERIOD = 14;

    private static final long SECONDS_PER_DAY = 86_400L;

    public boolean isUsable(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    /** Simple day-over-day returns. A series of n closes yields n-1 returns. */
    public double[] dailyReturns(double[] closes) {
        if (closes == null || closes.length < 2) return new double[0];

        double[] returns = new double[closes.length - 1];
        for (int i = 1; i < closes.length; i++) {
            double previous = closes[i - 1];
            returns[i - 1] = previous == 0 ? 0 : (closes[i] / previous) - 1;
        }
        return returns;
    }

    /**
     * Sample standard deviation of daily returns, annualised and expressed as a percentage.
     *
     * <p>Requires the same minimum sample as beta, Sharpe and VaR. Scaling a fortnight of noise up
     * by sqrt(252) produces a confident-looking number with nothing behind it, and this metric
     * carries the most weight in the risk score - so it holds to the same bar as the others.
     */
    public double annualisedVolatilityPercent(double[] returns) {
        if (returns == null || returns.length < MIN_OBSERVATIONS) return Double.NaN;

        double stdDev = standardDeviation(returns);
        if (Double.isNaN(stdDev)) return Double.NaN;
        return stdDev * Math.sqrt(TRADING_DAYS) * 100;
    }

    /**
     * Beta against a benchmark: cov(asset, benchmark) / var(benchmark).
     *
     * <p>Both series must already be day-aligned - see {@link #alignSeriesOnCommonDays}. Trimming
     * two unaligned series to a common length is not enough: a single missing holiday shifts one
     * series against the other for the whole window and drives the covariance toward zero.
     */
    public double beta(double[] assetReturns, double[] benchmarkReturns) {
        if (assetReturns == null || benchmarkReturns == null) return Double.NaN;

        int n = Math.min(assetReturns.length, benchmarkReturns.length);
        if (n < MIN_OBSERVATIONS) return Double.NaN;

        double[] asset = tail(assetReturns, n);
        double[] benchmark = tail(benchmarkReturns, n);

        double benchmarkVariance = variance(benchmark);
        if (benchmarkVariance == 0 || Double.isNaN(benchmarkVariance)) return Double.NaN;

        return covariance(asset, benchmark) / benchmarkVariance;
    }

    /** Worst peak-to-trough decline over the window, as a positive percentage. */
    public double maxDrawdownPercent(double[] closes) {
        if (closes == null || closes.length < 2) return Double.NaN;

        double peak = closes[0];
        double worst = 0;
        for (double close : closes) {
            if (close > peak) peak = close;
            if (peak > 0) {
                double drawdown = (peak - close) / peak;
                if (drawdown > worst) worst = drawdown;
            }
        }
        return worst * 100;
    }

    /** (annualised return - risk free rate) / annualised volatility, both in percent. */
    public double sharpeRatio(double[] returns, double riskFreeRatePercent) {
        if (returns == null || returns.length < MIN_OBSERVATIONS) return Double.NaN;

        double volatility = annualisedVolatilityPercent(returns);
        if (Double.isNaN(volatility) || volatility == 0) return Double.NaN;

        double annualisedReturn = mean(returns) * TRADING_DAYS * 100;
        return (annualisedReturn - riskFreeRatePercent) / volatility;
    }

    /**
     * Historical 1-day Value at Risk at 95% confidence, as a positive percentage.
     *
     * <p>The 5th percentile of the observed return distribution - "on the worst 1 day in 20, this
     * is roughly how much it fell". Historical rather than parametric because equity returns have
     * fatter tails than a normal distribution admits.
     */
    public double historicalVar95Percent(double[] returns) {
        if (returns == null || returns.length < MIN_OBSERVATIONS) return Double.NaN;

        double[] sorted = returns.clone();
        Arrays.sort(sorted);

        int index = (int) Math.floor(0.05 * sorted.length);
        double percentile = sorted[Math.min(index, sorted.length - 1)];

        // Losses are negative returns; report VaR as a positive magnitude.
        return Math.max(0, -percentile) * 100;
    }

    /** Simple moving average of the last {@code period} closes. */
    public double sma(double[] closes, int period) {
        if (closes == null || period <= 0 || closes.length < period) return Double.NaN;

        double sum = 0;
        for (int i = closes.length - period; i < closes.length; i++) {
            sum += closes[i];
        }
        return sum / period;
    }

    /**
     * Wilder's 14-period RSI. Above 70 is conventionally read as overbought, below 30 as oversold.
     */
    public double rsi14(double[] closes) {
        if (closes == null || closes.length < RSI_PERIOD + 1) return Double.NaN;

        // Seed with a simple average of the first 14 changes, then smooth Wilder-style.
        double avgGain = 0;
        double avgLoss = 0;
        for (int i = 1; i <= RSI_PERIOD; i++) {
            double change = closes[i] - closes[i - 1];
            if (change > 0) avgGain += change;
            else avgLoss -= change;
        }
        avgGain /= RSI_PERIOD;
        avgLoss /= RSI_PERIOD;

        for (int i = RSI_PERIOD + 1; i < closes.length; i++) {
            double change = closes[i] - closes[i - 1];
            double gain = change > 0 ? change : 0;
            double loss = change < 0 ? -change : 0;
            avgGain = ((avgGain * (RSI_PERIOD - 1)) + gain) / RSI_PERIOD;
            avgLoss = ((avgLoss * (RSI_PERIOD - 1)) + loss) / RSI_PERIOD;
        }

        if (avgLoss == 0) return avgGain == 0 ? 50 : 100;

        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    /** Percentage price change over the last {@code days} trading days. */
    public double momentumPercent(double[] closes, int days) {
        if (closes == null || days <= 0 || closes.length < days + 1) return Double.NaN;

        double past = closes[closes.length - 1 - days];
        double latest = closes[closes.length - 1];
        if (past == 0) return Double.NaN;

        return ((latest / past) - 1) * 100;
    }

    /**
     * Where the current price sits inside the 52-week range: 0 means at the low, 100 at the high.
     * A flat range (high == low) has no meaningful position, so it reports the midpoint.
     */
    public double pricePositionPercent(double current, double low, double high) {
        if (Double.isNaN(current) || Double.isNaN(low) || Double.isNaN(high)) return Double.NaN;
        if (high <= low) return 50;
        return clamp(((current - low) / (high - low)) * 100, 0, 100);
    }

    /**
     * Restricts every input series to the trading days present in all of them, preserving date
     * order, so the returns derived from them line up day for day.
     *
     * <p>This matters more than it looks. Yahoo returns 250 days for RELIANCE.NS but 246 for
     * ^NSEI over the same year - four days where one has a bar and the other does not. Aligning by
     * array position instead of by date offsets the two series for the entire window, which
     * collapses their covariance and reports a beta near zero for stocks that plainly track the
     * index.
     *
     * <p>Days are bucketed by UTC date. Yahoo stamps each bar at the session open - 03:45 UTC for
     * NSE - so bars for the same session land in the same bucket with hours to spare. An exchange
     * whose open straddles UTC midnight would need its bars shifted into exchange-local time first.
     *
     * @return one aligned close series per input, or an empty array if there is no common history
     */
    public double[][] alignSeriesOnCommonDays(long[][] timestamps, double[][] closes) {
        if (timestamps == null || closes == null
                || timestamps.length == 0 || timestamps.length != closes.length) {
            return new double[0][];
        }

        List<Map<Long, Double>> byDay = new ArrayList<>();
        for (int s = 0; s < timestamps.length; s++) {
            long[] seriesTimestamps = timestamps[s];
            double[] seriesCloses = closes[s];
            if (seriesTimestamps == null || seriesCloses == null) return new double[0][];

            Map<Long, Double> dayToClose = new LinkedHashMap<>();
            int length = Math.min(seriesTimestamps.length, seriesCloses.length);
            for (int i = 0; i < length; i++) {
                dayToClose.put(seriesTimestamps[i] / SECONDS_PER_DAY, seriesCloses[i]);
            }
            if (dayToClose.isEmpty()) return new double[0][];
            byDay.add(dayToClose);
        }

        TreeSet<Long> commonDays = new TreeSet<>(byDay.get(0).keySet());
        for (int s = 1; s < byDay.size(); s++) {
            commonDays.retainAll(byDay.get(s).keySet());
        }
        if (commonDays.isEmpty()) return new double[0][];

        double[][] aligned = new double[byDay.size()][commonDays.size()];
        for (int s = 0; s < byDay.size(); s++) {
            int i = 0;
            for (Long day : commonDays) {
                aligned[s][i++] = byDay.get(s).get(day);
            }
        }
        return aligned;
    }

    /**
     * Beta computed from raw dated series, aligning them on their common trading days first.
     * This is the entry point callers should use; the array overload assumes alignment is done.
     */
    public double betaFromSeries(long[] assetTimestamps, double[] assetCloses,
                                 long[] benchmarkTimestamps, double[] benchmarkCloses) {

        double[][] aligned = alignSeriesOnCommonDays(
                new long[][]{assetTimestamps, benchmarkTimestamps},
                new double[][]{assetCloses, benchmarkCloses});

        if (aligned.length != 2) return Double.NaN;
        return beta(dailyReturns(aligned[0]), dailyReturns(aligned[1]));
    }

    /**
     * Builds a synthetic portfolio return series: the weighted sum of each holding's daily returns.
     *
     * <p>This is what makes the portfolio volatility figure honest. A weighted average of the
     * individual volatilities would ignore correlation entirely and always overstate risk; summing
     * the return streams first lets offsetting moves cancel, which is exactly the diversification
     * benefit the report is meant to show.
     *
     * <p>Series are trimmed to their shortest common tail so every day lines up.
     */
    public double[] weightedPortfolioReturns(double[][] returnsPerHolding, double[] weights) {
        if (returnsPerHolding == null || weights == null || returnsPerHolding.length == 0) return new double[0];
        if (returnsPerHolding.length != weights.length) return new double[0];

        int common = Integer.MAX_VALUE;
        for (double[] returns : returnsPerHolding) {
            if (returns == null || returns.length == 0) return new double[0];
            common = Math.min(common, returns.length);
        }
        if (common == Integer.MAX_VALUE || common == 0) return new double[0];

        double[] portfolio = new double[common];
        for (int h = 0; h < returnsPerHolding.length; h++) {
            double[] aligned = tail(returnsPerHolding[h], common);
            for (int i = 0; i < common; i++) {
                portfolio[i] += aligned[i] * weights[h];
            }
        }
        return portfolio;
    }

    /**
     * Herfindahl-Hirschman index of position weights, where weights are fractions summing to 1.
     * Ranges from 1/n (perfectly even) to 1 (a single position).
     */
    public double herfindahlIndex(double[] weights) {
        if (weights == null || weights.length == 0) return Double.NaN;

        double hhi = 0;
        for (double weight : weights) {
            hhi += weight * weight;
        }
        return hhi;
    }

    /**
     * Diversification on a 0-100 scale where 100 is perfectly even. Normalises HHI against its
     * 1/n floor so the score measures evenness rather than simply rewarding more holdings.
     */
    public double diversificationScore(double[] weights) {
        if (weights == null || weights.length == 0) return Double.NaN;
        if (weights.length == 1) return 0;

        double hhi = herfindahlIndex(weights);
        double floor = 1.0 / weights.length;
        return clamp((1 - ((hhi - floor) / (1 - floor))) * 100, 0, 100);
    }

    /**
     * Maps a raw metric onto a 0-100 scale between a floor and a ceiling, clamped at both ends.
     * The risk score is built entirely from these, which keeps each component comparable.
     */
    public double scaleToScore(double value, double floor, double ceiling) {
        if (Double.isNaN(value) || ceiling == floor) return Double.NaN;
        return clamp(((value - floor) / (ceiling - floor)) * 100, 0, 100);
    }

    public double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public double mean(double[] values) {
        if (values == null || values.length == 0) return Double.NaN;

        double sum = 0;
        for (double value : values) sum += value;
        return sum / values.length;
    }

    public double standardDeviation(double[] values) {
        double variance = variance(values);
        return Double.isNaN(variance) ? Double.NaN : Math.sqrt(variance);
    }

    /** Sample variance (n-1 denominator), the right choice for an observed return sample. */
    public double variance(double[] values) {
        if (values == null || values.length < 2) return Double.NaN;

        double mean = mean(values);
        double sumSquares = 0;
        for (double value : values) {
            double delta = value - mean;
            sumSquares += delta * delta;
        }
        return sumSquares / (values.length - 1);
    }

    public double covariance(double[] a, double[] b) {
        if (a == null || b == null || a.length != b.length || a.length < 2) return Double.NaN;

        double meanA = mean(a);
        double meanB = mean(b);
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += (a[i] - meanA) * (b[i] - meanB);
        }
        return sum / (a.length - 1);
    }

    /** The most recent {@code n} elements, so two series of different lengths end on the same day. */
    private double[] tail(double[] values, int n) {
        if (values.length == n) return values;
        return Arrays.copyOfRange(values, values.length - n, values.length);
    }
}

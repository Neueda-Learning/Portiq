package com.portiq.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A year of daily closing prices for one ticker, as returned by the Yahoo chart API.
 *
 * <p>Kept separate from {@link PortfolioHistoryPoint} because the analytics code needs the raw
 * per-ticker series (to derive returns, volatility and beta) rather than the portfolio-level
 * totals the dashboard chart consumes.
 */
public record DailySeries(
        String ticker,
        List<Long> timestamps,
        List<BigDecimal> closes,
        List<Long> volumes,
        BigDecimal currentPrice) {

    public static DailySeries empty(String ticker) {
        return new DailySeries(ticker, List.of(), List.of(), List.of(), null);
    }

    public boolean isEmpty() {
        return closes.isEmpty();
    }

    public int size() {
        return closes.size();
    }

    /** The latest close, used as a price fallback when the quote endpoint gives us nothing. */
    public BigDecimal latestClose() {
        return closes.isEmpty() ? null : closes.get(closes.size() - 1);
    }

    public BigDecimal fiftyTwoWeekHigh() {
        return closes.stream().max(BigDecimal::compareTo).orElse(null);
    }

    public BigDecimal fiftyTwoWeekLow() {
        return closes.stream().min(BigDecimal::compareTo).orElse(null);
    }

    /** Closes as primitive doubles, the form every {@link com.portiq.service.MetricsCalculator} method takes. */
    public double[] closesAsDoubles() {
        double[] values = new double[closes.size()];
        for (int i = 0; i < closes.size(); i++) {
            values[i] = closes.get(i).doubleValue();
        }
        return values;
    }

    /** Timestamps as primitive longs, needed to align two series on their common trading days. */
    public long[] timestampsAsLongs() {
        long[] values = new long[timestamps.size()];
        for (int i = 0; i < timestamps.size(); i++) {
            values[i] = timestamps.get(i);
        }
        return values;
    }
}

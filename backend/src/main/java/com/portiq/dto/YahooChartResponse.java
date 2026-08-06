package com.portiq.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * The slice of Yahoo's {@code v8/finance/chart} response this application actually reads.
 *
 * <p>Replaces the chain of unchecked {@code Map}/{@code List} casts the three price fetchers used
 * to do by hand. Those casts were unchecked in the literal compiler sense - nothing verified that
 * {@code body.get("chart")} really was a {@code Map} until a {@code ClassCastException} said
 * otherwise at runtime, and each fetcher re-implemented the same six-step descent with its own
 * null checks. Binding to records instead means Jackson does the shape checking once, in one
 * place, and a change in the upstream payload surfaces as a null field rather than a cast failure
 * somewhere in the middle of a loop.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} throughout: Yahoo sends a great deal more
 * than this, and new fields appearing upstream must never break a running deployment.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record YahooChartResponse(Chart chart) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chart(List<Result> result) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(List<Long> timestamp, Indicators indicators, Meta meta) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Indicators(List<Quote> quote) {}

    /**
     * Closes and volumes are boxed rather than primitive because Yahoo emits explicit nulls for
     * market holidays. Unboxing those to 0.0 would silently invent a day on which the price
     * crashed to zero, which the return series would then read as a -100% move.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Quote(List<BigDecimal> close, List<Long> volume) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Meta(@JsonProperty("regularMarketPrice") BigDecimal regularMarketPrice) {}

    /** The first result, or null when the payload carried none. */
    public Result firstResult() {
        if (chart == null || chart.result() == null || chart.result().isEmpty()) {
            return null;
        }
        return chart.result().get(0);
    }

    /** The live price from the result metadata, or null when it is absent. */
    public static BigDecimal livePriceOf(Result result) {
        return result == null || result.meta() == null ? null : result.meta().regularMarketPrice();
    }

    /** The first quote block of a result, or null when the payload carried none. */
    public static Quote firstQuoteOf(Result result) {
        if (result == null || result.indicators() == null
                || result.indicators().quote() == null || result.indicators().quote().isEmpty()) {
            return null;
        }
        return result.indicators().quote().get(0);
    }
}

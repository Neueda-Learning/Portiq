package com.portiq.model;

/**
 * How much price history backed a given analysis.
 *
 * <p>Exposed on every result so the UI can say "not enough history to judge this" instead of
 * rendering a confident-looking score that was computed from twelve days of data.
 */
public enum DataQuality {
    /** Enough observations for every metric. */
    SUFFICIENT,
    /** Some history, but too short for volatility/beta - the score uses what could be computed. */
    LIMITED,
    /** The price feed returned nothing for this ticker. */
    UNAVAILABLE
}

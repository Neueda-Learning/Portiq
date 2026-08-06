package com.portiq.service;

import com.portiq.exception.InvalidRequestException;

import java.util.regex.Pattern;

/**
 * Guards the analytics endpoints against tickers that could never resolve.
 *
 * <p>Without this a blank or malformed symbol reaches the price feed, comes back empty, and is
 * reported as "no price history available" - which reads as "this stock has no data" rather than
 * "you asked for something that is not a ticker". Rejecting it up front with a specific message
 * saves the caller chasing a data problem that is really a typo.
 */
final class TickerValidator {

    /** Letters, digits, and the separators real symbols use: RELIANCE.NS, BRK-B, ^NSEI. */
    private static final Pattern VALID = Pattern.compile("^[A-Z0-9^][A-Z0-9.\\-^]{0,19}$");

    private TickerValidator() {}

    static String normalise(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            throw new InvalidRequestException("A ticker symbol is required, for example RELIANCE.NS.");
        }

        String normalised = ticker.trim().toUpperCase();
        if (!VALID.matcher(normalised).matches()) {
            throw new InvalidRequestException(
                    "'" + ticker.trim() + "' is not a valid ticker symbol. Use letters, digits, dots or hyphens, "
                            + "for example RELIANCE.NS.");
        }
        return normalised;
    }
}

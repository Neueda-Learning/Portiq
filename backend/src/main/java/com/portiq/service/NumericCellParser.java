package com.portiq.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Reads a number out of a cell written for a human to look at.
 *
 * <p>Shared by the CSV importer and the model-output parser so both accept exactly the same
 * formats. They had near-identical copies of this logic, which is the arrangement where one of
 * them quietly gains a fix the other does not - and "the CSV import accepts this file but the
 * screenshot import rejects it" is a genuinely baffling bug to be handed.
 *
 * <p>What is tolerated is drawn from what real broker exports and Indian statements contain:
 * thousands separators, a currency symbol or code, and accountancy-style parenthesised negatives.
 * Stripping them here is not laxness - the alternative is a user hand-editing a 400-row export.
 *
 * <p>What is deliberately <em>not</em> tolerated is a leading run of arbitrary letters. Accepting
 * that would turn {@code "abc123"} into 123, so currency prefixes are matched against a fixed list
 * rather than a general "strip any letters" rule.
 */
final class NumericCellParser {

    /**
     * Currency prefixes seen in real statements. Symbols are handled separately by Unicode
     * category; these are the alphabetic codes, which no character class covers.
     */
    private static final List<String> CURRENCY_CODES =
            List.of("RS.", "RS", "INR", "USD", "EUR", "GBP", "AUD", "CAD", "SGD", "JPY");

    private NumericCellParser() {}

    /**
     * Parses {@code cell}, or returns empty when it is blank or not a number.
     *
     * <p>Returning an {@link Optional} rather than throwing lets each caller phrase its own error:
     * the CSV importer names a row number, the model parser names a field and position, and both
     * read better than a shared message trying to serve both.
     */
    static Optional<BigDecimal> parse(String cell) {
        String raw = cell == null ? "" : cell.trim();
        if (raw.isEmpty()) {
            return Optional.empty();
        }

        // Accountancy notation: (1,250) means -1250.
        boolean parenthesisedNegative = raw.startsWith("(") && raw.endsWith(")");

        String value = raw;
        if (parenthesisedNegative) {
            value = value.substring(1, value.length() - 1).trim();
        }

        // Strip a currency symbol (₹, $, £, €) by Unicode category, then an alphabetic code.
        value = value.replaceFirst("^\\p{Sc}\\s*", "");
        String upper = value.toUpperCase();
        for (String code : CURRENCY_CODES) {
            if (upper.startsWith(code)) {
                value = value.substring(code.length()).trim();
                break;
            }
        }

        // Thousands separators and any internal spacing, including the non-breaking space that
        // spreadsheets emit and that looks identical to a normal one in a diff.
        value = value.replaceAll("[,\\s\\u00A0\\u202F]", "");

        if (value.isEmpty()) {
            return Optional.empty();
        }

        try {
            BigDecimal parsed = new BigDecimal(value);
            return Optional.of(parenthesisedNegative ? parsed.negate() : parsed);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}

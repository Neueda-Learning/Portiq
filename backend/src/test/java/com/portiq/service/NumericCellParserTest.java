package com.portiq.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class NumericCellParserTest {

    @ParameterizedTest
    @CsvSource({
            "'1250',        1250",
            "'1,250',       1250",
            "'1,25,000',    125000",     // Indian grouping
            "'1250.75',     1250.75",
            "'  1250  ',    1250",
            "'-500',        -500",
            "'0',           0",
    })
    void readsPlainAndGroupedNumbers(String cell, String expected) {
        assertThat(NumericCellParser.parse(cell)).hasValueSatisfying(
                value -> assertThat(value).isEqualByComparingTo(expected));
    }

    @ParameterizedTest
    @CsvSource({
            "'Rs 1250',     1250",
            "'Rs. 1,250',   1250",
            "'INR 1250',    1250",
            "'USD 99.99',   99.99",
            "'rs 1250',     1250",       // case-insensitive
    })
    void stripsAlphabeticCurrencyCodes(String cell, String expected) {
        // The app's own formatter emits "Rs 1,234.50", so a user round-tripping an export back
        // through the importer hits this exact format.
        assertThat(NumericCellParser.parse(cell)).hasValueSatisfying(
                value -> assertThat(value).isEqualByComparingTo(expected));
    }

    @ParameterizedTest
    @CsvSource({
            "'₹1250',       1250",
            "'₹ 1,250.50',  1250.50",
            "'$99.99',      99.99",
            "'£10',         10",
            "'€25',         25",
    })
    void stripsCurrencySymbols(String cell, String expected) {
        assertThat(NumericCellParser.parse(cell)).hasValueSatisfying(
                value -> assertThat(value).isEqualByComparingTo(expected));
    }

    @ParameterizedTest
    @CsvSource({
            "'(1250)',      -1250",
            "'(1,250.50)',  -1250.50",
            "'(Rs 500)',    -500",
    })
    void readsAccountancyStyleNegatives(String cell, String expected) {
        assertThat(NumericCellParser.parse(cell)).hasValueSatisfying(
                value -> assertThat(value).isEqualByComparingTo(expected));
    }

    @Test
    void handlesTheNonBreakingSpacesSpreadsheetsEmit() {
        // Visually identical to a normal space, including in a diff - which is what makes it a
        // genuinely maddening import failure to diagnose by eye.
        assertThat(NumericCellParser.parse("1 250")).hasValueSatisfying(
                value -> assertThat(value).isEqualByComparingTo("1250"));
        assertThat(NumericCellParser.parse("1 250.50")).hasValueSatisfying(
                value -> assertThat(value).isEqualByComparingTo("1250.50"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abc123",          // must NOT become 123 - the reason currency codes are an allowlist
            "12abc",
            "not a number",
            "1.2.3",
            "--5",
            "Rs",
            "()",
            "#REF!",           // a broken spreadsheet formula
            "N/A",
    })
    void refusesAnythingThatIsNotActuallyANumber(String cell) {
        assertThat(NumericCellParser.parse(cell))
                .as("'%s' must not parse", cell)
                .isEmpty();
    }

    @Test
    void treatsBlankAndNullAsAbsent() {
        assertThat(NumericCellParser.parse(null)).isEmpty();
        assertThat(NumericCellParser.parse("")).isEmpty();
        assertThat(NumericCellParser.parse("   ")).isEmpty();
    }

    @Test
    void keepsFullPrecision() {
        // Quantities can be fractional to several places for mutual fund units.
        assertThat(NumericCellParser.parse("123.456789")).hasValueSatisfying(
                value -> assertThat(value).isEqualByComparingTo("123.456789"));
    }
}

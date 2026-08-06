package com.portiq.service;

import com.portiq.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TickerValidatorTest {

    @Test
    void normalise_uppercasesAndTrims() {
        assertThat(TickerValidator.normalise("  reliance.ns  ")).isEqualTo("RELIANCE.NS");
    }

    @Test
    void normalise_acceptsTheSymbolShapesTheAppActuallyUses() {
        assertThat(TickerValidator.normalise("^NSEI")).isEqualTo("^NSEI");
        assertThat(TickerValidator.normalise("BRK-B")).isEqualTo("BRK-B");
        assertThat(TickerValidator.normalise("TCS.NS")).isEqualTo("TCS.NS");
    }

    @Test
    void normalise_blank_saysATickerIsRequired() {
        assertThatThrownBy(() -> TickerValidator.normalise("   "))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("A ticker symbol is required");
    }

    @Test
    void normalise_null_saysATickerIsRequired() {
        assertThatThrownBy(() -> TickerValidator.normalise(null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("A ticker symbol is required");
    }

    /** The message has to name the offending input, or the caller cannot see their own typo. */
    @Test
    void normalise_malformed_quotesTheOffendingValue() {
        assertThatThrownBy(() -> TickerValidator.normalise("no spaces allowed"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("'no spaces allowed' is not a valid ticker symbol");
    }

    @Test
    void normalise_rejectsOverlyLongInput() {
        assertThatThrownBy(() -> TickerValidator.normalise("A".repeat(40)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("not a valid ticker symbol");
    }
}

package com.portiq.dto;

import com.portiq.model.HoldingType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ticker constraint is the one that carries security weight: a stored ticker is later
 * interpolated into outbound URLs, and tickers arrive from a CSV and from a model reading an
 * uploaded image as well as from the form.
 */
class HoldingRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void openValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TCS.NS", "RELIANCE.NS", "AAPL", "BRK-B", "^NSEI", "CASH", "BLS.NS"})
    void acceptsRealTickerSymbols(String ticker) {
        assertThat(validator.validate(request(ticker))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../../v7/finance/quote",          // climbs out of the chart endpoint
            "TCS.NS?symbol=EVIL",              // rewrites the query string
            "TCS.NS#fragment",
            "TCS.NS/../../../etc/passwd",
            "TCS NS",
            "TCS.NS\r\nX-Injected: 1",         // header injection into the outbound request
            "<script>alert(1)</script>",
            "{malicious}",                     // RestTemplate URI template placeholder
            "%2e%2e%2f"
    })
    void rejectsTickersThatCouldSteerAnOutboundRequest(String ticker) {
        assertThat(validator.validate(request(ticker)))
                .as("'%s' must not be storable as a ticker", ticker)
                .isNotEmpty();
    }

    @Test
    void rejectsAnOverlongTicker() {
        assertThat(validator.validate(request("A".repeat(21)))).isNotEmpty();
    }

    @Test
    void rejectsAnOverlongName() {
        HoldingRequest request = request("TCS.NS");
        request.setName("N".repeat(121));

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void rejectsAnAbsurdQuantity() {
        HoldingRequest request = request("TCS.NS");
        request.setQuantity(new BigDecimal("999999999999999"));

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void rejectsAFuturePurchaseDate() {
        HoldingRequest request = request("TCS.NS");
        request.setPurchaseDate(LocalDate.now().plusDays(1));

        assertThat(validator.validate(request)).isNotEmpty();
    }

    private HoldingRequest request(String ticker) {
        HoldingRequest request = new HoldingRequest();
        request.setTicker(ticker);
        request.setName("Tata Consultancy Services");
        request.setType(HoldingType.STOCK);
        request.setQuantity(new BigDecimal("10"));
        request.setPurchasePrice(new BigDecimal("3500.00"));
        request.setPurchaseDate(LocalDate.of(2024, 1, 15));
        return request;
    }
}

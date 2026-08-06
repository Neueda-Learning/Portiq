package com.portiq.service;

import com.portiq.dto.HoldingRequest;
import com.portiq.exception.InvalidRequestException;
import com.portiq.model.HoldingType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The model's output is untrusted input - it is generated from a file the user uploaded, so its
 * content is attacker-influenced even though the endpoint producing it is one we configured.
 */
class LlmHoldingsParserTest {

    private final LlmHoldingsParser parser = new LlmHoldingsParser();

    @Test
    void parsesAWellFormedRow() {
        List<HoldingRequest> result = parser.parse("""
                [{"ticker":"TCS.NS","name":"Tata Consultancy","type":"STOCK",
                  "quantity":"5","purchasePrice":"3500.50","purchaseDate":"2024-01-15"}]
                """);

        assertThat(result).hasSize(1);
        HoldingRequest holding = result.get(0);
        assertThat(holding.getTicker()).isEqualTo("TCS.NS");
        assertThat(holding.getName()).isEqualTo("Tata Consultancy");
        assertThat(holding.getType()).isEqualTo(HoldingType.STOCK);
        assertThat(holding.getQuantity()).isEqualByComparingTo("5");
        assertThat(holding.getPurchasePrice()).isEqualByComparingTo("3500.50");
        assertThat(holding.getPurchaseDate()).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    void acceptsNumbersSentAsJsonNumbersRatherThanStrings() {
        // A model asked for JSON will send quantity as 5 about as often as "5". Binding the row
        // to typed fields must not make that an all-or-nothing parse failure.
        List<HoldingRequest> result = parser.parse("""
                [{"ticker":"INFY.NS","name":"Infosys","type":"STOCK",
                  "quantity":12,"purchasePrice":1400.25,"purchaseDate":"2023-11-01"}]
                """);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getQuantity()).isEqualByComparingTo("12");
        assertThat(result.get(0).getPurchasePrice()).isEqualByComparingTo("1400.25");
    }

    @ParameterizedTest
    @CsvSource({
            "'1,250',       1250",
            "'Rs 1250',     1250",
            "'₹1,250.50',   1250.50",
            "'  75  ',      75",
    })
    void toleratesFormattingCarriedOverFromTheSourceDocument(String raw, String expected) {
        List<HoldingRequest> result = parser.parse(
                "[{\"ticker\":\"X\",\"name\":\"X\",\"quantity\":\"" + raw + "\",\"purchasePrice\":\"10\"}]");

        assertThat(result.get(0).getQuantity()).isEqualByComparingTo(expected);
    }

    @Test
    void namesTheFieldAndRowWhenANumberIsUnreadable() {
        // The old version produced a NumberFormatException whose message was the offending text
        // with no indication of which field or row it came from.
        assertThatThrownBy(() -> parser.parse("""
                [{"ticker":"A","name":"A","quantity":"5","purchasePrice":"10"},
                 {"ticker":"B","name":"B","quantity":"not a number","purchasePrice":"10"}]
                """))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Row 2")
                .hasMessageContaining("quantity")
                .hasMessageContaining("not a number");
    }

    @Test
    void reportsAMissingNumberAsMissingRatherThanAsTheWordNull() {
        // Previously String.valueOf(null) produced the literal "null", which then failed as a
        // NumberFormatException reading `null` - indistinguishable from a cell containing garbage.
        assertThatThrownBy(() -> parser.parse("[{\"ticker\":\"A\",\"name\":\"A\",\"purchasePrice\":\"10\"}]"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("no quantity could be read");
    }

    @Test
    void survivesUnknownFieldsFromTheModel() {
        // Models add commentary fields unprompted; a new key must not fail the whole array.
        List<HoldingRequest> result = parser.parse("""
                [{"ticker":"TCS.NS","name":"TCS","quantity":"5","purchasePrice":"100",
                  "confidence":0.9,"notes":"read from row 3","currency":"INR"}]
                """);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTicker()).isEqualTo("TCS.NS");
    }

    @Test
    void stripsProseAndMarkdownFencesAroundTheArray() {
        List<HoldingRequest> result = parser.parse("""
                Here are the holdings I found:
                ```json
                [{"ticker":"ITC.NS","name":"ITC","quantity":"50","purchasePrice":"380"}]
                ```
                Let me know if you need anything else.
                """);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTicker()).isEqualTo("ITC.NS");
    }

    @Test
    void fallsBackToStockForAnUnrecognisedType() {
        List<HoldingRequest> result = parser.parse(
                "[{\"ticker\":\"X\",\"name\":\"X\",\"type\":\"CRYPTO\",\"quantity\":\"1\",\"purchasePrice\":\"1\"}]");

        assertThat(result.get(0).getType()).isEqualTo(HoldingType.STOCK);
    }

    @Test
    void fallsBackToTodayForAnUnreadableDate() {
        // Quantities and prices drive every calculation; a date does not. Losing an otherwise good
        // position because the model could not read a date off a blurry screenshot is a bad trade.
        List<HoldingRequest> result = parser.parse(
                "[{\"ticker\":\"X\",\"name\":\"X\",\"quantity\":\"1\",\"purchasePrice\":\"1\",\"purchaseDate\":\"last Tuesday\"}]");

        assertThat(result.get(0).getPurchaseDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void clampsAFutureDateToToday() {
        // A future purchase date fails validation downstream, and a misread year is the usual cause.
        List<HoldingRequest> result = parser.parse(
                "[{\"ticker\":\"X\",\"name\":\"X\",\"quantity\":\"1\",\"purchasePrice\":\"1\",\"purchaseDate\":\"2099-01-01\"}]");

        assertThat(result.get(0).getPurchaseDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void fallsBackToTheTickerWhenNoNameIsGiven() {
        List<HoldingRequest> result = parser.parse(
                "[{\"ticker\":\"wipro.ns\",\"quantity\":\"1\",\"purchasePrice\":\"1\"}]");

        assertThat(result.get(0).getTicker()).isEqualTo("WIPRO.NS");
        assertThat(result.get(0).getName()).isEqualTo("WIPRO.NS");
    }

    @Test
    void rejectsOutputWithNoArrayInIt() {
        assertThatThrownBy(() -> parser.parse("I could not read that image, sorry."))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Could not read a holdings list");

        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMalformedJsonWithoutEchoingTheModelOutput() {
        // The output is derived from an uploaded statement and can contain the holder's positions,
        // so it must not be echoed into an exception message that reaches a response or a log.
        String secret = "SECRET-POSITION-DATA";
        assertThatThrownBy(() -> parser.parse("[{\"ticker\": " + secret + " }]"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining(secret);
    }

    @Test
    void returnsAnEmptyListForAnEmptyArray() {
        assertThat(parser.parse("[]")).isEmpty();
    }
}

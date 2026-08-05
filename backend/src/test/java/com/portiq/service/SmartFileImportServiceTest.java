package com.portiq.service;

import com.portiq.dto.HoldingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The model is only ever asked to normalize rows 1:1 (never sum across rows - see class javadoc
 * on SmartFileImportService for why); these tests verify the actual buy/sell netting arithmetic
 * deterministically, with the model call mocked to return fixed transaction JSON.
 */
@ExtendWith(MockitoExtension.class)
class SmartFileImportServiceTest {

    @Mock
    private ChatCompletionClient chatCompletionClient;

    @Mock
    private SpreadsheetTextExtractor textExtractor;

    private SmartFileImportService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new SmartFileImportService(chatCompletionClient, textExtractor);
        ReflectionTestUtils.setField(service, "model", "test-model");
        when(textExtractor.extractTableText(any())).thenReturn("dummy table text");
    }

    @Test
    void nets_multipleBuyRowsOfSameStock_intoWeightedAveragePosition() throws Exception {
        // Mirrors a real broker order history: six BUY rows for the same stock.
        String modelResponse = "[" +
                "{\"ticker\":\"BLS.NS\",\"name\":\"BLS INTL SERVS LTD\",\"type\":\"STOCK\",\"side\":\"BUY\",\"quantity\":20,\"price\":288.5,\"date\":\"2026-04-10\"}," +
                "{\"ticker\":\"BLS.NS\",\"name\":\"BLS INTL SERVS LTD\",\"type\":\"STOCK\",\"side\":\"BUY\",\"quantity\":10,\"price\":297.6,\"date\":\"2026-04-16\"}," +
                "{\"ticker\":\"BLS.NS\",\"name\":\"BLS INTL SERVS LTD\",\"type\":\"STOCK\",\"side\":\"BUY\",\"quantity\":10,\"price\":282.25,\"date\":\"2026-04-23\"}," +
                "{\"ticker\":\"BLS.NS\",\"name\":\"BLS INTL SERVS LTD\",\"type\":\"STOCK\",\"side\":\"BUY\",\"quantity\":20,\"price\":264.0,\"date\":\"2026-06-04\"}," +
                "{\"ticker\":\"BLS.NS\",\"name\":\"BLS INTL SERVS LTD\",\"type\":\"STOCK\",\"side\":\"BUY\",\"quantity\":25,\"price\":263.0,\"date\":\"2026-06-10\"}," +
                "{\"ticker\":\"BLS.NS\",\"name\":\"BLS INTL SERVS LTD\",\"type\":\"STOCK\",\"side\":\"BUY\",\"quantity\":65,\"price\":251.0,\"date\":\"2026-06-30\"}" +
                "]";
        when(chatCompletionClient.complete(anyString(), any())).thenReturn(modelResponse);

        List<HoldingRequest> result = service.extractHoldings(csvFile());

        assertThat(result).hasSize(1);
        HoldingRequest holding = result.get(0);
        assertThat(holding.getTicker()).isEqualTo("BLS.NS");
        assertThat(holding.getQuantity()).isEqualByComparingTo("150");
        // Total value = 20*288.5 + 10*297.6 + 10*282.25 + 20*264 + 25*263 + 65*251 = 39738.5; /150 = 264.9233
        assertThat(holding.getPurchasePrice()).isEqualByComparingTo("264.9233");
        assertThat(holding.getPurchaseDate().toString()).isEqualTo("2026-04-10");
    }

    @Test
    void sell_reducesQuantityButLeavesAverageCostOfRemainingSharesUnchanged() throws Exception {
        String modelResponse = "[" +
                "{\"ticker\":\"XYZ.NS\",\"name\":\"XYZ Ltd\",\"type\":\"STOCK\",\"side\":\"BUY\",\"quantity\":10,\"price\":100,\"date\":\"2024-01-01\"}," +
                "{\"ticker\":\"XYZ.NS\",\"name\":\"XYZ Ltd\",\"type\":\"STOCK\",\"side\":\"SELL\",\"quantity\":4,\"price\":150,\"date\":\"2024-02-01\"}," +
                "{\"ticker\":\"XYZ.NS\",\"name\":\"XYZ Ltd\",\"type\":\"STOCK\",\"side\":\"BUY\",\"quantity\":4,\"price\":200,\"date\":\"2024-03-01\"}" +
                "]";
        when(chatCompletionClient.complete(anyString(), any())).thenReturn(modelResponse);

        List<HoldingRequest> result = service.extractHoldings(csvFile());

        assertThat(result).hasSize(1);
        HoldingRequest holding = result.get(0);
        // qty: 10 - 4 + 4 = 10. Avg cost: sell doesn't change cost basis, so (6*100 + 4*200)/10 = 140.
        assertThat(holding.getQuantity()).isEqualByComparingTo("10");
        assertThat(holding.getPurchasePrice()).isEqualByComparingTo("140.0000");
    }

    @Test
    void fullySoldPosition_isOmittedFromResults() throws Exception {
        String modelResponse = "[" +
                "{\"ticker\":\"ABC.NS\",\"name\":\"ABC Ltd\",\"type\":\"STOCK\",\"side\":\"BUY\",\"quantity\":5,\"price\":100,\"date\":\"2024-01-01\"}," +
                "{\"ticker\":\"ABC.NS\",\"name\":\"ABC Ltd\",\"type\":\"STOCK\",\"side\":\"SELL\",\"quantity\":5,\"price\":120,\"date\":\"2024-02-01\"}" +
                "]";
        when(chatCompletionClient.complete(anyString(), any())).thenReturn(modelResponse);

        List<HoldingRequest> result = service.extractHoldings(csvFile());

        assertThat(result).isEmpty();
    }

    @Test
    void totalValueDividedByQuantity_computesPerSharePrice() throws Exception {
        // The row already arrives normalized (division is the model's one safe per-row job);
        // this just confirms a single-row import passes the number through untouched.
        String modelResponse = "[{\"ticker\":\"CAPLIPOINT.NS\",\"name\":\"CAPLIN POINT LAB LTD.\",\"type\":\"STOCK\"," +
                "\"side\":\"BUY\",\"quantity\":5,\"price\":1710,\"date\":\"2026-04-10\"}]";
        when(chatCompletionClient.complete(anyString(), any())).thenReturn(modelResponse);

        List<HoldingRequest> result = service.extractHoldings(csvFile());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getQuantity()).isEqualByComparingTo("5");
        assertThat(result.get(0).getPurchasePrice()).isEqualByComparingTo(new BigDecimal("1710"));
    }

    private MockMultipartFile csvFile() {
        return new MockMultipartFile("file", "orders.csv", "text/csv", "irrelevant".getBytes());
    }
}

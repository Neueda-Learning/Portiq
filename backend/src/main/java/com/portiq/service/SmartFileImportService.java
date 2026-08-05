package com.portiq.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portiq.dto.HoldingRequest;
import com.portiq.model.HoldingType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses arbitrary CSV or spreadsheet exports (broker order histories, statements, any column
 * layout) into holdings. Real broker exports vary wildly: different columns, total order value
 * instead of per-share price, ticker symbols without an exchange suffix, and often a full order
 * history with many individual buy/sell rows per stock.
 *
 * <p>The model is only asked to normalize each row 1:1 (map columns, compute a per-row per-share
 * price, add the exchange suffix) - never to sum or average across rows itself. Summing dozens of
 * rows correctly is exactly the kind of multi-step arithmetic a model gets subtly wrong (verified
 * empirically: asking it to net a 21-row order history in one pass produced a wrong total for one
 * stock and silently dropped another entirely). The actual buy/sell netting into a single
 * weighted-average position happens here in deterministic code instead.
 */
@Service
public class SmartFileImportService {

    private final ChatCompletionClient chatCompletionClient;
    private final SpreadsheetTextExtractor textExtractor;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.insights.model:}")
    private String model;

    public SmartFileImportService(ChatCompletionClient chatCompletionClient, SpreadsheetTextExtractor textExtractor) {
        this.chatCompletionClient = chatCompletionClient;
        this.textExtractor = textExtractor;
    }

    public boolean isAvailable() {
        return chatCompletionClient.isConfigured();
    }

    public List<HoldingRequest> extractHoldings(MultipartFile file) throws IOException {
        String tableText = textExtractor.extractTableText(file);
        if (tableText.isBlank()) {
            throw new IllegalStateException("The file appears to be empty");
        }

        String instructions = "The following is the raw text of a spreadsheet or CSV export from a stock broker "
                + "or portfolio statement, in any column layout or order. It may list one row per stock, or a full "
                + "order history with one row per individual buy/sell transaction.\n\n"
                + "Output one JSON object per INPUT row - do not combine, merge, or summarize rows yourself, just "
                + "normalize each row exactly as it stands on its own:\n"
                + "- ticker: for Indian stocks, SYMBOL.NS if the exchange is NSE, SYMBOL.BO if BSE; otherwise the "
                + "standard ticker as given.\n"
                + "- name: the company/stock name.\n"
                + "- type: STOCK, BOND, or CASH.\n"
                + "- side: BUY or SELL for that single row (default BUY if not stated).\n"
                + "- quantity: the number of shares in that row.\n"
                + "- price: the PER-SHARE price for that row only. If the row gives a TOTAL order value instead "
                + "of a per-share price, divide that row's value by that row's quantity.\n"
                + "- date: that row's transaction date, YYYY-MM-DD.\n\n"
                + "Return ONLY a JSON array (no prose, no markdown fences). Include every row, in the same order "
                + "they appear - do not skip or merge any, even if the same stock appears many times.\n\n"
                + "Data:\n" + tableText;

        List<Map<String, Object>> messages = List.of(Map.of("role", "user", "content", instructions));
        String raw = chatCompletionClient.complete(model, messages);
        List<RawTransaction> transactions = parseTransactions(raw);
        return netByTicker(transactions);
    }

    private List<RawTransaction> parseTransactions(String rawModelOutput) {
        String json = extractJsonArray(rawModelOutput);
        List<Map<String, Object>> rows;
        try {
            rows = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Could not understand the extracted transaction list", e);
        }

        List<RawTransaction> transactions = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            transactions.add(toTransaction(row));
        }
        return transactions;
    }

    private RawTransaction toTransaction(Map<String, Object> row) {
        String ticker = String.valueOf(row.get("ticker")).trim().toUpperCase();
        String name = String.valueOf(row.getOrDefault("name", ticker));

        HoldingType type = HoldingType.STOCK;
        try {
            type = HoldingType.valueOf(String.valueOf(row.getOrDefault("type", "STOCK")).trim().toUpperCase());
        } catch (Exception ignored) {
            // fall back to STOCK
        }

        boolean isSell = "SELL".equalsIgnoreCase(String.valueOf(row.getOrDefault("side", "BUY")).trim());
        BigDecimal quantity = new BigDecimal(String.valueOf(row.get("quantity")).trim());
        BigDecimal price = new BigDecimal(String.valueOf(row.get("price")).trim());

        LocalDate date;
        try {
            Object dateVal = row.get("date");
            date = dateVal != null ? LocalDate.parse(String.valueOf(dateVal).trim()) : LocalDate.now();
        } catch (Exception e) {
            date = LocalDate.now();
        }

        return new RawTransaction(ticker, name, type, isSell, quantity, price, date);
    }

    /**
     * Nets BUY/SELL rows per ticker into a single position using standard weighted-average-cost
     * accounting: a BUY blends into the running average cost, a SELL reduces quantity only and
     * leaves the average cost of the remaining shares unchanged. Tickers whose net position ends
     * at zero or negative are dropped.
     */
    private List<HoldingRequest> netByTicker(List<RawTransaction> transactions) {
        Map<String, List<RawTransaction>> byTicker = new LinkedHashMap<>();
        for (RawTransaction tx : transactions) {
            byTicker.computeIfAbsent(tx.ticker(), k -> new ArrayList<>()).add(tx);
        }

        List<HoldingRequest> requests = new ArrayList<>();
        for (List<RawTransaction> txs : byTicker.values()) {
            txs.sort(Comparator.comparing(RawTransaction::date));

            BigDecimal quantity = BigDecimal.ZERO;
            BigDecimal avgCost = BigDecimal.ZERO;
            LocalDate earliestDate = txs.get(0).date();

            for (RawTransaction tx : txs) {
                if (tx.sell()) {
                    quantity = quantity.subtract(tx.quantity());
                } else {
                    BigDecimal newQuantity = quantity.add(tx.quantity());
                    if (newQuantity.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal existingCost = quantity.multiply(avgCost);
                        BigDecimal addedCost = tx.quantity().multiply(tx.price());
                        avgCost = existingCost.add(addedCost).divide(newQuantity, 6, RoundingMode.HALF_UP);
                    }
                    quantity = newQuantity;
                }
            }

            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            RawTransaction first = txs.get(0);
            HoldingRequest request = new HoldingRequest();
            request.setTicker(first.ticker());
            request.setName(first.name());
            request.setType(first.type());
            request.setQuantity(quantity);
            request.setPurchasePrice(avgCost.setScale(4, RoundingMode.HALF_UP));
            request.setPurchaseDate(earliestDate);
            requests.add(request);
        }
        return requests;
    }

    private String extractJsonArray(String raw) {
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start == -1 || end == -1 || end < start) {
            throw new IllegalStateException("Could not read a transaction list from the file");
        }
        return raw.substring(start, end + 1);
    }

    private record RawTransaction(String ticker, String name, HoldingType type, boolean sell, BigDecimal quantity,
                                   BigDecimal price, LocalDate date) {}
}

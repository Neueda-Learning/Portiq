package com.portiq.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portiq.dto.HoldingRequest;
import com.portiq.model.HoldingType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parses the JSON holdings array that both statement-image scanning and smart file import ask
 * the model to return, into {@link HoldingRequest}s.
 */
@Component
public class LlmHoldingsParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public List<HoldingRequest> parse(String rawModelOutput) {
        String json = extractJsonArray(rawModelOutput);
        List<Map<String, Object>> rows;
        try {
            rows = objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Could not understand the extracted holdings list", e);
        }

        List<HoldingRequest> requests = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            requests.add(toRequest(row));
        }
        return requests;
    }

    private String extractJsonArray(String raw) {
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start == -1 || end == -1 || end < start) {
            throw new IllegalStateException("Could not read a holdings list from the response");
        }
        return raw.substring(start, end + 1);
    }

    private HoldingRequest toRequest(Map<String, Object> row) {
        HoldingRequest request = new HoldingRequest();
        String ticker = String.valueOf(row.get("ticker")).trim().toUpperCase();
        request.setTicker(ticker);
        Object name = row.getOrDefault("name", ticker);
        request.setName(String.valueOf(name));

        HoldingType type = HoldingType.STOCK;
        try {
            type = HoldingType.valueOf(String.valueOf(row.getOrDefault("type", "STOCK")).trim().toUpperCase());
        } catch (Exception ignored) {
            // fall back to STOCK
        }
        request.setType(type);
        request.setQuantity(new BigDecimal(String.valueOf(row.get("quantity")).trim()));
        request.setPurchasePrice(new BigDecimal(String.valueOf(row.get("purchasePrice")).trim()));

        Object dateVal = row.get("purchaseDate");
        LocalDate date;
        try {
            date = dateVal != null ? LocalDate.parse(String.valueOf(dateVal).trim()) : LocalDate.now();
        } catch (Exception e) {
            date = LocalDate.now();
        }
        request.setPurchaseDate(date);
        return request;
    }
}

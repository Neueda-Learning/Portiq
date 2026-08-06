package com.portiq.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portiq.dto.HoldingRequest;
import com.portiq.exception.InvalidRequestException;
import com.portiq.model.HoldingType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the JSON holdings array that both statement-image scanning and smart file import ask
 * the model to return, into {@link HoldingRequest}s.
 *
 * <p>Everything here treats the model's output as untrusted input, because that is what it is: it
 * is generated from a file the user uploaded, so its content is attacker-influenced even though
 * the endpoint producing it is one we configured. Nothing that leaves this class is assumed to be
 * well-formed - {@code HoldingImportService} then runs the same bean constraints a hand-typed
 * holding goes through before any of it reaches the database.
 */
@Component
public class LlmHoldingsParser {

    /**
     * A row as the model was asked to produce it.
     *
     * <p>Every field is a String rather than its eventual type. That is deliberate: a model asked
     * for a number will sometimes answer {@code "1,250"}, {@code "Rs 1250"} or {@code "1250 shares"},
     * and binding those straight to {@code BigDecimal} makes Jackson reject the whole array over
     * one cell. Taking them as text lets {@link #parseNumber} apply the same tolerance the CSV
     * importer uses, and lets a single bad row fail on its own terms with a message naming the
     * field.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawHolding(String ticker, String name, String type,
                              String quantity, String purchasePrice, String purchaseDate) {}

    private final ObjectMapper objectMapper = new ObjectMapper()
            // Numbers, booleans and nulls in the JSON still bind to the String fields above
            // instead of failing the parse, which is the point of taking them as text.
            .configure(DeserializationFeature.FAIL_ON_UNRESOLVED_OBJECT_IDS, false);

    public List<HoldingRequest> parse(String rawModelOutput) {
        String json = extractJsonArray(rawModelOutput);

        List<RawHolding> rows;
        try {
            rows = objectMapper.readValue(json, new TypeReference<List<RawHolding>>() {});
        } catch (JsonProcessingException e) {
            // The message names a line and column in text the user never sees, so it is not
            // passed on. The output itself is not logged either: it is derived from an uploaded
            // statement and can contain the holder's positions.
            throw new IllegalStateException("Could not understand the extracted holdings list");
        }

        List<HoldingRequest> requests = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            RawHolding row = rows.get(i);
            if (row == null) {
                continue;
            }
            requests.add(toRequest(row, i + 1));
        }
        return requests;
    }

    private String extractJsonArray(String raw) {
        if (raw == null) {
            throw new IllegalStateException("Could not read a holdings list from the response");
        }
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start == -1 || end == -1 || end < start) {
            throw new IllegalStateException("Could not read a holdings list from the response");
        }
        return raw.substring(start, end + 1);
    }

    private HoldingRequest toRequest(RawHolding row, int position) {
        HoldingRequest request = new HoldingRequest();

        String ticker = row.ticker() == null ? "" : row.ticker().trim().toUpperCase();
        request.setTicker(ticker);
        request.setName(row.name() == null || row.name().isBlank() ? ticker : row.name().trim());
        request.setType(parseType(row.type()));
        request.setQuantity(parseNumber(row.quantity(), "quantity", position));
        request.setPurchasePrice(parseNumber(row.purchasePrice(), "purchase price", position));
        request.setPurchaseDate(parseDate(row.purchaseDate()));

        return request;
    }

    /** An unrecognised type falls back to STOCK, which is what the overwhelming majority are. */
    private HoldingType parseType(String value) {
        if (value == null || value.isBlank()) {
            return HoldingType.STOCK;
        }
        try {
            return HoldingType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return HoldingType.STOCK;
        }
    }

    /**
     * Reads a numeric field, tolerating the formatting a model tends to carry over from the source
     * document - thousands separators, a currency symbol, a parenthesised negative - and failing
     * with a message that names the field and the row when it genuinely is not a number.
     *
     * <p>The previous version called {@code new BigDecimal(String.valueOf(row.get("quantity")))},
     * which turned a missing field into the literal string "null" and then into a
     * {@code NumberFormatException} whose message was the word "null" - indistinguishable from a
     * cell that really did contain garbage.
     */
    private BigDecimal parseNumber(String value, String fieldName, int position) {
        String raw = value == null ? "" : value.trim();
        if (raw.isEmpty()) {
            throw new InvalidRequestException(
                    "Row " + position + ": no " + fieldName + " could be read from the document.");
        }
        return NumericCellParser.parse(raw).orElseThrow(() -> new InvalidRequestException(
                "Row " + position + ": " + fieldName + " '" + raw + "' is not a number."));
    }

    /**
     * An unreadable or absent date falls back to today rather than failing the row.
     *
     * <p>A date is the one field on a holding that nothing downstream depends on for correctness -
     * quantities and prices drive every calculation - so losing an otherwise good position over a
     * date the model could not read from a blurry screenshot is the wrong trade.
     */
    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return LocalDate.now();
        }
        try {
            LocalDate parsed = LocalDate.parse(value.trim());
            // A future purchase date fails validation later; today is the honest substitute for a
            // misread year, which is the usual cause.
            return parsed.isAfter(LocalDate.now()) ? LocalDate.now() : parsed;
        } catch (DateTimeParseException e) {
            return LocalDate.now();
        }
    }
}

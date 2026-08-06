package com.portiq.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portiq.dto.RecommendationResponse;
import com.portiq.dto.StockRecommendation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Optional LLM polish for recommendation text.
 *
 * <p>The model never decides anything - it receives the already-computed action, score and signal
 * list and is asked only to turn them into readable prose. Every failure path (no API key, timeout,
 * malformed JSON, a ticker the model skipped) falls back to the rule-generated reason that is
 * already on the DTO, so unlike {@code /api/insights/summary} this endpoint never returns 502
 * because the model misbehaved. The response carries {@code llmNarrated} so the UI can say which
 * text it is showing.
 */
@Service
public class RecommendationNarrator {

    private static final Logger log = LoggerFactory.getLogger(RecommendationNarrator.class);

    private static final String SYSTEM_PROMPT =
            "You are a plain-spoken investment analyst. You will be given stock recommendations that have "
                    + "ALREADY been decided by a quantitative model, along with the signals behind each one. "
                    + "Do not change any recommendation, price or number. Rewrite each one's justification as "
                    + "one or two clear sentences an ordinary investor would understand. No markdown, no bullet "
                    + "points, no headings. Respond with JSON only, in exactly this shape: "
                    + "{\"narrative\": \"3-4 sentences summarising the overall picture\", "
                    + "\"reasons\": {\"TICKER\": \"the rewritten justification\"}}";

    private final ChatCompletionClient chatCompletionClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.insights.model:}")
    private String model;

    public RecommendationNarrator(ChatCompletionClient chatCompletionClient) {
        this.chatCompletionClient = chatCompletionClient;
    }

    public boolean isAvailable() {
        return chatCompletionClient.isConfigured();
    }

    /** Rewrites the reasons in place. Leaves the response untouched if anything goes wrong. */
    public void narrate(RecommendationResponse response) {
        if (!isAvailable()) return;

        List<StockRecommendation> all = new ArrayList<>();
        if (response.getHoldings() != null) all.addAll(response.getHoldings());
        if (response.getIdeas() != null) all.addAll(response.getIdeas());
        if (all.isEmpty()) return;

        try {
            String content = chatCompletionClient.complete(model, List.of(
                    Map.of("role", "system", "content", SYSTEM_PROMPT),
                    Map.of("role", "user", "content", buildPrompt(all))
            ));

            JsonNode root = objectMapper.readTree(stripCodeFence(content));

            JsonNode narrative = root.get("narrative");
            if (narrative != null && narrative.isTextual() && !narrative.asText().isBlank()) {
                response.setNarrative(narrative.asText().trim());
            }

            JsonNode reasons = root.get("reasons");
            if (reasons != null && reasons.isObject()) {
                applyReasons(all, reasons);
            }

            response.setLlmNarrated(true);
        } catch (JsonProcessingException e) {
            // The model answered, but not with the JSON it was asked for.
            log.warn("Narration skipped - the model did not return usable JSON: {}", e.getOriginalMessage());
            response.setLlmNarrated(false);
        } catch (Exception e) {
            // Keep the rule-generated reasons. A narration failure is not worth failing the
            // request, but it should not vanish either - otherwise a misconfigured or unreachable
            // model looks exactly like having no model configured at all.
            log.warn("Narration skipped - the summary service could not be reached: {}", e.toString());
            response.setLlmNarrated(false);
        }
    }

    private void applyReasons(List<StockRecommendation> recommendations, JsonNode reasons) {
        for (StockRecommendation rec : recommendations) {
            JsonNode reason = findReason(reasons, rec.getTicker());
            if (reason != null && reason.isTextual() && !reason.asText().isBlank()) {
                rec.setReason(reason.asText().trim());
            }
        }
    }

    /** Models are inconsistent about ticker casing, so match case-insensitively. */
    private JsonNode findReason(JsonNode reasons, String ticker) {
        if (ticker == null) return null;

        JsonNode exact = reasons.get(ticker);
        if (exact != null) return exact;

        Iterator<String> names = reasons.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (name.equalsIgnoreCase(ticker)) return reasons.get(name);
        }
        return null;
    }

    private String buildPrompt(List<StockRecommendation> recommendations) {
        StringBuilder sb = new StringBuilder();
        for (StockRecommendation rec : recommendations) {
            sb.append(rec.getTicker())
                    .append(" (").append(rec.getName()).append(")")
                    .append(" - action ").append(rec.getAction())
                    .append(", currently ").append(rec.getCurrentPrice())
                    .append(rec.isHeld() ? ", held" : ", not held")
                    .append(", score ").append(rec.getOpportunityScore())
                    .append(", risk ").append(rec.getRiskScore()).append("/100")
                    .append(". Signals: ")
                    .append(rec.getSignals() == null || rec.getSignals().isEmpty()
                            ? "none"
                            : String.join("; ", rec.getSignals()))
                    .append("\n");
        }
        return sb.toString();
    }

    /** Models routinely wrap JSON in ```json fences despite being told not to. */
    private String stripCodeFence(String content) {
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) return trimmed;

        int firstNewline = trimmed.indexOf('\n');
        if (firstNewline < 0) return trimmed;

        String body = trimmed.substring(firstNewline + 1);
        int closing = body.lastIndexOf("```");
        return closing < 0 ? body.trim() : body.substring(0, closing).trim();
    }
}

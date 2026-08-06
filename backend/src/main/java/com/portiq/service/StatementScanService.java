package com.portiq.service;

import com.portiq.dto.HoldingRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Extracts holdings from a photo or screenshot of a brokerage/portfolio statement using a
 * vision-capable chat completion model.
 */
@Service
public class StatementScanService {

    private final ChatCompletionClient chatCompletionClient;
    private final LlmHoldingsParser holdingsParser;

    @Value("${app.insights.vision-model:}")
    private String visionModel;

    public StatementScanService(ChatCompletionClient chatCompletionClient, LlmHoldingsParser holdingsParser) {
        this.chatCompletionClient = chatCompletionClient;
        this.holdingsParser = holdingsParser;
    }

    public boolean isAvailable() {
        return chatCompletionClient.isConfigured();
    }

    /** The environment variable that needs setting, or null when the feature is ready. */
    public String missingConfiguration() {
        return chatCompletionClient.missingConfiguration();
    }

    public List<HoldingRequest> extractHoldings(MultipartFile file) throws IOException {
        String base64 = Base64.getEncoder().encodeToString(file.getBytes());
        String contentType = file.getContentType() != null ? file.getContentType() : "image/png";
        String dataUrl = "data:" + contentType + ";base64," + base64;

        String instructions = "Look at this brokerage or portfolio statement image. Return ONLY a JSON array "
                + "(no prose, no markdown fences) of objects with keys: ticker, name, type (STOCK, BOND, or CASH), "
                + "quantity, purchasePrice, purchaseDate (YYYY-MM-DD, use today's date if not visible). "
                + "If a field cannot be read exactly, make a reasonable estimate rather than skipping the row.";

        List<Map<String, Object>> content = List.of(
                Map.of("type", "text", "text", instructions),
                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))
        );

        List<Map<String, Object>> messages = List.of(Map.of("role", "user", "content", content));

        String raw = chatCompletionClient.complete(visionModel, messages);
        return holdingsParser.parse(raw);
    }
}

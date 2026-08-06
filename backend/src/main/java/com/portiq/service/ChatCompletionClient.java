package com.portiq.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin client for an OpenAI-compatible chat completions endpoint. The provider is fully
 * configurable via environment variables (INSIGHTS_API_KEY / INSIGHTS_API_URL) so this class
 * has no vendor-specific knowledge baked in.
 */
@Component
public class ChatCompletionClient {

    @Value("${app.insights.api-key:}")
    private String apiKey;

    @Value("${app.insights.api-url:}")
    private String apiUrl;

    private final RestTemplate restTemplate;

    public ChatCompletionClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && apiUrl != null && !apiUrl.isBlank();
    }

    /**
     * The endpoint is an operator setting, not user input, so it is not run through
     * {@code OutboundUrlGuard} - a self-hosted model on a private address is a legitimate
     * configuration, and an allowlist would forbid it. What is checked is the scheme: the API key
     * travels in an {@code Authorization} header on every call, and over plain http that key is
     * readable by anything on the path. Loopback is exempt because it never leaves the machine.
     */
    private void checkEndpoint() {
        String url = apiUrl.trim().toLowerCase();
        boolean localhost = url.startsWith("http://localhost") || url.startsWith("http://127.0.0.1")
                || url.startsWith("http://[::1]");
        if (url.startsWith("http://") && !localhost) {
            throw new IllegalStateException(
                    "INSIGHTS_API_URL must use https - the API key is sent with every request");
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalStateException("INSIGHTS_API_URL must be an http(s) URL");
        }
    }

    @SuppressWarnings("unchecked")
    public String complete(String model, List<Map<String, Object>> messages) {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "This feature is not configured. Set INSIGHTS_API_KEY and INSIGHTS_API_URL in the backend environment.");
        }
        checkEndpoint();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", 0.3);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, entity, Map.class);

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null) {
            throw new IllegalStateException("Empty response from the summary service");
        }

        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("The summary service returned no choices");
        }

        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        Object content = message.get("content");
        if (content == null) {
            throw new IllegalStateException("The summary service returned an empty message");
        }
        return content.toString();
    }
}

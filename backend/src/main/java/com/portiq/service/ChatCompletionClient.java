package com.portiq.service;

import com.portiq.dto.ChatCompletionResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ChatCompletionClient.class);

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
     * Names the environment variable that is missing, or null when the client is ready.
     *
     * <p>Exists because "not configured on this server" is a dead end for whoever reads it. Both
     * halves have to be set, only one is usually forgotten, and the message gave no way to tell
     * which - so the reader's next move was to go reading source. Naming the variable is not a
     * disclosure risk: the names are in the public repository, and the value never appears here.
     */
    public String missingConfiguration() {
        boolean noKey = apiKey == null || apiKey.isBlank();
        boolean noUrl = apiUrl == null || apiUrl.isBlank();

        if (noKey && noUrl) return "INSIGHTS_API_KEY and INSIGHTS_API_URL";
        if (noKey) return "INSIGHTS_API_KEY";
        if (noUrl) return "INSIGHTS_API_URL";
        return null;
    }

    /**
     * States at startup whether the AI-backed features are on. Silence about a disabled optional
     * feature is what let this go unnoticed until someone clicked the button and got a shrug.
     */
    @PostConstruct
    void reportConfiguration() {
        if (isConfigured()) {
            log.info("AI features enabled - summaries, smart import and statement scanning are available");
        } else {
            log.info("AI features disabled - {} not set. Summaries, smart import and statement "
                    + "scanning will report that they are unavailable; everything else is unaffected.",
                    missingConfiguration());
        }
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

        // Bound to a record rather than a raw Map: the endpoint is configurable per deployment, so
        // the response can come from a self-hosted gateway or a proxy that wraps errors in its own
        // envelope. Jackson checks the shape once, here, instead of a ClassCastException surfacing
        // from the middle of the parsing below.
        ResponseEntity<ChatCompletionResponse> response =
                restTemplate.postForEntity(apiUrl, entity, ChatCompletionResponse.class);

        ChatCompletionResponse responseBody = response.getBody();
        if (responseBody == null) {
            throw new IllegalStateException("Empty response from the model service");
        }

        String content = responseBody.firstContent();
        if (content == null || content.isBlank()) {
            // One message for all three shapes of "no usable text": no choices, no message, or a
            // null content because the model stopped on a filter or returned a tool call. The
            // distinction changes nothing for any caller - each falls back the same way.
            throw new IllegalStateException("The model service returned no usable content");
        }
        return content;
    }
}

package com.portiq.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The chat endpoint is configurable per deployment, so this payload can come from a self-hosted
 * gateway or a proxy that wraps things in its own envelope. Every level has been seen missing.
 */
class ChatCompletionResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsTheAssistantContent() throws Exception {
        ChatCompletionResponse response = objectMapper.readValue("""
                {"choices":[{"message":{"role":"assistant","content":"Your portfolio is up 8%."}}]}
                """, ChatCompletionResponse.class);

        assertThat(response.firstContent()).isEqualTo("Your portfolio is up 8%.");
    }

    @Test
    void ignoresTheVendorExtensionsProvidersAddFreely() throws Exception {
        // usage counts, fingerprints and assorted extras must never break a running deployment.
        ChatCompletionResponse response = objectMapper.readValue("""
                {"id":"chatcmpl-1","object":"chat.completion","created":1700000000,
                 "model":"llama-3.3-70b","system_fingerprint":"fp_abc",
                 "usage":{"prompt_tokens":10,"completion_tokens":20,"total_tokens":30},
                 "choices":[{"index":0,"finish_reason":"stop",
                             "message":{"role":"assistant","content":"Done."}}]}
                """, ChatCompletionResponse.class);

        assertThat(response.firstContent()).isEqualTo("Done.");
    }

    @Test
    void returnsNullRatherThanThrowingWhenThereAreNoChoices() throws Exception {
        // Some providers return an empty choices array when a request is filtered.
        assertThat(objectMapper.readValue("{\"choices\":[]}", ChatCompletionResponse.class)
                .firstContent()).isNull();
        assertThat(objectMapper.readValue("{}", ChatCompletionResponse.class)
                .firstContent()).isNull();
    }

    @Test
    void returnsNullWhenTheChoiceCarriesNoMessage() throws Exception {
        assertThat(objectMapper.readValue("{\"choices\":[{\"index\":0}]}", ChatCompletionResponse.class)
                .firstContent()).isNull();
    }

    @Test
    void returnsNullWhenContentIsNull() throws Exception {
        // A model that stops on a content filter, or returns a tool call, sends a null content.
        ChatCompletionResponse response = objectMapper.readValue("""
                {"choices":[{"finish_reason":"content_filter",
                             "message":{"role":"assistant","content":null}}]}
                """, ChatCompletionResponse.class);

        assertThat(response.firstContent()).isNull();
    }
}

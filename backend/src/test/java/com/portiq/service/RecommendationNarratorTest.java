package com.portiq.service;

import com.portiq.dto.RecommendationResponse;
import com.portiq.dto.StockRecommendation;
import com.portiq.model.RecommendationAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The narrator's whole contract is that it can fail without taking the request down with it, so
 * most of these tests are failure paths.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecommendationNarratorTest {

    private static final String RULE_REASON = "Nothing in the numbers argues for changing your AAA position.";

    @Mock
    private ChatCompletionClient chatCompletionClient;

    private RecommendationNarrator narrator;

    @BeforeEach
    void setUp() {
        narrator = new RecommendationNarrator(chatCompletionClient);
        ReflectionTestUtils.setField(narrator, "model", "test-model");
        when(chatCompletionClient.isConfigured()).thenReturn(true);
    }

    @Test
    void narrate_rewritesReasonsAndSetsNarrative() {
        when(chatCompletionClient.complete(anyString(), any())).thenReturn(
                "{\"narrative\":\"Your book looks steady.\",\"reasons\":{\"AAA\":\"Alpha is holding its trend.\"}}");

        RecommendationResponse response = response();
        narrator.narrate(response);

        assertThat(response.isLlmNarrated()).isTrue();
        assertThat(response.getNarrative()).isEqualTo("Your book looks steady.");
        assertThat(response.getHoldings().get(0).getReason()).isEqualTo("Alpha is holding its trend.");
    }

    @Test
    void narrate_stripsMarkdownCodeFences() {
        when(chatCompletionClient.complete(anyString(), any())).thenReturn(
                "```json\n{\"narrative\":\"Steady.\",\"reasons\":{\"AAA\":\"Rewritten.\"}}\n```");

        RecommendationResponse response = response();
        narrator.narrate(response);

        assertThat(response.isLlmNarrated()).isTrue();
        assertThat(response.getHoldings().get(0).getReason()).isEqualTo("Rewritten.");
    }

    @Test
    void narrate_matchesTickersCaseInsensitively() {
        when(chatCompletionClient.complete(anyString(), any())).thenReturn(
                "{\"reasons\":{\"aaa\":\"Lowercase key.\"}}");

        RecommendationResponse response = response();
        narrator.narrate(response);

        assertThat(response.getHoldings().get(0).getReason()).isEqualTo("Lowercase key.");
    }

    @Test
    void narrate_clientThrows_keepsRuleGeneratedText() {
        when(chatCompletionClient.complete(anyString(), any()))
                .thenThrow(new IllegalStateException("upstream exploded"));

        RecommendationResponse response = response();
        narrator.narrate(response);

        assertThat(response.isLlmNarrated()).isFalse();
        assertThat(response.getHoldings().get(0).getReason()).isEqualTo(RULE_REASON);
    }

    @Test
    void narrate_malformedJson_keepsRuleGeneratedText() {
        when(chatCompletionClient.complete(anyString(), any())).thenReturn("this is not json at all");

        RecommendationResponse response = response();
        narrator.narrate(response);

        assertThat(response.isLlmNarrated()).isFalse();
        assertThat(response.getHoldings().get(0).getReason()).isEqualTo(RULE_REASON);
    }

    @Test
    void narrate_tickerMissingFromResponse_keepsThatOnesRuleText() {
        when(chatCompletionClient.complete(anyString(), any())).thenReturn(
                "{\"narrative\":\"Fine.\",\"reasons\":{\"ZZZ\":\"About a different stock.\"}}");

        RecommendationResponse response = response();
        narrator.narrate(response);

        assertThat(response.isLlmNarrated()).isTrue();
        assertThat(response.getHoldings().get(0).getReason()).isEqualTo(RULE_REASON);
    }

    @Test
    void narrate_notConfigured_makesNoCallAtAll() {
        when(chatCompletionClient.isConfigured()).thenReturn(false);

        RecommendationResponse response = response();
        narrator.narrate(response);

        verify(chatCompletionClient, never()).complete(anyString(), any());
        assertThat(response.isLlmNarrated()).isFalse();
        assertThat(response.getHoldings().get(0).getReason()).isEqualTo(RULE_REASON);
    }

    @Test
    void narrate_nothingToNarrate_makesNoCall() {
        RecommendationResponse response = new RecommendationResponse();
        response.setHoldings(List.of());
        response.setIdeas(List.of());

        narrator.narrate(response);

        verify(chatCompletionClient, never()).complete(anyString(), any());
    }

    private static RecommendationResponse response() {
        StockRecommendation rec = new StockRecommendation();
        rec.setTicker("AAA");
        rec.setName("Alpha");
        rec.setAction(RecommendationAction.HOLD);
        rec.setCurrentPrice(new BigDecimal("110.00"));
        rec.setOpportunityScore(new BigDecimal("5.00"));
        rec.setRiskScore(new BigDecimal("42.00"));
        rec.setSignals(List.of("Trading above both its 50- and 200-day averages"));
        rec.setReason(RULE_REASON);

        RecommendationResponse response = new RecommendationResponse();
        response.setHoldings(List.of(rec));
        response.setIdeas(List.of());
        return response;
    }
}

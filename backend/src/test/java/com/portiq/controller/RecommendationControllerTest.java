package com.portiq.controller;

import com.portiq.dto.RecommendationResponse;
import com.portiq.dto.StockRecommendation;
import com.portiq.exception.GlobalExceptionHandler;
import com.portiq.model.DataQuality;
import com.portiq.model.RecommendationAction;
import com.portiq.model.RiskLevel;
import com.portiq.service.RecommendationService;
import com.portiq.service.RiskAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RecommendationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private RecommendationService recommendationService;

    @InjectMocks
    private RecommendationController recommendationController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(recommendationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getRecommendations_returns200WithHoldingsAndIdeas() throws Exception {
        when(recommendationService.getRecommendations(true, true)).thenReturn(response());

        mockMvc.perform(get("/api/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdings[0].ticker").value("AAA"))
                .andExpect(jsonPath("$.holdings[0].action").value("HOLD"))
                .andExpect(jsonPath("$.holdings[0].currentPrice").value(110.0))
                .andExpect(jsonPath("$.holdings[0].reason").exists())
                .andExpect(jsonPath("$.holdings[0].signals[0]").exists())
                .andExpect(jsonPath("$.ideas[0].ticker").value("BBB"))
                .andExpect(jsonPath("$.ideas[0].action").value("BUY"))
                .andExpect(jsonPath("$.disclaimer").exists());
    }

    @Test
    void getRecommendations_honoursQueryParameters() throws Exception {
        when(recommendationService.getRecommendations(false, false)).thenReturn(response());

        mockMvc.perform(get("/api/recommendations?includeIdeas=false&narrate=false"))
                .andExpect(status().isOk());

        verify(recommendationService).getRecommendations(false, false);
    }

    @Test
    void getRecommendation_singleTicker_returns200() throws Exception {
        when(recommendationService.recommendTicker("AAA")).thenReturn(held());

        mockMvc.perform(get("/api/recommendations/AAA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticker").value("AAA"))
                .andExpect(jsonPath("$.held").value(true))
                .andExpect(jsonPath("$.riskLevel").value("MODERATE"))
                .andExpect(jsonPath("$.trend").value("UPTREND"));
    }

    /** No API key means rule-generated prose, not a 503 - unlike /api/insights/summary. */
    @Test
    void getRecommendations_withoutLlm_stillReturns200() throws Exception {
        RecommendationResponse unnarrated = response();
        unnarrated.setLlmNarrated(false);
        unnarrated.setNarrative(null);
        when(recommendationService.getRecommendations(true, true)).thenReturn(unnarrated);

        mockMvc.perform(get("/api/recommendations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.llmNarrated").value(false))
                .andExpect(jsonPath("$.holdings[0].reason").exists());
    }

    private static RecommendationResponse response() {
        RecommendationResponse response = new RecommendationResponse();
        response.setHoldings(List.of(held()));
        response.setIdeas(List.of(idea()));
        response.setNarrative("Your portfolio is holding up.");
        response.setLlmNarrated(true);
        response.setGeneratedAt(Instant.parse("2026-08-06T00:00:00Z"));
        response.setDisclaimer(RiskAnalysisService.DISCLAIMER);
        return response;
    }

    private static StockRecommendation held() {
        StockRecommendation rec = new StockRecommendation();
        rec.setTicker("AAA");
        rec.setName("Alpha");
        rec.setCurrentPrice(new BigDecimal("110.00"));
        rec.setAction(RecommendationAction.HOLD);
        rec.setConfidence(new BigDecimal("35.00"));
        rec.setReason("Nothing in the numbers argues for changing your Alpha position.");
        rec.setSignals(List.of("Trading above both its 50- and 200-day averages"));
        rec.setOpportunityScore(new BigDecimal("8.00"));
        rec.setHeld(true);
        rec.setQuantity(new BigDecimal("10"));
        rec.setCurrentValue(new BigDecimal("1100.00"));
        rec.setGainLossPercent(new BigDecimal("10.00"));
        rec.setWeightPercent(new BigDecimal("22.00"));
        rec.setSuggestedWeightPercent(new BigDecimal("22.00"));
        rec.setRiskScore(new BigDecimal("44.00"));
        rec.setRiskLevel(RiskLevel.MODERATE);
        rec.setDataQuality(DataQuality.SUFFICIENT);
        rec.setTrend("UPTREND");
        return rec;
    }

    private static StockRecommendation idea() {
        StockRecommendation rec = new StockRecommendation();
        rec.setTicker("BBB");
        rec.setName("Beta");
        rec.setCurrentPrice(new BigDecimal("250.00"));
        rec.setAction(RecommendationAction.BUY);
        rec.setConfidence(new BigDecimal("58.00"));
        rec.setReason("Beta (BBB) screens as a buy candidate at 250.00.");
        rec.setSignals(List.of("Near the bottom of its 52-week range"));
        rec.setOpportunityScore(new BigDecimal("58.00"));
        rec.setHeld(false);
        rec.setSuggestedWeightPercent(new BigDecimal("10.00"));
        rec.setRiskScore(new BigDecimal("38.00"));
        rec.setRiskLevel(RiskLevel.MODERATE);
        rec.setDataQuality(DataQuality.SUFFICIENT);
        rec.setTrend("SIDEWAYS");
        return rec;
    }
}

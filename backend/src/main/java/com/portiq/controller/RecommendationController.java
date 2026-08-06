package com.portiq.controller;

import com.portiq.dto.RecommendationResponse;
import com.portiq.dto.StockRecommendation;
import com.portiq.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Buy/hold/sell calls for held stocks and new ideas.
 *
 * <p>Works without an API key: the calls are rule-based. Setting INSIGHTS_API_KEY only improves the
 * wording of the reasons, so unlike the insights endpoint there is no 503 path here.
 */
@RestController
@RequestMapping("/api/recommendations")
@Tag(name = "Recommendations", description = "Stock recommendations with prices, actions and reasons")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping
    @Operation(summary = "Recommendations for every holding, plus new ideas outside the portfolio")
    public RecommendationResponse getRecommendations(
            @RequestParam(defaultValue = "true") boolean includeIdeas,
            @RequestParam(defaultValue = "true") boolean narrate) {
        return recommendationService.getRecommendations(includeIdeas, narrate);
    }

    @GetMapping("/{ticker}")
    @Operation(summary = "Recommendation for a single stock, held or not")
    public StockRecommendation getRecommendation(@PathVariable String ticker) {
        return recommendationService.recommendTicker(ticker);
    }
}

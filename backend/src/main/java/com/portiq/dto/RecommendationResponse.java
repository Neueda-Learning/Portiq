package com.portiq.dto;

import java.time.Instant;
import java.util.List;

/** Recommendations for what you already own, plus ideas you don't. */
public class RecommendationResponse {

    private List<StockRecommendation> holdings;
    private List<StockRecommendation> ideas;

    /** A few sentences tying the individual calls together. */
    private String narrative;

    /** False when no LLM is configured - the reasons are then rule-generated, which is fine. */
    private boolean llmNarrated;

    private Instant generatedAt;
    private String disclaimer;

    public List<StockRecommendation> getHoldings() { return holdings; }
    public void setHoldings(List<StockRecommendation> holdings) { this.holdings = holdings; }
    public List<StockRecommendation> getIdeas() { return ideas; }
    public void setIdeas(List<StockRecommendation> ideas) { this.ideas = ideas; }
    public String getNarrative() { return narrative; }
    public void setNarrative(String narrative) { this.narrative = narrative; }
    public boolean isLlmNarrated() { return llmNarrated; }
    public void setLlmNarrated(boolean llmNarrated) { this.llmNarrated = llmNarrated; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
    public String getDisclaimer() { return disclaimer; }
    public void setDisclaimer(String disclaimer) { this.disclaimer = disclaimer; }
}

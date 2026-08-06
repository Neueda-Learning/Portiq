package com.portiq.dto;

import com.portiq.model.DataQuality;
import com.portiq.model.RecommendationAction;
import com.portiq.model.RiskLevel;

import java.math.BigDecimal;
import java.util.List;

/** A buy/hold/sell call on one stock, with the price, the reason, and the numbers behind it. */
public class StockRecommendation {

    private String ticker;
    private String name;
    private BigDecimal currentPrice;
    private RecommendationAction action;

    /** 0-100. Reflects how strongly the signals agree, discounted when history is thin. */
    private BigDecimal confidence;

    /** One-paragraph plain-English explanation. Rewritten by the LLM when one is configured. */
    private String reason;

    /** The individual rule hits that produced the call, each already human-readable. */
    private List<String> signals;

    /** -100 (strong sell) to +100 (strong buy). */
    private BigDecimal opportunityScore;

    private boolean held;
    private BigDecimal quantity;
    private BigDecimal currentValue;
    private BigDecimal gainLossPercent;
    private BigDecimal weightPercent;
    private BigDecimal suggestedWeightPercent;

    private BigDecimal riskScore;
    private RiskLevel riskLevel;
    private DataQuality dataQuality;

    private BigDecimal momentum30dPercent;
    private BigDecimal momentum90dPercent;
    private BigDecimal rsi14;
    private BigDecimal pricePositionPercent;
    private BigDecimal annualisedVolatilityPercent;
    private BigDecimal beta;

    /** "UPTREND", "DOWNTREND" or "SIDEWAYS", from price against its 50- and 200-day averages. */
    private String trend;

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
    public RecommendationAction getAction() { return action; }
    public void setAction(RecommendationAction action) { this.action = action; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public List<String> getSignals() { return signals; }
    public void setSignals(List<String> signals) { this.signals = signals; }
    public BigDecimal getOpportunityScore() { return opportunityScore; }
    public void setOpportunityScore(BigDecimal opportunityScore) { this.opportunityScore = opportunityScore; }
    public boolean isHeld() { return held; }
    public void setHeld(boolean held) { this.held = held; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getCurrentValue() { return currentValue; }
    public void setCurrentValue(BigDecimal currentValue) { this.currentValue = currentValue; }
    public BigDecimal getGainLossPercent() { return gainLossPercent; }
    public void setGainLossPercent(BigDecimal gainLossPercent) { this.gainLossPercent = gainLossPercent; }
    public BigDecimal getWeightPercent() { return weightPercent; }
    public void setWeightPercent(BigDecimal weightPercent) { this.weightPercent = weightPercent; }
    public BigDecimal getSuggestedWeightPercent() { return suggestedWeightPercent; }
    public void setSuggestedWeightPercent(BigDecimal suggestedWeightPercent) { this.suggestedWeightPercent = suggestedWeightPercent; }
    public BigDecimal getRiskScore() { return riskScore; }
    public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }
    public DataQuality getDataQuality() { return dataQuality; }
    public void setDataQuality(DataQuality dataQuality) { this.dataQuality = dataQuality; }
    public BigDecimal getMomentum30dPercent() { return momentum30dPercent; }
    public void setMomentum30dPercent(BigDecimal momentum30dPercent) { this.momentum30dPercent = momentum30dPercent; }
    public BigDecimal getMomentum90dPercent() { return momentum90dPercent; }
    public void setMomentum90dPercent(BigDecimal momentum90dPercent) { this.momentum90dPercent = momentum90dPercent; }
    public BigDecimal getRsi14() { return rsi14; }
    public void setRsi14(BigDecimal rsi14) { this.rsi14 = rsi14; }
    public BigDecimal getPricePositionPercent() { return pricePositionPercent; }
    public void setPricePositionPercent(BigDecimal pricePositionPercent) { this.pricePositionPercent = pricePositionPercent; }
    public BigDecimal getAnnualisedVolatilityPercent() { return annualisedVolatilityPercent; }
    public void setAnnualisedVolatilityPercent(BigDecimal annualisedVolatilityPercent) { this.annualisedVolatilityPercent = annualisedVolatilityPercent; }
    public BigDecimal getBeta() { return beta; }
    public void setBeta(BigDecimal beta) { this.beta = beta; }
    public String getTrend() { return trend; }
    public void setTrend(String trend) { this.trend = trend; }
}

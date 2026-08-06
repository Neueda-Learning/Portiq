package com.portiq.dto;

import com.portiq.model.HoldingType;
import com.portiq.model.RiskLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Portfolio-wide risk roll-up plus the per-holding breakdown it was built from. */
public class PortfolioRiskReport {

    private BigDecimal overallRiskScore;
    private RiskLevel riskLevel;

    /**
     * Volatility of the portfolio as a whole, derived from a weighted daily return series rather
     * than by averaging the individual volatilities - so it reflects the diversification benefit
     * and will normally sit below the riskiest holding's own figure.
     */
    private BigDecimal portfolioVolatilityPercent;
    private BigDecimal portfolioBeta;
    private BigDecimal portfolioMaxDrawdownPercent;
    private BigDecimal sharpeRatio;

    /** 0-100, higher is better diversified. Derived from the Herfindahl index of position weights. */
    private BigDecimal diversificationScore;
    private BigDecimal concentrationHhi;
    private String topHoldingTicker;
    private BigDecimal topHoldingWeightPercent;
    private int holdingsCount;

    private Map<HoldingType, BigDecimal> allocationByType;
    private List<StockRisk> holdings;
    private List<StockRisk> highestRiskHoldings;
    private List<String> warnings;

    private String benchmark;
    private Instant generatedAt;
    private String disclaimer;

    public BigDecimal getOverallRiskScore() { return overallRiskScore; }
    public void setOverallRiskScore(BigDecimal overallRiskScore) { this.overallRiskScore = overallRiskScore; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }
    public BigDecimal getPortfolioVolatilityPercent() { return portfolioVolatilityPercent; }
    public void setPortfolioVolatilityPercent(BigDecimal portfolioVolatilityPercent) { this.portfolioVolatilityPercent = portfolioVolatilityPercent; }
    public BigDecimal getPortfolioBeta() { return portfolioBeta; }
    public void setPortfolioBeta(BigDecimal portfolioBeta) { this.portfolioBeta = portfolioBeta; }
    public BigDecimal getPortfolioMaxDrawdownPercent() { return portfolioMaxDrawdownPercent; }
    public void setPortfolioMaxDrawdownPercent(BigDecimal portfolioMaxDrawdownPercent) { this.portfolioMaxDrawdownPercent = portfolioMaxDrawdownPercent; }
    public BigDecimal getSharpeRatio() { return sharpeRatio; }
    public void setSharpeRatio(BigDecimal sharpeRatio) { this.sharpeRatio = sharpeRatio; }
    public BigDecimal getDiversificationScore() { return diversificationScore; }
    public void setDiversificationScore(BigDecimal diversificationScore) { this.diversificationScore = diversificationScore; }
    public BigDecimal getConcentrationHhi() { return concentrationHhi; }
    public void setConcentrationHhi(BigDecimal concentrationHhi) { this.concentrationHhi = concentrationHhi; }
    public String getTopHoldingTicker() { return topHoldingTicker; }
    public void setTopHoldingTicker(String topHoldingTicker) { this.topHoldingTicker = topHoldingTicker; }
    public BigDecimal getTopHoldingWeightPercent() { return topHoldingWeightPercent; }
    public void setTopHoldingWeightPercent(BigDecimal topHoldingWeightPercent) { this.topHoldingWeightPercent = topHoldingWeightPercent; }
    public int getHoldingsCount() { return holdingsCount; }
    public void setHoldingsCount(int holdingsCount) { this.holdingsCount = holdingsCount; }
    public Map<HoldingType, BigDecimal> getAllocationByType() { return allocationByType; }
    public void setAllocationByType(Map<HoldingType, BigDecimal> allocationByType) { this.allocationByType = allocationByType; }
    public List<StockRisk> getHoldings() { return holdings; }
    public void setHoldings(List<StockRisk> holdings) { this.holdings = holdings; }
    public List<StockRisk> getHighestRiskHoldings() { return highestRiskHoldings; }
    public void setHighestRiskHoldings(List<StockRisk> highestRiskHoldings) { this.highestRiskHoldings = highestRiskHoldings; }
    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    public String getBenchmark() { return benchmark; }
    public void setBenchmark(String benchmark) { this.benchmark = benchmark; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
    public String getDisclaimer() { return disclaimer; }
    public void setDisclaimer(String disclaimer) { this.disclaimer = disclaimer; }
}

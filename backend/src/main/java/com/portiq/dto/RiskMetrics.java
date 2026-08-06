package com.portiq.dto;

import java.math.BigDecimal;

/**
 * The raw measurements behind a risk score, exposed so the UI can show the working rather than
 * just a number. Any field may be null when the price series was too short to compute it.
 */
public class RiskMetrics {

    private BigDecimal annualisedVolatilityPercent;
    private BigDecimal beta;
    private BigDecimal maxDrawdownPercent;
    private BigDecimal sharpeRatio;
    private BigDecimal valueAtRisk95Percent;
    private BigDecimal rsi14;
    private BigDecimal momentum30dPercent;
    private BigDecimal momentum90dPercent;
    private BigDecimal fiftyTwoWeekHigh;
    private BigDecimal fiftyTwoWeekLow;
    private BigDecimal pricePositionPercent;
    private BigDecimal sma50;
    private BigDecimal sma200;
    private int dataPoints;

    public BigDecimal getAnnualisedVolatilityPercent() { return annualisedVolatilityPercent; }
    public void setAnnualisedVolatilityPercent(BigDecimal annualisedVolatilityPercent) { this.annualisedVolatilityPercent = annualisedVolatilityPercent; }
    public BigDecimal getBeta() { return beta; }
    public void setBeta(BigDecimal beta) { this.beta = beta; }
    public BigDecimal getMaxDrawdownPercent() { return maxDrawdownPercent; }
    public void setMaxDrawdownPercent(BigDecimal maxDrawdownPercent) { this.maxDrawdownPercent = maxDrawdownPercent; }
    public BigDecimal getSharpeRatio() { return sharpeRatio; }
    public void setSharpeRatio(BigDecimal sharpeRatio) { this.sharpeRatio = sharpeRatio; }
    public BigDecimal getValueAtRisk95Percent() { return valueAtRisk95Percent; }
    public void setValueAtRisk95Percent(BigDecimal valueAtRisk95Percent) { this.valueAtRisk95Percent = valueAtRisk95Percent; }
    public BigDecimal getRsi14() { return rsi14; }
    public void setRsi14(BigDecimal rsi14) { this.rsi14 = rsi14; }
    public BigDecimal getMomentum30dPercent() { return momentum30dPercent; }
    public void setMomentum30dPercent(BigDecimal momentum30dPercent) { this.momentum30dPercent = momentum30dPercent; }
    public BigDecimal getMomentum90dPercent() { return momentum90dPercent; }
    public void setMomentum90dPercent(BigDecimal momentum90dPercent) { this.momentum90dPercent = momentum90dPercent; }
    public BigDecimal getFiftyTwoWeekHigh() { return fiftyTwoWeekHigh; }
    public void setFiftyTwoWeekHigh(BigDecimal fiftyTwoWeekHigh) { this.fiftyTwoWeekHigh = fiftyTwoWeekHigh; }
    public BigDecimal getFiftyTwoWeekLow() { return fiftyTwoWeekLow; }
    public void setFiftyTwoWeekLow(BigDecimal fiftyTwoWeekLow) { this.fiftyTwoWeekLow = fiftyTwoWeekLow; }
    public BigDecimal getPricePositionPercent() { return pricePositionPercent; }
    public void setPricePositionPercent(BigDecimal pricePositionPercent) { this.pricePositionPercent = pricePositionPercent; }
    public BigDecimal getSma50() { return sma50; }
    public void setSma50(BigDecimal sma50) { this.sma50 = sma50; }
    public BigDecimal getSma200() { return sma200; }
    public void setSma200(BigDecimal sma200) { this.sma200 = sma200; }
    public int getDataPoints() { return dataPoints; }
    public void setDataPoints(int dataPoints) { this.dataPoints = dataPoints; }
}

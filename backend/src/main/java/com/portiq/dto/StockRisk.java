package com.portiq.dto;

import com.portiq.model.DataQuality;
import com.portiq.model.RiskLevel;

import java.math.BigDecimal;
import java.util.List;

/** Risk assessment for a single stock, held or not. */
public class StockRisk {

    private String ticker;
    private String name;
    private BigDecimal currentPrice;
    private BigDecimal weightPercent;
    private BigDecimal riskScore;
    private RiskLevel riskLevel;
    private DataQuality dataQuality;
    private List<String> drivers;
    private RiskMetrics metrics;

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
    public BigDecimal getWeightPercent() { return weightPercent; }
    public void setWeightPercent(BigDecimal weightPercent) { this.weightPercent = weightPercent; }
    public BigDecimal getRiskScore() { return riskScore; }
    public void setRiskScore(BigDecimal riskScore) { this.riskScore = riskScore; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }
    public DataQuality getDataQuality() { return dataQuality; }
    public void setDataQuality(DataQuality dataQuality) { this.dataQuality = dataQuality; }
    public List<String> getDrivers() { return drivers; }
    public void setDrivers(List<String> drivers) { this.drivers = drivers; }
    public RiskMetrics getMetrics() { return metrics; }
    public void setMetrics(RiskMetrics metrics) { this.metrics = metrics; }
}

package com.portiq.dto;

import java.math.BigDecimal;

public class PortfolioHistoryPoint {

    private long timestamp;
    private BigDecimal value;

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public BigDecimal getValue() { return value; }
    public void setValue(BigDecimal value) { this.value = value; }
}

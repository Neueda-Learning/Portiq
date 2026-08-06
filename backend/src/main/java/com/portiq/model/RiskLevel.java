package com.portiq.model;

/** Bucketed reading of a 0-100 risk score, for colour-coding and plain-language labels. */
public enum RiskLevel {
    LOW,
    MODERATE,
    HIGH,
    VERY_HIGH;

    public static RiskLevel fromScore(double score) {
        if (score < 25) return LOW;
        if (score < 50) return MODERATE;
        if (score < 75) return HIGH;
        return VERY_HIGH;
    }
}

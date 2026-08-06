package com.portiq.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PriceService {

    private final PriceLookupService priceLookupService;

    public PriceService(PriceLookupService priceLookupService) {
        this.priceLookupService = priceLookupService;
    }

    public BigDecimal getCurrentPrice(String ticker, BigDecimal fallback) {
        if (ticker == null || ticker.isBlank()) return fallback;
        BigDecimal live = priceLookupService.fetchLivePrice(ticker);
        return live != null ? live : fallback;
    }
}

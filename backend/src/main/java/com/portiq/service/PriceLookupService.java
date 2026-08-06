package com.portiq.service;

import com.portiq.security.OutboundUrlGuard;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Isolated in its own bean (rather than a private method on PriceService) so the
 * {@code @Cacheable} annotation goes through the Spring proxy - calling a cached method from
 * within the same class bypasses the proxy and silently disables caching.
 *
 * <p>Uses the chart endpoint rather than the quote endpoint: Yahoo now returns 401 Unauthorized
 * on {@code v7/finance/quote} for unauthenticated callers, but {@code v8/finance/chart} - the
 * same endpoint PriceSeriesFetcher uses for history - still works and reports the live price
 * directly in its {@code meta.regularMarketPrice} field.
 */
@Service
public class PriceLookupService {

    private static final String CHART_API = "https://query1.finance.yahoo.com/v8/finance/chart/";

    private final RestTemplate restTemplate;
    private final OutboundUrlGuard urlGuard;

    public PriceLookupService(RestTemplate restTemplate, OutboundUrlGuard urlGuard) {
        this.restTemplate = restTemplate;
        this.urlGuard = urlGuard;
    }

    @Cacheable(value = "prices", key = "#ticker", unless = "#result == null")
    @SuppressWarnings("unchecked")
    public BigDecimal fetchLivePrice(String ticker) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set("Accept", "application/json");

            // Encoded, not concatenated - see MarketDataFetcher for why the ticker cannot be
            // allowed to leave its path segment.
            String url = CHART_API + UriUtils.encodePathSegment(ticker, StandardCharsets.UTF_8)
                    + "?range=1d&interval=5m";
            if (!urlGuard.isAllowed(url)) return null;

            ResponseEntity<Map> response = restTemplate.exchange(
                    URI.create(url),
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class);

            Map<String, Object> body = response.getBody();
            if (body == null) return null;

            Map<String, Object> chart = (Map<String, Object>) body.get("chart");
            if (chart == null) return null;

            List<Map<String, Object>> results = (List<Map<String, Object>>) chart.get("result");
            if (results == null || results.isEmpty()) return null;

            Map<String, Object> meta = (Map<String, Object>) results.get(0).get("meta");
            if (meta == null) return null;

            Object price = meta.get("regularMarketPrice");
            return price != null ? new BigDecimal(price.toString()) : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}

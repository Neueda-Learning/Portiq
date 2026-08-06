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
import java.util.TreeMap;

/**
 * Isolated in its own bean so {@code @Cacheable} is applied through the Spring proxy (see
 * PriceLookupService for why). Caches the bucketed price series per ticker + range string.
 */
@Service
public class PriceSeriesFetcher {

    private static final String CHART_API = "https://query1.finance.yahoo.com/v8/finance/chart/";

    private final RestTemplate restTemplate;
    private final OutboundUrlGuard urlGuard;

    public PriceSeriesFetcher(RestTemplate restTemplate, OutboundUrlGuard urlGuard) {
        this.restTemplate = restTemplate;
        this.urlGuard = urlGuard;
    }

    @Cacheable(value = "priceHistory", key = "#ticker + ':' + #range")
    @SuppressWarnings("unchecked")
    public Map<Long, BigDecimal> fetchSeries(String ticker, String range) {
        RangeConfig config = resolveRange(range);
        TreeMap<Long, BigDecimal> series = new TreeMap<>();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set("Accept", "application/json");

            // Encoded, not concatenated - see MarketDataFetcher. The range and interval come from
            // resolveRange's fixed switch, so only the ticker is caller-influenced.
            String url = CHART_API + UriUtils.encodePathSegment(ticker, StandardCharsets.UTF_8)
                    + "?range=" + config.range() + "&interval=" + config.interval();
            if (!urlGuard.isAllowed(url)) return series;

            ResponseEntity<Map> response = restTemplate.exchange(
                    URI.create(url), HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) return series;

            Map<String, Object> chart = (Map<String, Object>) body.get("chart");
            if (chart == null) return series;
            List<Map<String, Object>> results = (List<Map<String, Object>>) chart.get("result");
            if (results == null || results.isEmpty()) return series;

            Map<String, Object> result = results.get(0);
            List<Number> timestamps = (List<Number>) result.get("timestamp");
            Map<String, Object> indicators = (Map<String, Object>) result.get("indicators");
            if (timestamps == null || indicators == null) return series;

            List<Map<String, Object>> quoteList = (List<Map<String, Object>>) indicators.get("quote");
            if (quoteList == null || quoteList.isEmpty()) return series;
            List<Number> closes = (List<Number>) quoteList.get(0).get("close");
            if (closes == null) return series;

            for (int i = 0; i < timestamps.size() && i < closes.size(); i++) {
                Number ts = timestamps.get(i);
                Number close = closes.get(i);
                if (ts == null || close == null) continue;
                long bucket = (ts.longValue() / config.bucketSeconds()) * config.bucketSeconds();
                series.put(bucket, BigDecimal.valueOf(close.doubleValue()));
            }
        } catch (Exception ignored) {
            // Skip tickers the price feed cannot resolve rather than failing the whole chart.
        }
        return series;
    }

    private RangeConfig resolveRange(String range) {
        String normalized = range == null ? "1m" : range.toLowerCase();
        return switch (normalized) {
            case "1d" -> new RangeConfig("1d", "15m", 900L);
            case "1w" -> new RangeConfig("5d", "1d", 86400L);
            case "all" -> new RangeConfig("2y", "1wk", 604800L);
            default -> new RangeConfig("1mo", "1d", 86400L);
        };
    }

    private record RangeConfig(String range, String interval, long bucketSeconds) {}
}

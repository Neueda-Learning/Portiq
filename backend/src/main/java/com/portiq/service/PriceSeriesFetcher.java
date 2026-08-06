package com.portiq.service;

import com.portiq.dto.YahooChartResponse;
import com.portiq.security.OutboundUrlGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
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

    private static final Logger log = LoggerFactory.getLogger(PriceSeriesFetcher.class);

    private static final String CHART_API = "https://query1.finance.yahoo.com/v8/finance/chart/";

    private final RestTemplate restTemplate;
    private final OutboundUrlGuard urlGuard;

    public PriceSeriesFetcher(RestTemplate restTemplate, OutboundUrlGuard urlGuard) {
        this.restTemplate = restTemplate;
        this.urlGuard = urlGuard;
    }

    @Cacheable(value = "priceHistory", key = "#ticker + ':' + #range")
    public Map<Long, BigDecimal> fetchSeries(String ticker, String range) {
        RangeConfig config = resolveRange(range);
        TreeMap<Long, BigDecimal> series = new TreeMap<>();
        if (ticker == null || ticker.isBlank()) return series;

        // Encoded, not concatenated - see MarketDataFetcher. The range and interval come from
        // resolveRange's fixed switch, so only the ticker is caller-influenced.
        String url = CHART_API + UriUtils.encodePathSegment(ticker, StandardCharsets.UTF_8)
                + "?range=" + config.range() + "&interval=" + config.interval();
        if (!urlGuard.isAllowed(url)) return series;

        YahooChartResponse.Result result = fetchResult(url, ticker);
        if (result == null || result.timestamp() == null) return series;

        YahooChartResponse.Quote quote = YahooChartResponse.firstQuoteOf(result);
        if (quote == null || quote.close() == null) return series;

        List<Long> timestamps = result.timestamp();
        List<BigDecimal> closes = quote.close();
        for (int i = 0; i < timestamps.size() && i < closes.size(); i++) {
            Long ts = timestamps.get(i);
            BigDecimal close = closes.get(i);
            if (ts == null || close == null) continue;
            long bucket = (ts / config.bucketSeconds()) * config.bucketSeconds();
            series.put(bucket, close);
        }
        return series;
    }

    /** See MarketDataFetcher#fetchResult for why each failure mode is caught separately. */
    private YahooChartResponse.Result fetchResult(String url, String ticker) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set("Accept", "application/json");

            ResponseEntity<YahooChartResponse> response = restTemplate.exchange(
                    URI.create(url), HttpMethod.GET, new HttpEntity<>(headers), YahooChartResponse.class);

            YahooChartResponse body = response.getBody();
            return body == null ? null : body.firstResult();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                log.info("No price history for {} - the symbol may be delisted or renamed", ticker);
            } else {
                log.warn("Price history feed returned {} for {}", e.getStatusCode(), ticker);
            }
            return null;
        } catch (ResourceAccessException e) {
            log.warn("Price history feed unreachable for {}: {}", ticker, e.getMostSpecificCause().getMessage());
            return null;
        } catch (RestClientException e) {
            log.warn("Could not read the price history response for {}: {}", ticker, e.getMessage());
            return null;
        }
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

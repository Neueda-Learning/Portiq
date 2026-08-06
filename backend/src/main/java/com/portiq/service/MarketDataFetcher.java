package com.portiq.service;

import com.portiq.dto.DailySeries;
import com.portiq.security.OutboundUrlGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fetches a year of daily closes per ticker for the risk and recommendation engines.
 *
 * <p>Isolated in its own bean so {@code @Cacheable} is applied through the Spring proxy - the same
 * reason PriceLookupService and PriceSeriesFetcher are separate beans. Calling a cached method from
 * within the same class bypasses the proxy and silently disables caching.
 *
 * <p>Uses the same {@code v8/finance/chart} endpoint as the rest of the app (Yahoo returns 401 on
 * {@code v7/finance/quote} for unauthenticated callers), but asks for a full year at daily interval
 * so there are enough observations for volatility, beta and drawdown to mean anything.
 */
@Service
public class MarketDataFetcher {

    private static final Logger log = LoggerFactory.getLogger(MarketDataFetcher.class);

    private static final String CHART_API = "https://query1.finance.yahoo.com/v8/finance/chart/";

    private final RestTemplate restTemplate;
    private final OutboundUrlGuard urlGuard;

    public MarketDataFetcher(RestTemplate restTemplate, OutboundUrlGuard urlGuard) {
        this.restTemplate = restTemplate;
        this.urlGuard = urlGuard;
    }

    @Cacheable(value = "dailySeries", key = "#ticker", unless = "#result.isEmpty()")
    @SuppressWarnings("unchecked")
    public DailySeries fetchDailySeries(String ticker) {
        if (ticker == null || ticker.isBlank()) return DailySeries.empty(ticker);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set("Accept", "application/json");

            // The ticker is a path segment, so it is encoded rather than concatenated: a raw value
            // containing '?', '#' or '..' would otherwise rewrite the query or climb out of the
            // chart endpoint into a different Yahoo API. Encoding pins it to the one segment it is
            // meant to fill. The URI is built pre-encoded so RestTemplate does not encode it twice.
            String url = CHART_API + UriUtils.encodePathSegment(ticker, StandardCharsets.UTF_8)
                    + "?range=1y&interval=1d";
            if (!urlGuard.isAllowed(url)) {
                return DailySeries.empty(ticker);
            }

            ResponseEntity<Map> response = restTemplate.exchange(
                    URI.create(url), HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            Map<String, Object> body = response.getBody();
            if (body == null) return DailySeries.empty(ticker);

            Map<String, Object> chart = (Map<String, Object>) body.get("chart");
            if (chart == null) return DailySeries.empty(ticker);

            List<Map<String, Object>> results = (List<Map<String, Object>>) chart.get("result");
            if (results == null || results.isEmpty()) return DailySeries.empty(ticker);

            Map<String, Object> result = results.get(0);
            List<Number> rawTimestamps = (List<Number>) result.get("timestamp");
            Map<String, Object> indicators = (Map<String, Object>) result.get("indicators");
            if (rawTimestamps == null || indicators == null) return DailySeries.empty(ticker);

            List<Map<String, Object>> quoteList = (List<Map<String, Object>>) indicators.get("quote");
            if (quoteList == null || quoteList.isEmpty()) return DailySeries.empty(ticker);

            List<Number> rawCloses = (List<Number>) quoteList.get(0).get("close");
            List<Number> rawVolumes = (List<Number>) quoteList.get(0).get("volume");
            if (rawCloses == null) return DailySeries.empty(ticker);

            List<Long> timestamps = new ArrayList<>();
            List<BigDecimal> closes = new ArrayList<>();
            List<Long> volumes = new ArrayList<>();

            for (int i = 0; i < rawTimestamps.size() && i < rawCloses.size(); i++) {
                Number ts = rawTimestamps.get(i);
                Number close = rawCloses.get(i);
                // Yahoo emits nulls for market holidays; carrying them through would corrupt returns.
                if (ts == null || close == null) continue;

                timestamps.add(ts.longValue());
                closes.add(BigDecimal.valueOf(close.doubleValue()));

                Number volume = rawVolumes != null && i < rawVolumes.size() ? rawVolumes.get(i) : null;
                volumes.add(volume != null ? volume.longValue() : 0L);
            }

            BigDecimal currentPrice = extractCurrentPrice(result);
            if (currentPrice == null && !closes.isEmpty()) {
                currentPrice = closes.get(closes.size() - 1);
            }

            return new DailySeries(ticker, timestamps, closes, volumes, currentPrice);
        } catch (RestClientResponseException e) {
            // Yahoo answers 404 for a delisted or renamed symbol. That is a data problem the user
            // can act on, not a fault, so it is logged plainly rather than as an error.
            if (e.getStatusCode().value() == 404) {
                log.info("No price history for {} - the symbol may be delisted or renamed", ticker);
            } else {
                log.warn("Price feed returned {} for {}: {}", e.getStatusCode(), ticker, e.getMessage());
            }
            return DailySeries.empty(ticker);
        } catch (Exception e) {
            // Skip tickers the price feed cannot resolve rather than failing the whole report -
            // but say so, because a silently empty series looks identical to a genuinely flat one.
            log.warn("Could not fetch price history for {}: {}", ticker, e.toString());
            return DailySeries.empty(ticker);
        }
    }

    @SuppressWarnings("unchecked")
    private BigDecimal extractCurrentPrice(Map<String, Object> result) {
        Map<String, Object> meta = (Map<String, Object>) result.get("meta");
        if (meta == null) return null;
        Object price = meta.get("regularMarketPrice");
        return price != null ? new BigDecimal(price.toString()) : null;
    }
}

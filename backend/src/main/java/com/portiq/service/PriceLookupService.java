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

    private static final Logger log = LoggerFactory.getLogger(PriceLookupService.class);

    private static final String CHART_API = "https://query1.finance.yahoo.com/v8/finance/chart/";

    private final RestTemplate restTemplate;
    private final OutboundUrlGuard urlGuard;

    public PriceLookupService(RestTemplate restTemplate, OutboundUrlGuard urlGuard) {
        this.restTemplate = restTemplate;
        this.urlGuard = urlGuard;
    }

    @Cacheable(value = "prices", key = "#ticker", unless = "#result == null")
    public BigDecimal fetchLivePrice(String ticker) {
        if (ticker == null || ticker.isBlank()) return null;

        // Encoded, not concatenated - see MarketDataFetcher for why the ticker cannot be allowed
        // to leave its path segment.
        String url = CHART_API + UriUtils.encodePathSegment(ticker, StandardCharsets.UTF_8)
                + "?range=1d&interval=5m";
        if (!urlGuard.isAllowed(url)) return null;

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set("Accept", "application/json");

            ResponseEntity<YahooChartResponse> response = restTemplate.exchange(
                    URI.create(url), HttpMethod.GET, new HttpEntity<>(headers), YahooChartResponse.class);

            YahooChartResponse body = response.getBody();
            return body == null ? null : YahooChartResponse.livePriceOf(body.firstResult());
        } catch (RestClientResponseException e) {
            // A caller with no live price falls back to the purchase price, so this is a degraded
            // result rather than a failure - but 404 (unknown symbol) and 429 (we are being rate
            // limited by Yahoo) call for very different responses from whoever reads the log.
            if (e.getStatusCode().value() == 404) {
                log.info("No live price for {} - the symbol may be delisted or renamed", ticker);
            } else {
                log.warn("Price feed returned {} for {}", e.getStatusCode(), ticker);
            }
            return null;
        } catch (ResourceAccessException e) {
            log.warn("Price feed unreachable for {}: {}", ticker, e.getMostSpecificCause().getMessage());
            return null;
        } catch (RestClientException e) {
            log.warn("Could not read the live price response for {}: {}", ticker, e.getMessage());
            return null;
        }
    }
}

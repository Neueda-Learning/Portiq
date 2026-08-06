package com.portiq.service;

import com.portiq.dto.DailySeries;
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
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
    public DailySeries fetchDailySeries(String ticker) {
        if (ticker == null || ticker.isBlank()) return DailySeries.empty(ticker);

        // The ticker is a path segment, so it is encoded rather than concatenated: a raw value
        // containing '?', '#' or '..' would otherwise rewrite the query or climb out of the chart
        // endpoint into a different Yahoo API. The URI is built pre-encoded so RestTemplate does
        // not encode it a second time.
        String url = CHART_API + UriUtils.encodePathSegment(ticker, StandardCharsets.UTF_8)
                + "?range=1y&interval=1d";
        if (!urlGuard.isAllowed(url)) {
            return DailySeries.empty(ticker);
        }

        YahooChartResponse.Result result = fetchResult(url, ticker);
        if (result == null) {
            return DailySeries.empty(ticker);
        }

        YahooChartResponse.Quote quote = YahooChartResponse.firstQuoteOf(result);
        if (result.timestamp() == null || quote == null || quote.close() == null) {
            return DailySeries.empty(ticker);
        }

        return toSeries(ticker, result, quote);
    }

    /**
     * Performs the call and unwraps the payload, mapping each failure mode to what it actually
     * means for the caller.
     *
     * <p>These used to share one {@code catch (Exception)}, which reported a delisted symbol, a
     * timeout and a bug in this method identically. Every branch still degrades to an empty series
     * rather than failing the whole report - one unresolvable ticker should not cost the user their
     * other twenty - but the log now says which happened.
     */
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
            // Yahoo answers 404 for a delisted or renamed symbol. That is a data problem the user
            // can act on, not a fault, so it is logged plainly rather than as an error.
            if (e.getStatusCode().value() == 404) {
                log.info("No price history for {} - the symbol may be delisted or renamed", ticker);
            } else {
                log.warn("Price feed returned {} for {}", e.getStatusCode(), ticker);
            }
            return null;
        } catch (ResourceAccessException e) {
            // Connect or read timeout, DNS failure, connection reset - the feed is unreachable
            // rather than unhelpful, which is worth telling apart when several tickers fail at once.
            log.warn("Price feed unreachable for {}: {}", ticker, e.getMostSpecificCause().getMessage());
            return null;
        } catch (RestClientException e) {
            // Everything else the client can raise, most usefully a payload that no longer binds
            // to YahooChartResponse - which means the upstream contract has changed.
            log.warn("Could not read the price feed response for {}: {}", ticker, e.getMessage());
            return null;
        }
    }

    private DailySeries toSeries(String ticker, YahooChartResponse.Result result,
                                 YahooChartResponse.Quote quote) {
        List<Long> rawTimestamps = result.timestamp();
        List<BigDecimal> rawCloses = quote.close();
        List<Long> rawVolumes = quote.volume();

        List<Long> timestamps = new ArrayList<>();
        List<BigDecimal> closes = new ArrayList<>();
        List<Long> volumes = new ArrayList<>();

        for (int i = 0; i < rawTimestamps.size() && i < rawCloses.size(); i++) {
            Long ts = rawTimestamps.get(i);
            BigDecimal close = rawCloses.get(i);
            // Yahoo emits nulls for market holidays; carrying them through would corrupt returns.
            if (ts == null || close == null) continue;

            timestamps.add(ts);
            closes.add(close);

            Long volume = rawVolumes != null && i < rawVolumes.size() ? rawVolumes.get(i) : null;
            volumes.add(volume != null ? volume : 0L);
        }

        BigDecimal currentPrice = YahooChartResponse.livePriceOf(result);
        if (currentPrice == null && !closes.isEmpty()) {
            currentPrice = closes.get(closes.size() - 1);
        }

        return new DailySeries(ticker, timestamps, closes, volumes, currentPrice);
    }
}

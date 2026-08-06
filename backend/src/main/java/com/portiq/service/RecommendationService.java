package com.portiq.service;

import com.portiq.dto.DailySeries;
import com.portiq.dto.HoldingPerformance;
import com.portiq.dto.PerformanceSummary;
import com.portiq.dto.RecommendationResponse;
import com.portiq.dto.RiskMetrics;
import com.portiq.dto.StockRecommendation;
import com.portiq.dto.StockRisk;
import com.portiq.model.DataQuality;
import com.portiq.model.HoldingType;
import com.portiq.model.RecommendationAction;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Produces a BUY / ACCUMULATE / HOLD / TRIM / SELL call for every held stock, plus BUY ideas drawn
 * from a configurable universe of tickers the investor does not already own.
 *
 * <p>The decision is entirely rule-based and reproducible: signals are scored into a single
 * opportunity number between -100 and +100, and the action falls out of which band that lands in.
 * The LLM, when one is configured, only rewrites the reason text - it never picks the action. This
 * follows the same split SmartFileImportService settled on, where the model normalises language
 * and Java owns the arithmetic.
 */
@Service
@Transactional(readOnly = true)
public class RecommendationService {

    // Signal contributions to the opportunity score.
    private static final double MOMENTUM_CAP = 30;
    private static final double TREND_BONUS = 20;
    private static final double RSI_BONUS = 15;
    private static final double VALUE_BONUS = 15;
    private static final double STRETCHED_PENALTY = 10;
    private static final double CONCENTRATION_PENALTY = 20;
    private static final double BROKEN_POSITION_PENALTY = 10;
    private static final double WINNER_BONUS = 5;

    private static final double RSI_OVERSOLD = 30;
    private static final double RSI_OVERBOUGHT = 70;
    private static final double NEAR_52W_LOW = 20;
    private static final double NEAR_52W_HIGH = 85;

    /** Above this risk score the signals get discounted - conviction should not survive chaos. */
    private static final double HIGH_RISK_THRESHOLD = 70;
    private static final double HIGH_RISK_DISCOUNT = 0.7;

    // Score bands that map onto actions.
    private static final double STRONG_BUY_BAND = 40;
    private static final double MILD_BUY_BAND = 15;
    private static final double MILD_SELL_BAND = -15;
    private static final double STRONG_SELL_BAND = -40;

    /** A position bigger than this is trimmed regardless of how good the stock looks. */
    private static final double OVERSIZED_WEIGHT_PERCENT = 25;
    private static final double MAX_SUGGESTED_WEIGHT = 25;
    private static final double MAX_IDEA_WEIGHT = 10;

    /** How much of a position a TRIM leaves standing. */
    private static final double TRIM_FACTOR = 0.7;

    private static final int FETCH_THREADS = 8;

    private final HoldingService holdingService;
    private final MarketDataFetcher marketDataFetcher;
    private final MetricsCalculator metrics;
    private final RiskAnalysisService riskAnalysisService;
    private final RecommendationNarrator narrator;

    /**
     * A cold cache means one HTTP call per universe ticker. These are blocking IO, so they get a
     * dedicated small pool rather than parallelStream(), whose shared ForkJoinPool is sized for
     * CPU work and would be starved by twenty concurrent socket reads.
     */
    private final ExecutorService fetchPool = Executors.newFixedThreadPool(FETCH_THREADS, runnable -> {
        Thread thread = new Thread(runnable, "reco-fetch");
        thread.setDaemon(true);
        return thread;
    });

    @Value("${app.recommendations.universe:}")
    private String universeProperty;

    @Value("${app.recommendations.max-ideas:5}")
    private int maxIdeas;

    public RecommendationService(HoldingService holdingService,
                                 MarketDataFetcher marketDataFetcher,
                                 MetricsCalculator metrics,
                                 RiskAnalysisService riskAnalysisService,
                                 RecommendationNarrator narrator) {
        this.holdingService = holdingService;
        this.marketDataFetcher = marketDataFetcher;
        this.metrics = metrics;
        this.riskAnalysisService = riskAnalysisService;
        this.narrator = narrator;
    }

    @PreDestroy
    void shutdownPool() {
        fetchPool.shutdown();
    }

    public RecommendationResponse getRecommendations(boolean includeIdeas, boolean narrate) {
        PerformanceSummary summary = holdingService.getAggregatePerformance();
        List<HoldingPerformance> holdings = summary.getHoldings() == null ? List.of() : summary.getHoldings();
        BigDecimal totalValue = summary.getTotalCurrentValue();

        DailySeries benchmark = riskAnalysisService.benchmarkSeries();

        // Cash has no price series and no meaningful buy/sell call, so it is left out entirely.
        List<HoldingPerformance> priceable = holdings.stream()
                .filter(h -> h.getType() != HoldingType.CASH)
                .toList();

        Map<String, DailySeries> heldSeries = fetchSeries(priceable.stream()
                .map(HoldingPerformance::getTicker)
                .toList());

        int positionCount = Math.max(priceable.size(), 1);

        List<StockRecommendation> heldRecommendations = priceable.stream()
                .map(holding -> evaluateHeld(holding, totalValue, positionCount,
                        heldSeries.getOrDefault(holding.getTicker(), DailySeries.empty(holding.getTicker())),
                        benchmark))
                .sorted(Comparator.comparing(StockRecommendation::getOpportunityScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        Set<String> heldTickers = holdings.stream()
                .map(h -> h.getTicker() == null ? "" : h.getTicker().toUpperCase())
                .collect(Collectors.toCollection(HashSet::new));

        List<StockRecommendation> ideas = includeIdeas ? buildIdeas(heldTickers, benchmark) : List.of();

        RecommendationResponse response = new RecommendationResponse();
        response.setHoldings(heldRecommendations);
        response.setIdeas(ideas);
        response.setGeneratedAt(Instant.now());
        response.setDisclaimer(RiskAnalysisService.DISCLAIMER);

        if (narrate) {
            narrator.narrate(response);
        }
        return response;
    }

    /** Evaluates any single ticker, using portfolio context when the ticker happens to be held. */
    public StockRecommendation recommendTicker(String ticker) {
        String normalised = TickerValidator.normalise(ticker);

        PerformanceSummary summary = holdingService.getAggregatePerformance();
        List<HoldingPerformance> holdings = summary.getHoldings() == null ? List.of() : summary.getHoldings();

        HoldingPerformance held = holdings.stream()
                .filter(h -> h.getTicker() != null && h.getTicker().equalsIgnoreCase(normalised))
                .findFirst()
                .orElse(null);

        DailySeries series = marketDataFetcher.fetchDailySeries(normalised);
        DailySeries benchmark = riskAnalysisService.benchmarkSeries();

        if (held != null) {
            return evaluateHeld(held, summary.getTotalCurrentValue(), Math.max(holdings.size(), 1), series, benchmark);
        }
        return evaluateIdea(normalised, normalised, series, benchmark);
    }

    private List<StockRecommendation> buildIdeas(Set<String> heldTickers, DailySeries benchmark) {
        List<String> candidates = universe().stream()
                .filter(ticker -> !heldTickers.contains(ticker))
                .toList();

        if (candidates.isEmpty()) return List.of();

        Map<String, DailySeries> seriesByTicker = fetchSeries(candidates);

        return candidates.stream()
                .map(ticker -> evaluateIdea(ticker, ticker,
                        seriesByTicker.getOrDefault(ticker, DailySeries.empty(ticker)), benchmark))
                // Only surface ideas that actually argue for themselves - an "AVOID" list of stocks
                // the investor was never going to buy is noise, not a recommendation.
                .filter(rec -> rec.getAction() == RecommendationAction.BUY)
                .sorted(Comparator.comparing(StockRecommendation::getOpportunityScore).reversed())
                .limit(Math.max(maxIdeas, 0))
                .toList();
    }

    private StockRecommendation evaluateHeld(HoldingPerformance holding, BigDecimal totalValue,
                                             int positionCount, DailySeries series, DailySeries benchmark) {

        double weightPercent = percentOf(holding.getCurrentValue(), totalValue);

        StockRisk risk = riskAnalysisService.buildStockRisk(holding.getTicker(), holding.getName(),
                holding.getType(), holding.getCurrentPrice(), weightPercent, series, benchmark);

        StockRecommendation rec = baseRecommendation(holding.getTicker(), holding.getName(),
                holding.getCurrentPrice(), series, risk);
        rec.setHeld(true);
        rec.setQuantity(holding.getQuantity());
        rec.setCurrentValue(holding.getCurrentValue());
        rec.setGainLossPercent(holding.getGainLossPercent());
        rec.setWeightPercent(round(weightPercent));

        List<String> signals = new ArrayList<>();
        double score = scoreSignals(rec, risk, signals);

        // Position-level adjustments that only make sense for something already owned.
        double gainLoss = holding.getGainLossPercent() == null ? 0 : holding.getGainLossPercent().doubleValue();
        if (gainLoss < -20 && "DOWNTREND".equals(rec.getTrend())) {
            score -= BROKEN_POSITION_PENALTY;
            signals.add("Down " + holding.getGainLossPercent() + "% on your cost and still in a downtrend");
        } else if (gainLoss > 20 && "UPTREND".equals(rec.getTrend())) {
            score += WINNER_BONUS;
            signals.add("Up " + holding.getGainLossPercent() + "% on your cost with the trend intact");
        }

        boolean oversized = weightPercent > OVERSIZED_WEIGHT_PERCENT;
        if (oversized) {
            score -= CONCENTRATION_PENALTY;
            signals.add("Position is " + round(weightPercent) + "% of the portfolio, above the "
                    + (int) OVERSIZED_WEIGHT_PERCENT + "% concentration comfort line");
        }

        score = metrics.clamp(score, -100, 100);
        rec.setOpportunityScore(round(score));
        rec.setSignals(signals);

        RecommendationAction action = heldAction(score, oversized);
        rec.setAction(action);
        rec.setConfidence(confidence(score, risk.getDataQuality()));
        rec.setSuggestedWeightPercent(suggestedWeight(action, weightPercent, positionCount));
        rec.setReason(buildReason(rec, signals));

        return rec;
    }

    private StockRecommendation evaluateIdea(String ticker, String name, DailySeries series, DailySeries benchmark) {
        StockRisk risk = riskAnalysisService.buildStockRisk(ticker, name, HoldingType.STOCK,
                series.currentPrice(), 0, series, benchmark);

        StockRecommendation rec = baseRecommendation(ticker, name, series.currentPrice(), series, risk);
        rec.setHeld(false);

        List<String> signals = new ArrayList<>();
        double score = metrics.clamp(scoreSignals(rec, risk, signals), -100, 100);

        rec.setOpportunityScore(round(score));
        rec.setSignals(signals);

        RecommendationAction action = ideaAction(score, risk.getDataQuality());
        rec.setAction(action);
        rec.setConfidence(confidence(score, risk.getDataQuality()));
        rec.setSuggestedWeightPercent(action == RecommendationAction.BUY
                ? round(MAX_IDEA_WEIGHT)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        rec.setReason(buildReason(rec, signals));

        return rec;
    }

    /** Copies the shared metric fields across from the risk analysis so both features agree. */
    private StockRecommendation baseRecommendation(String ticker, String name, BigDecimal currentPrice,
                                                   DailySeries series, StockRisk risk) {
        RiskMetrics m = risk.getMetrics();

        StockRecommendation rec = new StockRecommendation();
        rec.setTicker(ticker);
        rec.setName(name);
        rec.setCurrentPrice(currentPrice != null ? currentPrice : series.currentPrice());
        rec.setRiskScore(risk.getRiskScore());
        rec.setRiskLevel(risk.getRiskLevel());
        rec.setDataQuality(risk.getDataQuality());
        rec.setMomentum30dPercent(m.getMomentum30dPercent());
        rec.setMomentum90dPercent(m.getMomentum90dPercent());
        rec.setRsi14(m.getRsi14());
        rec.setPricePositionPercent(m.getPricePositionPercent());
        rec.setAnnualisedVolatilityPercent(m.getAnnualisedVolatilityPercent());
        rec.setBeta(m.getBeta());

        // Trend is measured against the series' own latest close, not the displayed current price.
        // The two normally agree, but PriceService falls back to the purchase price when a live
        // quote fails - comparing that to a 200-day average would report a trend that never
        // happened. The 52-week position in RiskAnalysisService anchors on the series for the
        // same reason.
        BigDecimal seriesPrice = series.currentPrice() != null ? series.currentPrice() : series.latestClose();
        rec.setTrend(trend(seriesPrice, m.getSma50(), m.getSma200()));
        return rec;
    }

    /**
     * The shared signal scoring, common to held positions and new ideas. Appends a human-readable
     * line to {@code signals} for every rule that fires, so the UI can show the reasoning as chips
     * without re-deriving it.
     */
    private double scoreSignals(StockRecommendation rec, StockRisk risk, List<String> signals) {
        if (risk.getDataQuality() == DataQuality.UNAVAILABLE) {
            signals.add("No price history available - cannot form a view");
            return 0;
        }

        double score = 0;

        BigDecimal momentum = rec.getMomentum90dPercent();
        if (momentum != null) {
            double contribution = metrics.clamp(momentum.doubleValue(), -MOMENTUM_CAP, MOMENTUM_CAP);
            score += contribution;
            if (momentum.doubleValue() >= 10) {
                signals.add("Up " + momentum + "% over the last 90 days");
            } else if (momentum.doubleValue() <= -10) {
                signals.add("Down " + momentum + "% over the last 90 days");
            }
        }

        String trend = rec.getTrend();
        if ("UPTREND".equals(trend)) {
            score += TREND_BONUS;
            signals.add("Trading above both its 50- and 200-day averages");
        } else if ("DOWNTREND".equals(trend)) {
            score -= TREND_BONUS;
            signals.add("Trading below its 200-day average");
        }

        BigDecimal rsi = rec.getRsi14();
        if (rsi != null) {
            if (rsi.doubleValue() < RSI_OVERSOLD) {
                score += RSI_BONUS;
                signals.add("RSI of " + rsi + " is in oversold territory");
            } else if (rsi.doubleValue() > RSI_OVERBOUGHT) {
                score -= RSI_BONUS;
                signals.add("RSI of " + rsi + " is in overbought territory");
            }
        }

        BigDecimal position = rec.getPricePositionPercent();
        if (position != null) {
            if (position.doubleValue() < NEAR_52W_LOW) {
                score += VALUE_BONUS;
                signals.add("Near the bottom of its 52-week range");
            } else if (position.doubleValue() > NEAR_52W_HIGH) {
                score -= STRETCHED_PENALTY;
                signals.add("Close to its 52-week high, leaving little room for error");
            }
        }

        // Volatile names get their conviction discounted rather than inverted - a high-risk stock
        // can still be the right buy, it just deserves less weight in the decision.
        if (risk.getRiskScore() != null && risk.getRiskScore().doubleValue() > HIGH_RISK_THRESHOLD) {
            score *= HIGH_RISK_DISCOUNT;
            signals.add("Risk score of " + risk.getRiskScore() + "/100 tempers the case either way");
        }

        if (risk.getDataQuality() == DataQuality.LIMITED) {
            signals.add("Limited price history, so treat this with caution");
        }

        return score;
    }

    private RecommendationAction heldAction(double score, boolean oversized) {
        // An oversized position gets trimmed unless the case for it is genuinely strong.
        if (oversized && score < STRONG_BUY_BAND / 2) return RecommendationAction.TRIM;

        if (score >= STRONG_BUY_BAND) return RecommendationAction.ACCUMULATE;
        if (score >= MILD_SELL_BAND) return RecommendationAction.HOLD;
        if (score >= STRONG_SELL_BAND) return RecommendationAction.TRIM;
        return RecommendationAction.SELL;
    }

    private RecommendationAction ideaAction(double score, DataQuality dataQuality) {
        if (dataQuality == DataQuality.UNAVAILABLE) return RecommendationAction.AVOID;
        if (score >= MILD_BUY_BAND) return RecommendationAction.BUY;
        return RecommendationAction.AVOID;
    }

    private BigDecimal confidence(double score, DataQuality dataQuality) {
        if (dataQuality == DataQuality.UNAVAILABLE) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        double confidence = metrics.clamp(Math.abs(score), 0, 100);
        if (dataQuality == DataQuality.LIMITED) confidence *= 0.6;
        return round(confidence);
    }

    /**
     * A target weight to move toward, anchored on an equal-weight baseline so the suggestion scales
     * with how many positions the investor actually runs.
     */
    private BigDecimal suggestedWeight(RecommendationAction action, double currentWeight, int positionCount) {
        double baseline = 100.0 / Math.max(positionCount, 5);

        double suggested = switch (action) {
            case SELL, AVOID -> 0;
            // A trim has to actually reduce the position. Capping at the equal-weight baseline
            // alone would leave an already-small holding untouched, which reads as "reduce this to
            // exactly what you already own".
            case TRIM -> Math.min(currentWeight * TRIM_FACTOR, baseline);
            case HOLD -> currentWeight;
            case ACCUMULATE, BUY -> Math.min(Math.max(currentWeight * 1.3, baseline), MAX_SUGGESTED_WEIGHT);
        };
        return round(metrics.clamp(suggested, 0, 100));
    }

    private String trend(BigDecimal price, BigDecimal sma50, BigDecimal sma200) {
        if (price == null) return "SIDEWAYS";

        double current = price.doubleValue();
        if (sma50 != null && sma200 != null) {
            if (current > sma50.doubleValue() && sma50.doubleValue() > sma200.doubleValue()) return "UPTREND";
            if (current < sma200.doubleValue()) return "DOWNTREND";
            return "SIDEWAYS";
        }
        // Under 200 trading days the long average does not exist yet; fall back to the 50-day.
        if (sma50 != null) {
            return current > sma50.doubleValue() ? "UPTREND" : "DOWNTREND";
        }
        return "SIDEWAYS";
    }

    /** The fallback prose, used verbatim whenever no LLM is configured. */
    private String buildReason(StockRecommendation rec, List<String> signals) {
        StringBuilder sb = new StringBuilder();
        sb.append(actionSentence(rec));

        // With no data the lone signal only restates the sentence above, so stop there.
        if (rec.getDataQuality() == DataQuality.UNAVAILABLE) {
            return sb.toString();
        }

        if (!signals.isEmpty()) {
            sb.append(" ").append(String.join(". ", signals)).append(".");
        }
        return sb.toString();
    }

    private String actionSentence(StockRecommendation rec) {
        String label = rec.getName() != null && !rec.getName().equals(rec.getTicker())
                ? rec.getName() + " (" + rec.getTicker() + ")"
                : rec.getTicker();

        // Without price history there is no view to state. Leading with "nothing argues for
        // changing your position" would read as a considered HOLD rather than an absence of data.
        if (rec.getDataQuality() == DataQuality.UNAVAILABLE) {
            return "No price data is available for " + label + ", so no view can be formed on it.";
        }

        return switch (rec.getAction()) {
            case BUY -> label + " screens as a buy candidate at " + rec.getCurrentPrice() + ".";
            case ACCUMULATE -> "The case for adding to " + label + " at " + rec.getCurrentPrice() + " is intact.";
            case HOLD -> "Nothing in the numbers argues for changing your " + label + " position.";
            case TRIM -> "Consider reducing " + label + " at " + rec.getCurrentPrice() + ".";
            case SELL -> "The signals argue for exiting " + label + " at " + rec.getCurrentPrice() + ".";
            case AVOID -> label + " does not screen well enough to open a position.";
        };
    }

    /** Fans the price fetches out across the pool; a failure yields an empty series, never an error. */
    private Map<String, DailySeries> fetchSeries(List<String> tickers) {
        Map<String, DailySeries> result = new LinkedHashMap<>();
        if (tickers.isEmpty()) return result;

        List<CompletableFuture<DailySeries>> futures = tickers.stream()
                .map(ticker -> CompletableFuture.supplyAsync(
                        () -> marketDataFetcher.fetchDailySeries(ticker), fetchPool)
                        .exceptionally(error -> DailySeries.empty(ticker)))
                .toList();

        for (int i = 0; i < tickers.size(); i++) {
            result.put(tickers.get(i), futures.get(i).join());
        }
        return result;
    }

    private List<String> universe() {
        if (universeProperty == null || universeProperty.isBlank()) return List.of();

        return Arrays.stream(universeProperty.split(","))
                .map(String::trim)
                .filter(ticker -> !ticker.isEmpty())
                .map(String::toUpperCase)
                .distinct()
                .toList();
    }

    private double percentOf(BigDecimal part, BigDecimal total) {
        if (part == null || total == null || total.compareTo(BigDecimal.ZERO) == 0) return 0;
        return part.divide(total, 6, RoundingMode.HALF_UP).doubleValue() * 100;
    }

    private BigDecimal round(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return null;
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}

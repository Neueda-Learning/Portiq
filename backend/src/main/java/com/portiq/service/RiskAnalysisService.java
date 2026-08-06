package com.portiq.service;

import com.portiq.dto.DailySeries;
import com.portiq.dto.HoldingPerformance;
import com.portiq.dto.PerformanceSummary;
import com.portiq.dto.PortfolioRiskReport;
import com.portiq.dto.RiskMetrics;
import com.portiq.dto.StockRisk;
import com.portiq.model.DataQuality;
import com.portiq.model.HoldingType;
import com.portiq.model.RiskLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Scores how risky each holding is, and the portfolio as a whole, from a year of daily closes.
 *
 * <p>Everything here is deterministic - no LLM is involved. The score is a weighted blend of five
 * components, each linearly mapped onto 0-100 between a floor (unremarkable) and a ceiling (as bad
 * as it realistically gets) so the components stay comparable. Weights and bounds are named
 * constants rather than magic numbers so the scoring can be argued with.
 */
@Service
@Transactional(readOnly = true)
public class RiskAnalysisService {

    public static final String DISCLAIMER =
            "Educational analysis generated from historical price data. Not investment advice.";

    // Component weights - must sum to 100. When a metric cannot be computed from a short series,
    // its weight is dropped and the survivors are renormalised, so a partial series still yields an
    // honest score rather than one silently deflated by missing terms.
    private static final double WEIGHT_VOLATILITY = 35;
    private static final double WEIGHT_BETA = 20;
    private static final double WEIGHT_DRAWDOWN = 25;
    private static final double WEIGHT_CONCENTRATION = 10;
    private static final double WEIGHT_VAR = 10;

    // Floor -> 0, ceiling -> 100 for each component.
    private static final double VOLATILITY_FLOOR = 10;
    private static final double VOLATILITY_CEILING = 60;
    private static final double BETA_FLOOR = 0.5;
    private static final double BETA_CEILING = 2.0;
    private static final double DRAWDOWN_FLOOR = 10;
    private static final double DRAWDOWN_CEILING = 60;
    private static final double CONCENTRATION_FLOOR = 5;
    private static final double CONCENTRATION_CEILING = 40;
    private static final double VAR_FLOOR = 1;
    private static final double VAR_CEILING = 6;

    // Thresholds that trigger portfolio warnings.
    private static final double CONCENTRATION_WARNING_PERCENT = 25;
    private static final int THIN_PORTFOLIO_HOLDINGS = 5;
    private static final double HIGH_BETA_WARNING = 1.3;

    private final HoldingService holdingService;
    private final MarketDataFetcher marketDataFetcher;
    private final MetricsCalculator metrics;

    @Value("${app.analytics.benchmark-ticker:^NSEI}")
    private String benchmarkTicker;

    @Value("${app.analytics.risk-free-rate:6.5}")
    private double riskFreeRatePercent;

    public RiskAnalysisService(HoldingService holdingService,
                               MarketDataFetcher marketDataFetcher,
                               MetricsCalculator metrics) {
        this.holdingService = holdingService;
        this.marketDataFetcher = marketDataFetcher;
        this.metrics = metrics;
    }

    public PortfolioRiskReport getPortfolioRisk() {
        PerformanceSummary summary = holdingService.getAggregatePerformance();
        List<HoldingPerformance> holdings = summary.getHoldings() == null ? List.of() : summary.getHoldings();

        PortfolioRiskReport report = new PortfolioRiskReport();
        report.setBenchmark(benchmarkTicker);
        report.setGeneratedAt(Instant.now());
        report.setDisclaimer(DISCLAIMER);
        report.setHoldingsCount(holdings.size());

        if (holdings.isEmpty()) {
            return emptyReport(report);
        }

        BigDecimal totalValue = summary.getTotalCurrentValue();
        DailySeries benchmark = benchmarkSeries();

        List<StockRisk> risks = new ArrayList<>();
        List<DailySeries> usableSeries = new ArrayList<>();
        List<Double> usableWeights = new ArrayList<>();
        double[] weightFractions = new double[holdings.size()];

        for (int i = 0; i < holdings.size(); i++) {
            HoldingPerformance holding = holdings.get(i);
            double weightPercent = percentOf(holding.getCurrentValue(), totalValue);
            weightFractions[i] = weightPercent / 100;

            DailySeries series = holding.getType() == HoldingType.CASH
                    ? DailySeries.empty(holding.getTicker())
                    : marketDataFetcher.fetchDailySeries(holding.getTicker());

            StockRisk risk = buildStockRisk(holding.getTicker(), holding.getName(), holding.getType(),
                    holding.getCurrentPrice(), weightPercent, series, benchmark);
            risks.add(risk);

            double[] returns = metrics.dailyReturns(series.closesAsDoubles());
            if (returns.length >= MetricsCalculator.MIN_OBSERVATIONS) {
                usableSeries.add(series);
                usableWeights.add(weightFractions[i]);
            }
        }

        report.setHoldings(risks);
        applyPortfolioAggregates(report, risks, weightFractions, usableSeries, usableWeights);
        report.setAllocationByType(allocationByType(holdings, totalValue));
        report.setWarnings(buildWarnings(report, risks));

        return report;
    }

    /** Analyses any ticker - if it happens to be held, its portfolio weight is folded in. */
    public StockRisk analyseTicker(String ticker) {
        String normalised = TickerValidator.normalise(ticker);

        PerformanceSummary summary = holdingService.getAggregatePerformance();
        HoldingPerformance held = summary.getHoldings() == null ? null : summary.getHoldings().stream()
                .filter(h -> h.getTicker() != null && h.getTicker().equalsIgnoreCase(normalised))
                .findFirst()
                .orElse(null);

        DailySeries series = marketDataFetcher.fetchDailySeries(normalised);

        String name = held != null ? held.getName() : normalised;
        HoldingType type = held != null ? held.getType() : HoldingType.STOCK;
        BigDecimal price = held != null ? held.getCurrentPrice() : series.currentPrice();
        double weightPercent = held != null ? percentOf(held.getCurrentValue(), summary.getTotalCurrentValue()) : 0;

        return buildStockRisk(normalised, name, type, price, weightPercent, series, benchmarkSeries());
    }

    /**
     * Scores one stock. Cash short-circuits to zero - it has no market series and pretending
     * otherwise would report it as UNAVAILABLE, which reads like a failure rather than the truth.
     */
    StockRisk buildStockRisk(String ticker, String name, HoldingType type, BigDecimal currentPrice,
                             double weightPercent, DailySeries series, DailySeries benchmark) {

        StockRisk risk = new StockRisk();
        risk.setTicker(ticker);
        risk.setName(name);
        risk.setWeightPercent(round(weightPercent));
        risk.setCurrentPrice(currentPrice != null ? currentPrice : series.currentPrice());

        if (type == HoldingType.CASH) {
            risk.setRiskScore(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            risk.setRiskLevel(RiskLevel.LOW);
            risk.setDataQuality(DataQuality.SUFFICIENT);
            risk.setDrivers(List.of("Cash carries no market risk"));
            risk.setMetrics(new RiskMetrics());
            return risk;
        }

        double[] closes = series.closesAsDoubles();
        double[] returns = metrics.dailyReturns(closes);
        boolean sufficient = returns.length >= MetricsCalculator.MIN_OBSERVATIONS;

        RiskMetrics m = buildMetrics(series, closes, returns, benchmark);
        risk.setMetrics(m);

        if (series.isEmpty()) {
            risk.setDataQuality(DataQuality.UNAVAILABLE);
            risk.setRiskScore(null);
            risk.setRiskLevel(null);
            risk.setDrivers(List.of("No price history available for " + ticker));
            return risk;
        }
        risk.setDataQuality(sufficient ? DataQuality.SUFFICIENT : DataQuality.LIMITED);

        double score = compositeScore(m, weightPercent);
        risk.setRiskScore(round(score));
        risk.setRiskLevel(RiskLevel.fromScore(score));
        risk.setDrivers(buildDrivers(m, weightPercent, sufficient));

        return risk;
    }

    private RiskMetrics buildMetrics(DailySeries series, double[] closes, double[] returns, DailySeries benchmark) {
        RiskMetrics m = new RiskMetrics();
        m.setDataPoints(closes.length);

        m.setAnnualisedVolatilityPercent(round(metrics.annualisedVolatilityPercent(returns)));
        // Beta joins the two series on their shared trading days rather than trusting index
        // position - see MetricsCalculator.alignSeriesOnCommonDays for why that is not optional.
        m.setBeta(round(metrics.betaFromSeries(
                series.timestampsAsLongs(), closes,
                benchmark.timestampsAsLongs(), benchmark.closesAsDoubles())));
        m.setMaxDrawdownPercent(round(metrics.maxDrawdownPercent(closes)));
        m.setSharpeRatio(round(metrics.sharpeRatio(returns, riskFreeRatePercent)));
        m.setValueAtRisk95Percent(round(metrics.historicalVar95Percent(returns)));
        m.setRsi14(round(metrics.rsi14(closes)));
        m.setMomentum30dPercent(round(metrics.momentumPercent(closes, 30)));
        m.setMomentum90dPercent(round(metrics.momentumPercent(closes, 90)));
        m.setSma50(round(metrics.sma(closes, 50)));
        m.setSma200(round(metrics.sma(closes, 200)));

        BigDecimal high = series.fiftyTwoWeekHigh();
        BigDecimal low = series.fiftyTwoWeekLow();
        m.setFiftyTwoWeekHigh(high);
        m.setFiftyTwoWeekLow(low);

        BigDecimal current = series.currentPrice() != null ? series.currentPrice() : series.latestClose();
        if (current != null && high != null && low != null) {
            m.setPricePositionPercent(round(metrics.pricePositionPercent(
                    current.doubleValue(), low.doubleValue(), high.doubleValue())));
        }

        return m;
    }

    /**
     * Weighted blend of the five components, renormalised over whichever ones could be computed.
     */
    private double compositeScore(RiskMetrics m, double weightPercent) {
        double weightedSum = 0;
        double appliedWeight = 0;

        double volatility = component(m.getAnnualisedVolatilityPercent(), VOLATILITY_FLOOR, VOLATILITY_CEILING);
        if (!Double.isNaN(volatility)) {
            weightedSum += volatility * WEIGHT_VOLATILITY;
            appliedWeight += WEIGHT_VOLATILITY;
        }

        // A deeply negative beta is just as much market exposure as a positive one, so score |beta|.
        double betaValue = m.getBeta() == null ? Double.NaN : Math.abs(m.getBeta().doubleValue());
        double beta = metrics.scaleToScore(betaValue, BETA_FLOOR, BETA_CEILING);
        if (!Double.isNaN(beta)) {
            weightedSum += beta * WEIGHT_BETA;
            appliedWeight += WEIGHT_BETA;
        }

        double drawdown = component(m.getMaxDrawdownPercent(), DRAWDOWN_FLOOR, DRAWDOWN_CEILING);
        if (!Double.isNaN(drawdown)) {
            weightedSum += drawdown * WEIGHT_DRAWDOWN;
            appliedWeight += WEIGHT_DRAWDOWN;
        }

        double var = component(m.getValueAtRisk95Percent(), VAR_FLOOR, VAR_CEILING);
        if (!Double.isNaN(var)) {
            weightedSum += var * WEIGHT_VAR;
            appliedWeight += WEIGHT_VAR;
        }

        // Position size only counts when the stock is actually held.
        if (weightPercent > 0) {
            double concentration = metrics.scaleToScore(weightPercent, CONCENTRATION_FLOOR, CONCENTRATION_CEILING);
            if (!Double.isNaN(concentration)) {
                weightedSum += concentration * WEIGHT_CONCENTRATION;
                appliedWeight += WEIGHT_CONCENTRATION;
            }
        }

        if (appliedWeight == 0) return 0;
        return metrics.clamp(weightedSum / appliedWeight, 0, 100);
    }

    private double component(BigDecimal value, double floor, double ceiling) {
        if (value == null) return Double.NaN;
        return metrics.scaleToScore(value.doubleValue(), floor, ceiling);
    }

    /** Plain-language explanations for whichever components pushed the score up. */
    private List<String> buildDrivers(RiskMetrics m, double weightPercent, boolean sufficient) {
        List<String> drivers = new ArrayList<>();

        if (!sufficient) {
            drivers.add("Only " + m.getDataPoints() + " days of price history - the score uses what could be measured");
        }

        BigDecimal volatility = m.getAnnualisedVolatilityPercent();
        if (volatility != null) {
            if (volatility.doubleValue() >= 40) {
                drivers.add("Annualised volatility of " + volatility + "% is well above the ~20% large-cap norm");
            } else if (volatility.doubleValue() <= 18) {
                drivers.add("Annualised volatility of " + volatility + "% is calm for an equity");
            }
        }

        BigDecimal beta = m.getBeta();
        if (beta != null) {
            if (beta.doubleValue() >= 1.3) {
                drivers.add("Beta of " + beta + " means it amplifies market moves");
            } else if (beta.doubleValue() <= 0.8) {
                drivers.add("Beta of " + beta + " means it moves less than the market");
            }
        }

        BigDecimal drawdown = m.getMaxDrawdownPercent();
        if (drawdown != null && drawdown.doubleValue() >= 30) {
            drivers.add("Fell " + drawdown + "% peak-to-trough over the last year");
        }

        BigDecimal var = m.getValueAtRisk95Percent();
        if (var != null && var.doubleValue() >= 3) {
            drivers.add("On the worst 1 day in 20 it lost about " + var + "%");
        }

        if (weightPercent >= CONCENTRATION_WARNING_PERCENT) {
            drivers.add("This single position is " + round(weightPercent) + "% of the portfolio");
        }

        if (drivers.isEmpty()) {
            drivers.add("No individual metric stands out as unusual");
        }
        return drivers;
    }

    private void applyPortfolioAggregates(PortfolioRiskReport report, List<StockRisk> risks,
                                          double[] weightFractions, List<DailySeries> usableSeries,
                                          List<Double> usableWeights) {

        report.setOverallRiskScore(round(valueWeightedScore(risks)));
        if (report.getOverallRiskScore() != null) {
            report.setRiskLevel(RiskLevel.fromScore(report.getOverallRiskScore().doubleValue()));
        }

        report.setPortfolioBeta(round(weightedMetric(risks, r -> r.getMetrics().getBeta())));

        // Renormalise over the holdings that actually had usable history, otherwise a single
        // unresolvable ticker would drag the whole portfolio's volatility toward zero.
        if (!usableSeries.isEmpty()) {
            double totalUsableWeight = usableWeights.stream().mapToDouble(Double::doubleValue).sum();
            if (totalUsableWeight > 0) {
                double[][] returnsMatrix = alignedReturns(usableSeries);
                double[] normalised = new double[usableWeights.size()];
                for (int i = 0; i < usableWeights.size(); i++) {
                    normalised[i] = usableWeights.get(i) / totalUsableWeight;
                }

                double[] portfolioReturns = metrics.weightedPortfolioReturns(returnsMatrix, normalised);
                report.setPortfolioVolatilityPercent(round(metrics.annualisedVolatilityPercent(portfolioReturns)));
                report.setSharpeRatio(round(metrics.sharpeRatio(portfolioReturns, riskFreeRatePercent)));
                report.setPortfolioMaxDrawdownPercent(round(metrics.maxDrawdownPercent(cumulate(portfolioReturns))));
            }
        }

        report.setConcentrationHhi(round(metrics.herfindahlIndex(weightFractions)));
        report.setDiversificationScore(round(metrics.diversificationScore(weightFractions)));

        risks.stream()
                .filter(r -> r.getWeightPercent() != null)
                .max(Comparator.comparing(StockRisk::getWeightPercent))
                .ifPresent(top -> {
                    report.setTopHoldingTicker(top.getTicker());
                    report.setTopHoldingWeightPercent(top.getWeightPercent());
                });

        report.setHighestRiskHoldings(risks.stream()
                .filter(r -> r.getRiskScore() != null)
                .sorted(Comparator.comparing(StockRisk::getRiskScore).reversed())
                .limit(3)
                .toList());
    }

    /** Turns a return series back into a notional price path so drawdown can be measured on it. */
    private double[] cumulate(double[] returns) {
        if (returns.length == 0) return new double[0];

        double[] path = new double[returns.length + 1];
        path[0] = 100;
        for (int i = 0; i < returns.length; i++) {
            path[i + 1] = path[i] * (1 + returns[i]);
        }
        return path;
    }

    private double valueWeightedScore(List<StockRisk> risks) {
        double weighted = 0;
        double totalWeight = 0;
        for (StockRisk risk : risks) {
            if (risk.getRiskScore() == null || risk.getWeightPercent() == null) continue;
            weighted += risk.getRiskScore().doubleValue() * risk.getWeightPercent().doubleValue();
            totalWeight += risk.getWeightPercent().doubleValue();
        }
        return totalWeight == 0 ? Double.NaN : weighted / totalWeight;
    }

    private double weightedMetric(List<StockRisk> risks, java.util.function.Function<StockRisk, BigDecimal> extractor) {
        double weighted = 0;
        double totalWeight = 0;
        for (StockRisk risk : risks) {
            BigDecimal value = risk.getMetrics() == null ? null : extractor.apply(risk);
            if (value == null || risk.getWeightPercent() == null) continue;
            weighted += value.doubleValue() * risk.getWeightPercent().doubleValue();
            totalWeight += risk.getWeightPercent().doubleValue();
        }
        return totalWeight == 0 ? Double.NaN : weighted / totalWeight;
    }

    private Map<HoldingType, BigDecimal> allocationByType(List<HoldingPerformance> holdings, BigDecimal totalValue) {
        Map<HoldingType, BigDecimal> allocation = new LinkedHashMap<>();
        for (HoldingType type : HoldingType.values()) {
            BigDecimal typeValue = holdings.stream()
                    .filter(h -> h.getType() == type)
                    .map(HoldingPerformance::getCurrentValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (typeValue.compareTo(BigDecimal.ZERO) > 0) {
                allocation.put(type, round(percentOf(typeValue, totalValue)));
            }
        }
        return allocation;
    }

    private List<String> buildWarnings(PortfolioRiskReport report, List<StockRisk> risks) {
        List<String> warnings = new ArrayList<>();

        for (StockRisk risk : risks) {
            if (risk.getWeightPercent() != null
                    && risk.getWeightPercent().doubleValue() > CONCENTRATION_WARNING_PERCENT) {
                warnings.add(risk.getTicker() + " is " + risk.getWeightPercent()
                        + "% of your portfolio - a single position that large dominates your outcome");
            }
        }

        if (report.getHoldingsCount() < THIN_PORTFOLIO_HOLDINGS) {
            warnings.add("Only " + report.getHoldingsCount()
                    + " holdings - there is little to absorb a bad result in any one of them");
        }

        if (report.getPortfolioBeta() != null && report.getPortfolioBeta().doubleValue() > HIGH_BETA_WARNING) {
            warnings.add("Portfolio beta of " + report.getPortfolioBeta()
                    + " means a market fall would hit you harder than the index");
        }

        risks.stream()
                .filter(r -> r.getRiskLevel() == RiskLevel.VERY_HIGH)
                .forEach(r -> warnings.add(r.getTicker() + " scores "
                        + r.getRiskScore() + "/100 on risk - the highest band"));

        risks.stream()
                .filter(r -> r.getDataQuality() == DataQuality.UNAVAILABLE)
                .forEach(r -> warnings.add("No price history for " + r.getTicker()
                        + " - it is excluded from the portfolio figures"));

        return warnings;
    }

    private PortfolioRiskReport emptyReport(PortfolioRiskReport report) {
        report.setOverallRiskScore(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        report.setRiskLevel(RiskLevel.LOW);
        report.setDiversificationScore(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        report.setHoldings(List.of());
        report.setHighestRiskHoldings(List.of());
        report.setAllocationByType(Map.of());
        report.setWarnings(List.of("No holdings to analyse yet - add some to see your risk profile"));
        return report;
    }

    /** Package-private so RecommendationService can reuse the benchmark instead of refetching it. */
    DailySeries benchmarkSeries() {
        return marketDataFetcher.fetchDailySeries(benchmarkTicker);
    }

    /**
     * Daily returns for each holding, restricted to the days every holding traded, so the
     * weighted portfolio series adds like for like.
     */
    private double[][] alignedReturns(List<DailySeries> seriesList) {
        long[][] timestamps = new long[seriesList.size()][];
        double[][] closes = new double[seriesList.size()][];
        for (int i = 0; i < seriesList.size(); i++) {
            timestamps[i] = seriesList.get(i).timestampsAsLongs();
            closes[i] = seriesList.get(i).closesAsDoubles();
        }

        double[][] aligned = metrics.alignSeriesOnCommonDays(timestamps, closes);
        if (aligned.length == 0) return new double[0][];

        double[][] returns = new double[aligned.length][];
        for (int i = 0; i < aligned.length; i++) {
            returns[i] = metrics.dailyReturns(aligned[i]);
        }
        return returns;
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

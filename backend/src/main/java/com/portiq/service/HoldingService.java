package com.portiq.service;

import com.portiq.dto.HoldingPerformance;
import com.portiq.dto.HoldingRequest;
import com.portiq.dto.PerformanceSummary;
import com.portiq.exception.ResourceNotFoundException;
import com.portiq.model.Holding;
import com.portiq.model.Portfolio;
import com.portiq.repository.HoldingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class HoldingService {

    private final HoldingRepository holdingRepository;
    private final PortfolioService portfolioService;
    private final PriceService priceService;

    public HoldingService(HoldingRepository holdingRepository,
                          PortfolioService portfolioService,
                          PriceService priceService) {
        this.holdingRepository = holdingRepository;
        this.portfolioService = portfolioService;
        this.priceService = priceService;
    }

    @Transactional(readOnly = true)
    public List<Holding> getHoldingsByPortfolio(Long portfolioId) {
        portfolioService.getById(portfolioId);
        return holdingRepository.findByPortfolioId(portfolioId);
    }

    @Transactional(readOnly = true)
    public Holding getHolding(Long portfolioId, Long holdingId) {
        return holdingRepository.findByIdAndPortfolioId(holdingId, portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Holding not found with id: " + holdingId));
    }

    public Holding addHolding(Long portfolioId, HoldingRequest request) {
        Portfolio portfolio = portfolioService.getById(portfolioId);
        return mergeOrCreate(portfolio, request);
    }

    public Holding updateHolding(Long portfolioId, Long holdingId, HoldingRequest request) {
        Holding holding = getHolding(portfolioId, holdingId);
        holding.setTicker(request.getTicker().toUpperCase());
        holding.setName(request.getName());
        holding.setType(request.getType());
        holding.setQuantity(request.getQuantity());
        holding.setPurchasePrice(request.getPurchasePrice());
        holding.setPurchaseDate(request.getPurchaseDate());
        return holdingRepository.save(holding);
    }

    public void removeHolding(Long portfolioId, Long holdingId) {
        Holding holding = getHolding(portfolioId, holdingId);
        holdingRepository.delete(holding);
    }

    @Transactional(readOnly = true)
    public List<Holding> getAllHoldings() {
        return holdingRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Holding getHoldingById(Long id) {
        return holdingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Holding not found with id: " + id));
    }

    public Holding updateHoldingById(Long id, HoldingRequest request) {
        Holding holding = getHoldingById(id);
        holding.setTicker(request.getTicker().toUpperCase());
        holding.setName(request.getName());
        holding.setType(request.getType());
        holding.setQuantity(request.getQuantity());
        holding.setPurchasePrice(request.getPurchasePrice());
        holding.setPurchaseDate(request.getPurchaseDate());
        return holdingRepository.save(holding);
    }

    public void removeHoldingById(Long id) {
        holdingRepository.delete(getHoldingById(id));
    }

    /** Deletes whichever of the given IDs exist; unknown IDs are silently ignored. Returns the number removed. */
    public int removeHoldingsByIds(List<Long> ids) {
        List<Holding> toDelete = holdingRepository.findAllById(ids);
        holdingRepository.deleteAll(toDelete);
        return toDelete.size();
    }

    /**
     * Adds a holding, merging it into any existing holding with the same ticker - regardless of
     * which portfolio it lives in, since the app presents one flat "all holdings" view - instead
     * of creating a duplicate row. Only a genuinely new ticker is created under the given
     * (default) portfolio.
     *
     * <p>The match is made in memory rather than with a {@code findByTicker} query, and that is
     * forced by the schema rather than chosen: {@code Holding.ticker} is encrypted with a random
     * IV per value, so two rows holding "TCS.NS" have different ciphertext and no {@code WHERE
     * ticker = ?} could ever match. Any query-based version of this would silently find nothing
     * and duplicate every position on the second import.
     */
    public Holding mergeOrCreate(Portfolio portfolio, HoldingRequest request) {
        return mergeAll(portfolio, List.of(request)).get(0);
    }

    /**
     * Merges a batch of requests in one pass.
     *
     * <p>The single-row path above used to be called in a loop by the importers, and each call
     * re-read every holding in the database. A 500-row broker export therefore performed 500 full
     * table loads and 500 individual saves - and because each load decrypts every field of every
     * row, the cost was not just I/O but a few hundred thousand AES operations for an import that
     * needs one read.
     *
     * <p>Here the table is read once into a ticker-keyed map, every request is folded into it, and
     * the result is written with a single {@code saveAll}. Requests that share a ticker with each
     * other merge together correctly too, which the per-row version only achieved by writing and
     * re-reading between rows.
     */
    public List<Holding> mergeAll(Portfolio portfolio, List<HoldingRequest> requests) {
        Map<String, Holding> byTicker = new HashMap<>();
        for (Holding holding : holdingRepository.findAll()) {
            byTicker.putIfAbsent(holding.getTicker().toUpperCase(), holding);
        }

        List<Holding> touched = new ArrayList<>(requests.size());
        for (HoldingRequest request : requests) {
            String ticker = request.getTicker().toUpperCase();
            Holding existing = byTicker.get(ticker);

            if (existing == null) {
                Holding created = toEntity(request, portfolio);
                byTicker.put(ticker, created);
                touched.add(created);
            } else {
                merge(existing, request);
                if (!touched.contains(existing)) {
                    touched.add(existing);
                }
            }
        }

        return holdingRepository.saveAll(touched);
    }

    /** Folds a request into an existing position at weighted-average cost. */
    private void merge(Holding holding, HoldingRequest request) {
        BigDecimal totalQuantity = holding.getQuantity().add(request.getQuantity());
        BigDecimal existingCost = holding.getQuantity().multiply(holding.getPurchasePrice());
        BigDecimal addedCost = request.getQuantity().multiply(request.getPurchasePrice());
        BigDecimal weightedAvgPrice = existingCost.add(addedCost)
                .divide(totalQuantity, 4, RoundingMode.HALF_UP);

        holding.setQuantity(totalQuantity);
        holding.setPurchasePrice(weightedAvgPrice);
    }

    @Transactional(readOnly = true)
    public PerformanceSummary getAggregatePerformance() {
        List<Holding> holdings = holdingRepository.findAll();
        return summarize(null, "All Holdings", holdings);
    }

    @Transactional(readOnly = true)
    public PerformanceSummary getPerformance(Long portfolioId) {
        Portfolio portfolio = portfolioService.getById(portfolioId);
        List<Holding> holdings = holdingRepository.findByPortfolioId(portfolioId);
        return summarize(portfolioId, portfolio.getName(), holdings);
    }

    private PerformanceSummary summarize(Long portfolioId, String portfolioName, List<Holding> holdings) {
        List<HoldingPerformance> performances = holdings.stream()
                .map(this::toHoldingPerformance)
                .collect(Collectors.toList());

        BigDecimal totalCost = performances.stream()
                .map(HoldingPerformance::getCostBasis)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalValue = performances.stream()
                .map(HoldingPerformance::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal gainLoss = totalValue.subtract(totalCost);

        BigDecimal gainLossPercent = totalCost.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : gainLoss.divide(totalCost, 4, RoundingMode.HALF_UP)
                          .multiply(BigDecimal.valueOf(100))
                          .setScale(2, RoundingMode.HALF_UP);

        PerformanceSummary summary = new PerformanceSummary();
        summary.setPortfolioId(portfolioId);
        summary.setPortfolioName(portfolioName);
        summary.setTotalCostBasis(totalCost.setScale(2, RoundingMode.HALF_UP));
        summary.setTotalCurrentValue(totalValue.setScale(2, RoundingMode.HALF_UP));
        summary.setTotalGainLoss(gainLoss.setScale(2, RoundingMode.HALF_UP));
        summary.setGainLossPercent(gainLossPercent);
        summary.setHoldings(performances);
        return summary;
    }

    private HoldingPerformance toHoldingPerformance(Holding holding) {
        BigDecimal currentPrice = priceService.getCurrentPrice(
                holding.getTicker(), holding.getPurchasePrice());

        BigDecimal costBasis = holding.getPurchasePrice()
                .multiply(holding.getQuantity())
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal currentValue = currentPrice
                .multiply(holding.getQuantity())
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal gainLoss = currentValue.subtract(costBasis).setScale(2, RoundingMode.HALF_UP);

        BigDecimal gainLossPercent = costBasis.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : gainLoss.divide(costBasis, 4, RoundingMode.HALF_UP)
                          .multiply(BigDecimal.valueOf(100))
                          .setScale(2, RoundingMode.HALF_UP);

        HoldingPerformance hp = new HoldingPerformance();
        hp.setId(holding.getId());
        hp.setTicker(holding.getTicker());
        hp.setName(holding.getName());
        hp.setType(holding.getType());
        hp.setQuantity(holding.getQuantity());
        hp.setPurchasePrice(holding.getPurchasePrice());
        hp.setCurrentPrice(currentPrice);
        hp.setCostBasis(costBasis);
        hp.setCurrentValue(currentValue);
        hp.setGainLoss(gainLoss);
        hp.setGainLossPercent(gainLossPercent);
        hp.setPurchaseDate(holding.getPurchaseDate());
        return hp;
    }

    private Holding toEntity(HoldingRequest request, Portfolio portfolio) {
        Holding holding = new Holding();
        holding.setPortfolio(portfolio);
        holding.setTicker(request.getTicker().toUpperCase());
        holding.setName(request.getName());
        holding.setType(request.getType());
        holding.setQuantity(request.getQuantity());
        holding.setPurchasePrice(request.getPurchasePrice());
        holding.setPurchaseDate(request.getPurchaseDate());
        return holding;
    }
}

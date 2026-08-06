package com.portiq.service;

import com.portiq.dto.PortfolioHistoryPoint;
import com.portiq.model.Holding;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds a portfolio value time series by re-pricing today's holdings at each historical point.
 * This approximates "what my current holdings were worth over time" rather than reconstructing
 * exact historical portfolio composition.
 */
@Service
public class PriceHistoryService {

    private final PriceSeriesFetcher priceSeriesFetcher;

    public PriceHistoryService(PriceSeriesFetcher priceSeriesFetcher) {
        this.priceSeriesFetcher = priceSeriesFetcher;
    }

    public List<PortfolioHistoryPoint> getPortfolioHistory(List<Holding> holdings, String range) {
        TreeMap<Long, BigDecimal> totals = new TreeMap<>();

        for (Holding holding : holdings) {
            Map<Long, BigDecimal> series = priceSeriesFetcher.fetchSeries(holding.getTicker(), range);
            BigDecimal quantity = holding.getQuantity();
            series.forEach((bucket, close) -> totals.merge(bucket, close.multiply(quantity), BigDecimal::add));
        }

        List<PortfolioHistoryPoint> points = new ArrayList<>();
        totals.forEach((epochSeconds, value) -> {
            PortfolioHistoryPoint point = new PortfolioHistoryPoint();
            point.setTimestamp(epochSeconds);
            point.setValue(value.setScale(2, RoundingMode.HALF_UP));
            points.add(point);
        });
        return points;
    }
}

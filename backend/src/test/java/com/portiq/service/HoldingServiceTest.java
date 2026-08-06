package com.portiq.service;

import com.portiq.dto.HoldingRequest;
import com.portiq.dto.PerformanceSummary;
import com.portiq.exception.ResourceNotFoundException;
import com.portiq.model.Holding;
import com.portiq.model.HoldingType;
import com.portiq.model.Portfolio;
import com.portiq.repository.HoldingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HoldingServiceTest {

    @Mock
    private HoldingRepository holdingRepository;

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private PriceService priceService;

    @InjectMocks
    private HoldingService holdingService;

    private Portfolio portfolio;
    private Holding holding;

    @BeforeEach
    void setUp() {
        portfolio = new Portfolio("Tech Growth", "Tech stocks");
        portfolio.setId(1L);

        holding = new Holding();
        holding.setId(10L);
        holding.setPortfolio(portfolio);
        holding.setTicker("AAPL");
        holding.setName("Apple Inc.");
        holding.setType(HoldingType.STOCK);
        holding.setQuantity(new BigDecimal("10"));
        holding.setPurchasePrice(new BigDecimal("150.00"));
        holding.setPurchaseDate(LocalDate.of(2023, 1, 15));
    }

    @Test
    void getHoldingsByPortfolio_returnsList() {
        when(portfolioService.getById(1L)).thenReturn(portfolio);
        when(holdingRepository.findByPortfolioId(1L)).thenReturn(List.of(holding));

        List<Holding> result = holdingService.getHoldingsByPortfolio(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTicker()).isEqualTo("AAPL");
    }

    @Test
    void getHolding_returnsHolding_whenExists() {
        when(holdingRepository.findByIdAndPortfolioId(10L, 1L)).thenReturn(Optional.of(holding));

        Holding result = holdingService.getHolding(1L, 10L);

        assertThat(result.getId()).isEqualTo(10L);
    }

    @Test
    void getHolding_throwsException_whenNotFound() {
        when(holdingRepository.findByIdAndPortfolioId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> holdingService.getHolding(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void addHolding_savesAndReturnsHolding() {
        HoldingRequest request = new HoldingRequest();
        request.setTicker("TSLA");
        request.setName("Tesla Inc.");
        request.setType(HoldingType.STOCK);
        request.setQuantity(new BigDecimal("5"));
        request.setPurchasePrice(new BigDecimal("200.00"));

        when(portfolioService.getById(1L)).thenReturn(portfolio);
        when(holdingRepository.save(any(Holding.class))).thenAnswer(inv -> {
            Holding h = inv.getArgument(0);
            h.setId(11L);
            return h;
        });

        Holding result = holdingService.addHolding(1L, request);

        assertThat(result.getTicker()).isEqualTo("TSLA");
        assertThat(result.getId()).isEqualTo(11L);
        verify(holdingRepository).save(any(Holding.class));
    }

    @Test
    void removeHolding_deletesHolding() {
        when(holdingRepository.findByIdAndPortfolioId(10L, 1L)).thenReturn(Optional.of(holding));

        holdingService.removeHolding(1L, 10L);

        verify(holdingRepository).delete(holding);
    }

    @Test
    void getPerformance_calculatesCorrectly() {
        when(portfolioService.getById(1L)).thenReturn(portfolio);
        when(holdingRepository.findByPortfolioId(1L)).thenReturn(List.of(holding));
        when(priceService.getCurrentPrice("AAPL", new BigDecimal("150.00")))
                .thenReturn(new BigDecimal("180.00"));

        PerformanceSummary summary = holdingService.getPerformance(1L);

        assertThat(summary.getPortfolioName()).isEqualTo("Tech Growth");
        assertThat(summary.getTotalCostBasis()).isEqualByComparingTo("1500.00");
        assertThat(summary.getTotalCurrentValue()).isEqualByComparingTo("1800.00");
        assertThat(summary.getTotalGainLoss()).isEqualByComparingTo("300.00");
        assertThat(summary.getGainLossPercent()).isEqualByComparingTo("20.00");
    }

    @Test
    void getPerformance_emptyPortfolio_returnsZeros() {
        when(portfolioService.getById(1L)).thenReturn(portfolio);
        when(holdingRepository.findByPortfolioId(1L)).thenReturn(List.of());

        PerformanceSummary summary = holdingService.getPerformance(1L);

        assertThat(summary.getTotalCostBasis()).isEqualByComparingTo("0.00");
        assertThat(summary.getTotalCurrentValue()).isEqualByComparingTo("0.00");
        assertThat(summary.getGainLossPercent()).isEqualByComparingTo("0.00");
    }
}

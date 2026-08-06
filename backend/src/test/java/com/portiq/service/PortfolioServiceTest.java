package com.portiq.service;

import com.portiq.dto.PortfolioRequest;
import com.portiq.exception.ResourceNotFoundException;
import com.portiq.model.Portfolio;
import com.portiq.repository.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @InjectMocks
    private PortfolioService portfolioService;

    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        portfolio = new Portfolio("Tech Growth", "Tech stocks");
        portfolio.setId(1L);
    }

    @Test
    void getAll_returnsAllPortfolios() {
        when(portfolioRepository.findAll()).thenReturn(List.of(portfolio));

        List<Portfolio> result = portfolioService.getAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Tech Growth");
    }

    @Test
    void getById_returnsPortfolio_whenExists() {
        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(portfolio));

        Portfolio result = portfolioService.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Tech Growth");
    }

    @Test
    void getById_throwsException_whenNotFound() {
        when(portfolioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void create_savesAndReturnsPortfolio() {
        PortfolioRequest request = new PortfolioRequest();
        request.setName("New Portfolio");
        request.setDescription("Description");

        Portfolio saved = new Portfolio("New Portfolio", "Description");
        saved.setId(2L);

        when(portfolioRepository.save(any(Portfolio.class))).thenReturn(saved);

        Portfolio result = portfolioService.create(request);

        assertThat(result.getName()).isEqualTo("New Portfolio");
        verify(portfolioRepository, times(1)).save(any(Portfolio.class));
    }

    @Test
    void update_updatesFieldsAndSaves() {
        PortfolioRequest request = new PortfolioRequest();
        request.setName("Updated Name");
        request.setDescription("Updated Desc");

        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(portfolio));
        when(portfolioRepository.save(any(Portfolio.class))).thenAnswer(inv -> inv.getArgument(0));

        Portfolio result = portfolioService.update(1L, request);

        assertThat(result.getName()).isEqualTo("Updated Name");
        assertThat(result.getDescription()).isEqualTo("Updated Desc");
    }

    @Test
    void delete_removesPortfolio() {
        when(portfolioRepository.findById(1L)).thenReturn(Optional.of(portfolio));

        portfolioService.delete(1L);

        verify(portfolioRepository, times(1)).delete(portfolio);
    }

    @Test
    void delete_throwsException_whenPortfolioNotFound() {
        when(portfolioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

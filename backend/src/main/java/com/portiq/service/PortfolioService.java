package com.portiq.service;

import com.portiq.dto.PortfolioRequest;
import com.portiq.exception.ResourceNotFoundException;
import com.portiq.model.Portfolio;
import com.portiq.repository.PortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;

    public PortfolioService(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    @Transactional(readOnly = true)
    public List<Portfolio> getAll() {
        return portfolioRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Portfolio getById(Long id) {
        return portfolioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Portfolio not found with id: " + id));
    }

    public Portfolio create(PortfolioRequest request) {
        Portfolio portfolio = new Portfolio(request.getName(), request.getDescription());
        return portfolioRepository.save(portfolio);
    }

    public Portfolio update(Long id, PortfolioRequest request) {
        Portfolio portfolio = getById(id);
        portfolio.setName(request.getName());
        portfolio.setDescription(request.getDescription());
        return portfolioRepository.save(portfolio);
    }

    public void delete(Long id) {
        Portfolio portfolio = getById(id);
        portfolioRepository.delete(portfolio);
    }

    public Portfolio getOrCreateDefault() {
        return portfolioRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> portfolioRepository.save(new Portfolio("My Portfolio", "Default portfolio")));
    }
}

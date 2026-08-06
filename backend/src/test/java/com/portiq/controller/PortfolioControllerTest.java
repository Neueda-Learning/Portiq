package com.portiq.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portiq.dto.PortfolioRequest;
import com.portiq.exception.GlobalExceptionHandler;
import com.portiq.exception.ResourceNotFoundException;
import com.portiq.model.Portfolio;
import com.portiq.service.HoldingService;
import com.portiq.service.PortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PortfolioControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private PortfolioService portfolioService;

    @Mock
    private HoldingService holdingService;

    @InjectMocks
    private PortfolioController portfolioController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(portfolioController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAllPortfolios_returns200() throws Exception {
        Portfolio p = new Portfolio("Tech Growth", "Tech stocks");
        p.setId(1L);
        when(portfolioService.getAll()).thenReturn(List.of(p));

        mockMvc.perform(get("/api/portfolios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Tech Growth"));
    }

    @Test
    void getPortfolio_returns200_whenExists() throws Exception {
        Portfolio p = new Portfolio("Tech Growth", "Tech stocks");
        p.setId(1L);
        when(portfolioService.getById(1L)).thenReturn(p);

        mockMvc.perform(get("/api/portfolios/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getPortfolio_returns404_whenNotFound() throws Exception {
        when(portfolioService.getById(99L))
                .thenThrow(new ResourceNotFoundException("Portfolio not found with id: 99"));

        mockMvc.perform(get("/api/portfolios/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Portfolio not found with id: 99"));
    }

    @Test
    void createPortfolio_returns201_withValidData() throws Exception {
        PortfolioRequest request = new PortfolioRequest();
        request.setName("New Portfolio");

        Portfolio saved = new Portfolio("New Portfolio", null);
        saved.setId(2L);
        when(portfolioService.create(any(PortfolioRequest.class))).thenReturn(saved);

        mockMvc.perform(post("/api/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Portfolio"));
    }

    @Test
    void createPortfolio_returns400_whenNameMissing() throws Exception {
        PortfolioRequest request = new PortfolioRequest();

        mockMvc.perform(post("/api/portfolios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletePortfolio_returns204() throws Exception {
        doNothing().when(portfolioService).delete(1L);

        mockMvc.perform(delete("/api/portfolios/1"))
                .andExpect(status().isNoContent());

        verify(portfolioService).delete(1L);
    }
}

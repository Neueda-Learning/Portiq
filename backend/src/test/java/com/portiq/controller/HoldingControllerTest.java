package com.portiq.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.portiq.dto.HoldingRequest;
import com.portiq.exception.GlobalExceptionHandler;
import com.portiq.model.Holding;
import com.portiq.model.HoldingType;
import com.portiq.model.Portfolio;
import com.portiq.service.HoldingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class HoldingControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Mock
    private HoldingService holdingService;

    @InjectMocks
    private HoldingController holdingController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(holdingController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Holding buildHolding() {
        Portfolio p = new Portfolio("Tech Growth", "desc");
        p.setId(1L);

        Holding h = new Holding();
        h.setId(10L);
        h.setTicker("AAPL");
        h.setName("Apple Inc.");
        h.setType(HoldingType.STOCK);
        h.setQuantity(new BigDecimal("10"));
        h.setPurchasePrice(new BigDecimal("150.00"));
        h.setPurchaseDate(LocalDate.of(2023, 1, 15));
        return h;
    }

    @Test
    void getHoldings_returns200() throws Exception {
        when(holdingService.getHoldingsByPortfolio(1L)).thenReturn(List.of(buildHolding()));

        mockMvc.perform(get("/api/portfolios/1/holdings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("AAPL"));
    }

    @Test
    void addHolding_returns201_withValidData() throws Exception {
        HoldingRequest req = new HoldingRequest();
        req.setTicker("TSLA");
        req.setName("Tesla Inc.");
        req.setType(HoldingType.STOCK);
        req.setQuantity(new BigDecimal("5"));
        req.setPurchasePrice(new BigDecimal("200.00"));
        req.setPurchaseDate(LocalDate.of(2023, 3, 1));

        Holding saved = buildHolding();
        saved.setTicker("TSLA");

        when(holdingService.addHolding(eq(1L), any(HoldingRequest.class))).thenReturn(saved);

        mockMvc.perform(post("/api/portfolios/1/holdings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void addHolding_returns400_whenTickerMissing() throws Exception {
        HoldingRequest req = new HoldingRequest();
        req.setName("Apple Inc.");
        req.setType(HoldingType.STOCK);
        req.setQuantity(new BigDecimal("10"));
        req.setPurchasePrice(new BigDecimal("150.00"));

        mockMvc.perform(post("/api/portfolios/1/holdings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void removeHolding_returns204() throws Exception {
        doNothing().when(holdingService).removeHolding(1L, 10L);

        mockMvc.perform(delete("/api/portfolios/1/holdings/10"))
                .andExpect(status().isNoContent());

        verify(holdingService).removeHolding(1L, 10L);
    }
}

package com.portiq.exception;

import com.portiq.controller.RiskController;
import com.portiq.service.RiskAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * These assert the shape and wording clients depend on. A generic 500 for a client-side mistake
 * sends people debugging the wrong system, so the status codes matter as much as the text.
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @Mock
    private RiskAnalysisService riskAnalysisService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RiskController(riskAnalysisService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void invalidRequest_returns400WithTheMessageVerbatim() throws Exception {
        when(riskAnalysisService.analyseTicker(anyString()))
                .thenThrow(new InvalidRequestException("A ticker symbol is required, for example RELIANCE.NS."));

        mockMvc.perform(get("/api/risk/x"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A ticker symbol is required, for example RELIANCE.NS."))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void notFound_returns404WithTheMessage() throws Exception {
        when(riskAnalysisService.analyseTicker(anyString()))
                .thenThrow(new ResourceNotFoundException("Holding not found with id: 7"));

        mockMvc.perform(get("/api/risk/AAA"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Holding not found with id: 7"));
    }

    /**
     * The caller gets a reference code instead of a stack trace: enough to correlate a report with
     * the logged exception, without leaking internals in the response.
     */
    @Test
    void unexpectedFailure_returns500WithATraceableReference() throws Exception {
        when(riskAnalysisService.analyseTicker(anyString()))
                .thenThrow(new IllegalStateException("connection pool exhausted"));

        mockMvc.perform(get("/api/risk/AAA"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.reference").isNotEmpty())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Quote reference")))
                // The internal detail must not reach the client.
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("connection pool"))));
    }
}

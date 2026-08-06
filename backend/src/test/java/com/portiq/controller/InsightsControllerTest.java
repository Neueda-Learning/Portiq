package com.portiq.controller;

import com.portiq.service.InsightsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.http.HttpStatus;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InsightsControllerTest {

    private MockMvc mockMvc;

    @Mock
    private InsightsService insightsService;

    @InjectMocks
    private InsightsController insightsController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(insightsController).build();
    }

    @Test
    void returnsTheSummaryWhenTheServiceIsAvailable() throws Exception {
        when(insightsService.isAvailable()).thenReturn(true);
        when(insightsService.generateSummary()).thenReturn("Your portfolio is up 8% this year.");

        mockMvc.perform(get("/api/insights/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Your portfolio is up 8% this year."));
    }

    @Test
    void namesTheMissingVariableWhenTheFeatureIsNotConfigured() throws Exception {
        // "Not configured on this server" on its own is a dead end: both halves have to be set,
        // only one is usually forgotten, and the reader's next move was to go and read source.
        when(insightsService.isAvailable()).thenReturn(false);
        when(insightsService.missingConfiguration()).thenReturn("INSIGHTS_API_KEY");

        mockMvc.perform(get("/api/insights/summary"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(
                        "Summaries are not configured on this server. Set INSIGHTS_API_KEY "
                                + "and restart the backend."));
    }

    @Test
    void neverRendersTheWordNullWhenTheMissingVariableIsUnknown() throws Exception {
        // Unreachable in practice, but "Set null and restart the backend" is a bad enough thing
        // to show someone that it is worth making impossible rather than unlikely.
        when(insightsService.isAvailable()).thenReturn(false);
        when(insightsService.missingConfiguration()).thenReturn(null);

        mockMvc.perform(get("/api/insights/summary"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("null"))));
    }

    @Test
    void doesNotLeakTheUpstreamErrorMessageToTheCaller() throws Exception {
        // A provider's error text can name the vendor, quote the endpoint, echo request fragments,
        // and restate the model and account tier. None of that is actionable for a user and all of
        // it is free reconnaissance, so it belongs in the log rather than the response.
        String leaky = "401 Unauthorized from POST https://api.vendor.example/v1/chat/completions: "
                + "invalid api key sk-live-abc123 for org org-portiq-prod";
        when(insightsService.isAvailable()).thenReturn(true);
        when(insightsService.generateSummary())
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, leaky));

        mockMvc.perform(get("/api/insights/summary"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message")
                        .value("The summary service is unavailable right now. Try again shortly."))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("sk-live"))))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("api.vendor.example"))));
    }

    @Test
    void reportsAnUnusableResponseWithoutRepeatingItsDetail() throws Exception {
        when(insightsService.isAvailable()).thenReturn(true);
        when(insightsService.generateSummary())
                .thenThrow(new IllegalStateException("The model service returned no usable content"));

        mockMvc.perform(get("/api/insights/summary"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message")
                        .value("The summary could not be generated. Try again shortly."));
    }
}

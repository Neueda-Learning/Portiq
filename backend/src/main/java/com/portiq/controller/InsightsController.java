package com.portiq.controller;

import com.portiq.service.InsightsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@RestController
@RequestMapping("/api/insights")
@Tag(name = "Insights", description = "Plain-language portfolio summary")
public class InsightsController {

    private static final Logger log = LoggerFactory.getLogger(InsightsController.class);

    private final InsightsService insightsService;

    public InsightsController(InsightsService insightsService) {
        this.insightsService = insightsService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Generate a short summary of current portfolio performance")
    public ResponseEntity<?> summary() {
        if (!insightsService.isAvailable()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Summaries are not configured on this server"));
        }
        try {
            return ResponseEntity.ok(Map.of("summary", insightsService.generateSummary()));
        } catch (RestClientException e) {
            // The upstream message is written for us, not for the caller: it can name the vendor,
            // quote the endpoint, echo request fragments, and in some providers restate the model
            // and account tier. None of that is actionable for a user and all of it is free
            // reconnaissance, so it goes to the log and the caller gets the fact of the failure.
            log.warn("The summary provider call failed", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", "The summary service is unavailable right now. Try again shortly."));
        } catch (IllegalStateException e) {
            // Raised by our own code for a response that arrived but carried no usable content.
            log.warn("The summary provider returned an unusable response: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("message", "The summary could not be generated. Try again shortly."));
        }
    }
}

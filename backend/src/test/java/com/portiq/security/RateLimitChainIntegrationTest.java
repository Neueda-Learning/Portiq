package com.portiq.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portiq.dto.LoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Confirms the rate limiter is genuinely in the security chain and running before authentication.
 *
 * <p>Unit-testing the filter proves it counts correctly but says nothing about whether it is
 * plugged in - and a filter registered in the wrong position, or not at all, fails silently: every
 * request succeeds, which is exactly what a passing test suite looks like.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.jwt.secret=another-integration-test-secret-over-32-bytes",
        "app.owner.username=owner",
        "app.owner.password=integration-test-password",
        "app.security.rate-limit.enabled=true",
        // Set high enough that the request throttle, not the account lockout, is what answers -
        // otherwise this would be testing LoginAttemptService by accident.
        "app.security.login.max-failures-per-account=10000",
        "app.security.login.max-failures-per-address=10000"
})
class RateLimitChainIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void unauthenticatedLoginAttemptsAreThrottledBeforeReachingTheController() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("owner");
        request.setPassword("wrong-password");
        String body = objectMapper.writeValueAsString(request);

        int refused = 0;
        // The auth bucket allows 20 per five minutes, so 30 attempts must run into it.
        //
        // Counting refusals across the whole run rather than checking the last response: the
        // limiter uses fixed windows, so a run that happens to straddle a window boundary gets a
        // fresh budget partway through and the final attempt is allowed again. That is a real
        // property of the algorithm, not a defect, and a test that ignored it would fail roughly
        // once in every few hundred runs for no useful reason.
        for (int i = 0; i < 30; i++) {
            int status = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn().getResponse().getStatus();
            if (status == 429) {
                refused++;
            }
        }

        assertThat(refused)
                .as("the filter must be reachable in the real chain, not only in isolation")
                .isPositive();
    }
}

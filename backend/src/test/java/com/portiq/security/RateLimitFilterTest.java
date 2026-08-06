package com.portiq.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RateLimitFilterTest {

    private RateLimitFilter filter;
    private ClientIpResolver clientIpResolver;
    private SecurityAuditLogger audit;

    @BeforeEach
    void setUp() {
        clientIpResolver = new ClientIpResolver();
        ReflectionTestUtils.setField(clientIpResolver, "trustProxy", false);
        audit = Mockito.mock(SecurityAuditLogger.class);
        filter = new RateLimitFilter(new RateLimiter(), clientIpResolver, audit, true);
    }

    @Test
    void refusesTheLoginEndpointOnceTheAuthBudgetIsSpent() throws Exception {
        // 20 attempts per five minutes: enough for a person fumbling, nowhere near a guessing run.
        for (int i = 0; i < 20; i++) {
            assertThat(callLogin().getStatus()).isNotEqualTo(429);
        }

        MockHttpServletResponse blocked = callLogin();

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotNull();
        assertThat(blocked.getContentAsString()).contains("Too many requests");
        assertThat(blocked.getContentType()).contains("application/json");
    }

    @Test
    void doesNotForwardTheRequestOnceBlocked() throws Exception {
        for (int i = 0; i < 20; i++) {
            callLogin();
        }

        FilterChain chain = Mockito.mock(FilterChain.class);
        filter.doFilter(request("POST", "/api/auth/login"), new MockHttpServletResponse(), chain);

        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void logsTheRefusal() throws Exception {
        for (int i = 0; i < 21; i++) {
            callLogin();
        }

        verify(audit).rateLimited(any(), any());
    }

    @Test
    void spendingTheLoginBudgetDoesNotBlockOrdinaryReads() throws Exception {
        for (int i = 0; i < 25; i++) {
            callLogin();
        }

        MockHttpServletResponse response = call("GET", "/api/holdings");

        assertThat(response.getStatus())
                .as("a guessing run against login must not take the whole API down with it")
                .isNotEqualTo(429);
    }

    @Test
    void countsEachCallerSeparately() throws Exception {
        for (int i = 0; i < 21; i++) {
            call("POST", "/api/auth/login", "203.0.113.1");
        }

        assertThat(call("POST", "/api/auth/login", "198.51.100.1").getStatus()).isNotEqualTo(429);
    }

    @Test
    void doesNotCountCorsPreflights() throws Exception {
        for (int i = 0; i < 50; i++) {
            call("OPTIONS", "/api/auth/login");
        }

        assertThat(callLogin().getStatus())
                .as("a browser is required to send a preflight; charging for it penalises correct clients")
                .isNotEqualTo(429);
    }

    @Test
    void reportsTheRemainingBudget() throws Exception {
        MockHttpServletResponse response = callLogin();

        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("20");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("19");
    }

    @Test
    void passesEverythingThroughWhenDisabled() throws Exception {
        RateLimitFilter disabled = new RateLimitFilter(new RateLimiter(), clientIpResolver, audit, false);

        for (int i = 0; i < 100; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            disabled.doFilter(request("POST", "/api/auth/login"), response, new MockFilterChain());
            assertThat(response.getStatus()).isNotEqualTo(429);
        }
    }

    private MockHttpServletResponse callLogin() throws Exception {
        return call("POST", "/api/auth/login");
    }

    private MockHttpServletResponse call(String method, String path) throws Exception {
        return call(method, path, "203.0.113.7");
    }

    private MockHttpServletResponse call(String method, String path, String ip) throws Exception {
        MockHttpServletRequest request = request(method, path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        request.setRemoteAddr("203.0.113.7");
        return request;
    }
}

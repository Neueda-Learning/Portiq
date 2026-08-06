package com.portiq.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Per-caller request throttling, applied before authentication so it also covers the login
 * endpoints - the ones most worth protecting, since an unauthenticated attacker can hit them
 * as fast as the network allows.
 *
 * <p>Requests are bucketed by cost rather than treated uniformly. A dashboard poll and a
 * credential guess are both "one request", but only one of them is worth 600 an hour; and the
 * import endpoints each cost an upstream model call, so they get the tightest budget of all.
 *
 * <p>Deliberately not a {@code @Component}: Boot auto-registers component filters into the outer
 * servlet chain as well as the security chain, which would count every request twice.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    /** Login and biometric-assertion endpoints: enough for real fumbling, far short of a guessing run. */
    private static final Bucket AUTH = new Bucket("auth", 20, Duration.ofMinutes(5));

    /** Each of these spends an upstream model call, so abuse costs real money as well as capacity. */
    private static final Bucket AI = new Bucket("ai", 20, Duration.ofMinutes(10));

    /** Analytics and exports: a full risk report fetches a year of history per holding. */
    private static final Bucket EXPENSIVE = new Bucket("expensive", 60, Duration.ofMinutes(1));

    /** Anything that changes state. */
    private static final Bucket WRITE = new Bucket("write", 120, Duration.ofMinutes(1));

    /** Ordinary reads - high enough that normal use never notices it. */
    private static final Bucket GENERAL = new Bucket("general", 600, Duration.ofMinutes(1));

    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final SecurityAuditLogger audit;
    private final boolean enabled;

    public RateLimitFilter(RateLimiter rateLimiter, ClientIpResolver clientIpResolver,
                            SecurityAuditLogger audit, boolean enabled) {
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
        this.audit = audit;
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Preflights carry no credentials and no payload; counting them would penalise a
        // cross-origin browser client for the CORS handshake it is required to perform.
        if (!enabled || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        Bucket bucket = bucketFor(request);
        String client = clientIpResolver.resolve(request);
        RateLimiter.Decision decision = rateLimiter.record(bucket.name(), client, bucket.limit(), bucket.window());

        response.setHeader("X-RateLimit-Limit", String.valueOf(bucket.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));

        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        audit.rateLimited(request, bucket.name());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"timestamp\":\"" + LocalDateTime.now()
                + "\",\"status\":429,\"message\":\"Too many requests. Try again in "
                + decision.retryAfterSeconds() + " seconds.\"}");
    }

    private Bucket bucketFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        if (path.startsWith("/api/auth/login") || path.startsWith("/api/auth/webauthn/login")) {
            return AUTH;
        }
        if (path.startsWith("/api/holdings/import") || path.startsWith("/api/insights")) {
            return AI;
        }
        if (path.startsWith("/api/risk") || path.startsWith("/api/recommendations")
                || path.startsWith("/api/news") || path.startsWith("/api/holdings/export")
                || path.startsWith("/api/holdings/history")) {
            return EXPENSIVE;
        }
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            return WRITE;
        }
        return GENERAL;
    }

    private record Bucket(String name, int limit, Duration window) {}
}

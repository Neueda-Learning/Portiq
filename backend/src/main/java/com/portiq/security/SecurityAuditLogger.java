package com.portiq.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Single place every security-relevant event is written from, under a dedicated logger name
 * ({@code SECURITY}) so the events can be shipped or alerted on without sifting the whole
 * application log.
 *
 * <p>Two rules hold for everything written here. Nothing secret is ever passed in - no passwords,
 * tokens, cookies or API keys, only the fact that one was presented and whether it was accepted.
 * And every caller-supplied value goes through {@link #safe} first, because a username containing
 * a newline would otherwise let an attacker forge extra log lines and hide their own trail.
 */
@Component
public class SecurityAuditLogger {

    private static final Logger log = LoggerFactory.getLogger("SECURITY");

    /** Long enough to identify an actor, short enough that a flood cannot fill the disk. */
    private static final int MAX_VALUE_LENGTH = 120;

    private final ClientIpResolver clientIpResolver;

    /**
     * Whole-IP logging is useful for tracing an attack but is personal data under GDPR. Sites that
     * would rather not retain it can log a truncated address instead.
     */
    @Value("${app.security.audit.log-client-ip:true}")
    private boolean logClientIp;

    public SecurityAuditLogger(ClientIpResolver clientIpResolver) {
        this.clientIpResolver = clientIpResolver;
    }

    public void loginSucceeded(String username, HttpServletRequest request) {
        log.info("event=LOGIN_SUCCESS user={} ip={}", safe(username), ip(request));
    }

    public void loginFailed(String username, HttpServletRequest request, String reason) {
        log.warn("event=LOGIN_FAILURE user={} ip={} reason={}", safe(username), ip(request), safe(reason));
    }

    public void loginBlocked(String username, HttpServletRequest request, long retryAfterSeconds) {
        log.warn("event=LOGIN_LOCKED_OUT user={} ip={} retryAfterSeconds={}",
                safe(username), ip(request), retryAfterSeconds);
    }

    public void logout(String username, HttpServletRequest request) {
        log.info("event=LOGOUT user={} ip={}", safe(username), ip(request));
    }

    public void rateLimited(HttpServletRequest request, String bucket) {
        log.warn("event=RATE_LIMITED ip={} bucket={} method={} path={}",
                ip(request), safe(bucket), safe(request.getMethod()), safe(request.getRequestURI()));
    }

    public void invalidToken(HttpServletRequest request, String reason) {
        log.warn("event=INVALID_TOKEN ip={} path={} reason={}",
                ip(request), safe(request.getRequestURI()), safe(reason));
    }

    public void authenticationRequired(HttpServletRequest request) {
        log.info("event=AUTH_REQUIRED ip={} method={} path={}",
                ip(request), safe(request.getMethod()), safe(request.getRequestURI()));
    }

    public void accessDenied(HttpServletRequest request, String principal) {
        log.warn("event=ACCESS_DENIED user={} ip={} method={} path={}",
                safe(principal), ip(request), safe(request.getMethod()), safe(request.getRequestURI()));
    }

    public void uploadRejected(HttpServletRequest request, String filename, String reason) {
        log.warn("event=UPLOAD_REJECTED ip={} filename={} reason={}",
                ip(request), safe(filename), safe(reason));
    }

    public void outboundBlocked(String url, String reason) {
        log.warn("event=OUTBOUND_REQUEST_BLOCKED url={} reason={}", safe(url), safe(reason));
    }

    public void webauthnEvent(String event, String username, String detail) {
        log.info("event=WEBAUTHN_{} user={} detail={}", safe(event), safe(username), safe(detail));
    }

    private String ip(HttpServletRequest request) {
        String address = clientIpResolver.resolve(request);
        if (!logClientIp) {
            return "redacted";
        }
        return safe(address);
    }

    /**
     * Strips CR/LF and other control characters so a caller-supplied value cannot break out of its
     * field and forge a log line, and caps the length so one request cannot write a megabyte.
     */
    static String safe(String value) {
        if (value == null || value.isEmpty()) {
            return "-";
        }
        String trimmed = value.length() > MAX_VALUE_LENGTH ? value.substring(0, MAX_VALUE_LENGTH) + "..." : value;
        StringBuilder cleaned = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            cleaned.append(Character.isISOControl(c) ? '_' : c);
        }
        return cleaned.toString();
    }
}

package com.portiq.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Answers unauthenticated and forbidden requests with the same JSON shape as
 * {@code GlobalExceptionHandler}, so a client never has to parse two different error formats.
 *
 * <p>It also fixes a real behavioural bug: with no entry point configured, a stateless Spring
 * Security chain answers an unauthenticated call with 403. The frontend only clears a stale token
 * and redirects to the login page on 401, so an expired session left the UI stuck showing
 * permission errors instead of asking the user to log in again.
 *
 * <p>Neither response says anything about *why* - "no token" and "expired token" look identical
 * from outside. The detail goes to the security log, not to the caller.
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecurityAuditLogger audit;

    public JsonAuthenticationEntryPoint(SecurityAuditLogger audit) {
        this.audit = audit;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        audit.authenticationRequired(request);
        write(response, HttpStatus.UNAUTHORIZED, "Authentication is required. Log in and try again.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        audit.accessDenied(request, authentication != null ? authentication.getName() : null);
        write(response, HttpStatus.FORBIDDEN, "You do not have permission to do that.");
    }

    private void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("message", message);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), body);
    }
}

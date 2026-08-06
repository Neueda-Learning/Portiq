package com.portiq.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Works out who a request came from, for rate limiting and audit logging.
 *
 * <p>{@code X-Forwarded-For} is only trusted when the deployment says a proxy is in front. That
 * switch matters: an attacker can set the header freely, so honouring it on a directly exposed
 * server hands them a fresh identity per request and makes every per-IP limit meaningless. When
 * trusted, the *last* entry is used rather than the first - a client can prepend fake hops, but
 * only the proxy immediately in front of us appends the entry it observed.
 */
@Component
public class ClientIpResolver {

    @Value("${app.security.trust-proxy:false}")
    private boolean trustProxy;

    public String resolve(HttpServletRequest request) {
        if (trustProxy) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] hops = forwarded.split(",");
                String nearest = hops[hops.length - 1].trim();
                if (!nearest.isEmpty()) {
                    return nearest;
                }
            }
        }
        String remote = request.getRemoteAddr();
        return remote != null ? remote : "unknown";
    }

    void setTrustProxy(boolean trustProxy) {
        this.trustProxy = trustProxy;
    }
}

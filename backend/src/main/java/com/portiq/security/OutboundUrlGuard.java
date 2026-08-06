package com.portiq.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Decides whether the application is allowed to make a given outbound request.
 *
 * <p>The URLs this application fetches are built from stored tickers - and tickers arrive from a
 * CSV, or from a language model reading an uploaded screenshot, not only from a form. That is the
 * SSRF shape: an attacker who controls part of a URL the *server* fetches gets to make requests
 * from inside the network, reaching services that were never exposed and that often trust local
 * callers implicitly.
 *
 * <p>Two layers, because an allowlist alone can be defeated by a hostname that resolves inward.
 * The host must appear on the list, and it must not be a loopback, link-local or private address -
 * the last of which specifically covers {@code 169.254.169.254}, the cloud metadata endpoint whose
 * response is a set of instance credentials.
 *
 * <p>The ticker itself is separately encoded by callers so it cannot escape its path segment; this
 * guard is the backstop for the assembled URL.
 */
@Component
public class OutboundUrlGuard {

    private final SecurityAuditLogger audit;

    /**
     * Hosts the application is permitted to call. Matching is on exact host or a dot-suffix, so
     * {@code finance.yahoo.com} covers {@code query1.finance.yahoo.com} but never
     * {@code finance.yahoo.com.attacker.net}.
     */
    @Value("${app.security.outbound.allowed-hosts}")
    private String[] allowedHosts;

    public OutboundUrlGuard(SecurityAuditLogger audit) {
        this.audit = audit;
    }

    /** Returns true when the URL may be fetched; logs and returns false otherwise. */
    public boolean isAllowed(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            audit.outboundBlocked(url, "not a valid URL");
            return false;
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("http")) {
            audit.outboundBlocked(url, "scheme '" + scheme + "' is not http(s)");
            return false;
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            audit.outboundBlocked(url, "no host");
            return false;
        }
        host = host.toLowerCase(Locale.ROOT);

        if (!hostAllowed(host)) {
            audit.outboundBlocked(url, "host '" + host + "' is not on the allowlist");
            return false;
        }
        if (isInternalAddress(host)) {
            audit.outboundBlocked(url, "host '" + host + "' resolves to an internal address");
            return false;
        }
        return true;
    }

    private boolean hostAllowed(String host) {
        List<String> allowed = Arrays.stream(allowedHosts)
                .map(entry -> entry.trim().toLowerCase(Locale.ROOT))
                .filter(entry -> !entry.isEmpty())
                .toList();
        return allowed.stream().anyMatch(entry -> host.equals(entry) || host.endsWith("." + entry));
    }

    /**
     * Literal-address check only. A DNS lookup here would be checking a different answer from the
     * one the connection later uses - the classic DNS-rebinding gap - and would also add a network
     * round trip to every call; the allowlist is what actually pins the destination, and this
     * catches the case of an allowlist entry pointing somewhere it should not.
     */
    private boolean isInternalAddress(String host) {
        if (host.equals("localhost") || host.equals("[::1]") || host.equals("::1")) {
            return true;
        }
        String[] octets = host.split("\\.");
        if (octets.length != 4) {
            return false;
        }
        int[] parts = new int[4];
        for (int i = 0; i < 4; i++) {
            try {
                parts[i] = Integer.parseInt(octets[i]);
            } catch (NumberFormatException e) {
                return false; // a hostname, not a literal address
            }
            if (parts[i] < 0 || parts[i] > 255) {
                return false;
            }
        }
        return parts[0] == 127                                  // loopback
                || parts[0] == 10                               // private
                || parts[0] == 0                                // "this host"
                || (parts[0] == 172 && parts[1] >= 16 && parts[1] <= 31)
                || (parts[0] == 192 && parts[1] == 168)
                || (parts[0] == 169 && parts[1] == 254)         // link-local, incl. cloud metadata
                || (parts[0] == 100 && parts[1] >= 64 && parts[1] <= 127); // carrier-grade NAT
    }

    void setAllowedHosts(String... hosts) {
        this.allowedHosts = hosts;
    }
}

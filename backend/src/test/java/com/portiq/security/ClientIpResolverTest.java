package com.portiq.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    @Test
    void ignoresForwardedHeadersWhenNoProxyIsTrusted() {
        // The default. Honouring the header on a directly exposed server would let a caller mint a
        // fresh identity per request and walk straight through every per-IP limit.
        ClientIpResolver resolver = resolver(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("X-Forwarded-For", "1.1.1.1");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void usesTheForwardedHeaderWhenAProxyIsTrusted() {
        ClientIpResolver resolver = resolver(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.9");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void takesTheLastHopSoPrependedEntriesCannotForgeAnIdentity() {
        // A client can put anything at the front of X-Forwarded-For; only the proxy in front of us
        // appends the address it actually observed.
        ClientIpResolver resolver = resolver(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "9.9.9.9, 8.8.8.8, 203.0.113.9");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void fallsBackToTheSocketAddressWhenTheHeaderIsEmpty() {
        ClientIpResolver resolver = resolver(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("X-Forwarded-For", "  ");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
    }

    private ClientIpResolver resolver(boolean trustProxy) {
        ClientIpResolver resolver = new ClientIpResolver();
        resolver.setTrustProxy(trustProxy);
        return resolver;
    }
}

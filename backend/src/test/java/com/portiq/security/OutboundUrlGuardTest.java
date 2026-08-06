package com.portiq.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class OutboundUrlGuardTest {

    private OutboundUrlGuard guard;

    @BeforeEach
    void setUp() {
        guard = new OutboundUrlGuard(Mockito.mock(SecurityAuditLogger.class));
        guard.setAllowedHosts("finance.yahoo.com", "news.google.com");
    }

    @Test
    void allowsAnAllowlistedHost() {
        assertThat(guard.isAllowed("https://query1.finance.yahoo.com/v8/finance/chart/TCS.NS?range=1y")).isTrue();
        assertThat(guard.isAllowed("https://news.google.com/rss/search?q=stock")).isTrue();
    }

    @Test
    void refusesAHostThatMerelyStartsWithAnAllowedName() {
        // finance.yahoo.com.attacker.net is a domain the attacker controls outright.
        assertThat(guard.isAllowed("https://finance.yahoo.com.attacker.net/chart")).isFalse();
    }

    @Test
    void refusesAHostNotOnTheList() {
        assertThat(guard.isAllowed("https://attacker.example/collect")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://169.254.169.254/latest/meta-data/",  // cloud instance credentials
            "http://127.0.0.1:8080/admin",
            "http://localhost:9200/_cluster/health",
            "http://10.0.0.5/internal",
            "http://192.168.1.1/",
            "http://172.16.4.9/",
            "http://0.0.0.0/"
    })
    void refusesInternalAddressesEvenIfSomehowAllowlisted(String url) {
        guard.setAllowedHosts("169.254.169.254", "127.0.0.1", "localhost", "10.0.0.5",
                "192.168.1.1", "172.16.4.9", "0.0.0.0");

        assertThat(guard.isAllowed(url))
                .as("%s must be refused even when the allowlist names it", url)
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "file:///etc/passwd",
            "gopher://finance.yahoo.com/",
            "jar:https://finance.yahoo.com/a.jar!/"
    })
    void refusesNonHttpSchemes(String url) {
        assertThat(guard.isAllowed(url)).isFalse();
    }

    @Test
    void refusesAMalformedUrl() {
        assertThat(guard.isAllowed("h t t p://finance.yahoo.com")).isFalse();
        assertThat(guard.isAllowed("")).isFalse();
    }

    @Test
    void hostMatchingIgnoresCase() {
        assertThat(guard.isAllowed("https://QUERY1.Finance.Yahoo.COM/v8/finance/chart/X")).isTrue();
    }
}

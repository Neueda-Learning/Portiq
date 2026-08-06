package com.portiq.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RssFeedFetcherLinkTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://finance.yahoo.com/news/story.html",
            "http://news.google.com/articles/abc",
            "  https://finance.yahoo.com/spaced  "
    })
    void keepsOrdinaryHttpLinks(String link) {
        assertThat(RssFeedFetcher.safeLink(link)).isNotNull().doesNotStartWith(" ");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(document.cookie)",
            "JaVaScRiPt:alert(1)",
            "data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==",
            "vbscript:msgbox(1)",
            "file:///etc/passwd"
    })
    void dropsLinksThatWouldExecuteOnClick(String link) {
        // These are rendered into an anchor's href, and React does not sanitise URLs.
        assertThat(RssFeedFetcher.safeLink(link))
                .as("'%s' must never reach an href", link)
                .isNull();
    }

    @Test
    void dropsAMissingLink() {
        assertThat(RssFeedFetcher.safeLink(null)).isNull();
        assertThat(RssFeedFetcher.safeLink("   ")).isNull();
    }
}

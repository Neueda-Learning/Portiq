package com.portiq.service;

import com.portiq.dto.NewsArticle;
import com.portiq.security.OutboundUrlGuard;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Isolated in its own bean so {@code @Cacheable} is applied through the Spring proxy (see
 * PriceLookupService for why). Caches the full parsed feed per URL; callers slice/limit after.
 */
@Service
public class RssFeedFetcher {

    /** A feed of headlines is a few hundred KB at most; anything larger is not a feed. */
    private static final int MAX_FEED_BYTES = 4 * 1024 * 1024;

    private final RestTemplate restTemplate;
    private final OutboundUrlGuard urlGuard;

    public RssFeedFetcher(RestTemplate restTemplate, OutboundUrlGuard urlGuard) {
        this.restTemplate = restTemplate;
        this.urlGuard = urlGuard;
    }

    @Cacheable(value = "news", key = "#url")
    public List<NewsArticle> fetch(String url) {
        List<NewsArticle> results = new ArrayList<>();
        if (!urlGuard.isAllowed(url)) {
            return results;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    URI.create(url), HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
            byte[] body = response.getBody();
            if (body == null || body.length == 0) return results;
            if (body.length > MAX_FEED_BYTES) return results;

            Document document = parseSafely(body);

            NodeList items = document.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                NewsArticle article = new NewsArticle();
                article.setTitle(textOf(item, "title"));
                article.setLink(safeLink(textOf(item, "link")));
                article.setPublishedAt(textOf(item, "pubDate"));
                String source = textOf(item, "source");
                article.setSource(source != null && !source.isBlank() ? source : hostOf(url));
                if (article.getTitle() != null && !article.getTitle().isBlank()) {
                    results.add(article);
                }
            }
        } catch (Exception ignored) {
            // A single feed failing should not break the whole news panel.
        }
        return results;
    }

    /**
     * Parses feed XML with every external-entity avenue closed.
     *
     * <p>Refusing a DOCTYPE was already here and is the strongest single control - no DOCTYPE, no
     * entity declarations, so no XXE. The rest is defence in depth for the case where a future
     * parser or a different JAXP implementation honours the doctype flag differently: external
     * general and parameter entities are disabled outright, XInclude is off (it can fetch a URL
     * without any entity involved), and the parser is told not to expand entity references or
     * dereference DTDs at all.
     *
     * <p>The `secure-processing` flag also caps entity expansion, which is what stops a "billion
     * laughs" style feed from consuming the heap.
     */
    private Document parseSafely(byte[] body) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        DocumentBuilder builder = factory.newDocumentBuilder();
        // A parser with entity resolution disabled should never call out, but an EntityResolver
        // returning empty guarantees it even if one of the flags above is ignored.
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new ByteArrayInputStream(new byte[0])));
        return builder.parse(new ByteArrayInputStream(body));
    }

    /**
     * Drops any headline link that is not plain http(s).
     *
     * <p>These links come from a third-party feed and are rendered straight into an anchor's
     * {@code href}. React escapes text but not URLs, so a feed item pointing at
     * {@code javascript:...} would execute in the user's session the moment they clicked the
     * headline. Nothing legitimate in an RSS feed needs another scheme, so anything else is
     * dropped rather than rewritten.
     */
    static String safeLink(String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        String trimmed = link.trim();
        String lower = trimmed.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://") ? trimmed : null;
    }

    private String textOf(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0) return null;
        return nodes.item(0).getTextContent();
    }

    private String hostOf(String url) {
        return url.contains("google.com") ? "Google News" : "Yahoo Finance";
    }
}

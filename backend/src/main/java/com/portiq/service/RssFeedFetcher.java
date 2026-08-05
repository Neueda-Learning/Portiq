package com.portiq.service;

import com.portiq.dto.NewsArticle;
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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Isolated in its own bean so {@code @Cacheable} is applied through the Spring proxy (see
 * PriceLookupService for why). Caches the full parsed feed per URL; callers slice/limit after.
 */
@Service
public class RssFeedFetcher {

    private final RestTemplate restTemplate = new RestTemplate();

    @Cacheable(value = "news", key = "#url")
    public List<NewsArticle> fetch(String url) {
        List<NewsArticle> results = new ArrayList<>();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            ResponseEntity<byte[]> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
            byte[] body = response.getBody();
            if (body == null || body.length == 0) return results;

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(body));

            NodeList items = document.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                Element item = (Element) items.item(i);
                NewsArticle article = new NewsArticle();
                article.setTitle(textOf(item, "title"));
                article.setLink(textOf(item, "link"));
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

    private String textOf(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0) return null;
        return nodes.item(0).getTextContent();
    }

    private String hostOf(String url) {
        return url.contains("google.com") ? "Google News" : "Yahoo Finance";
    }
}

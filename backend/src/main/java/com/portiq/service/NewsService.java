package com.portiq.service;

import com.portiq.dto.NewsArticle;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Pulls market headlines from free, keyless RSS feeds (Yahoo Finance per-ticker headlines and
 * Google News search) rather than a paid news API. Feed fetching itself is cached in
 * RssFeedFetcher; this class only slices/labels results per caller.
 */
@Service
public class NewsService {

    private final RssFeedFetcher rssFeedFetcher;

    public NewsService(RssFeedFetcher rssFeedFetcher) {
        this.rssFeedFetcher = rssFeedFetcher;
    }

    public List<NewsArticle> getNewsForTickers(List<String> tickers, int limitPerTicker) {
        List<NewsArticle> articles = new ArrayList<>();
        for (String ticker : tickers) {
            String url = "https://feeds.finance.yahoo.com/rss/2.0/headline?s=" + encode(ticker) + "&region=US&lang=en-US";
            List<NewsArticle> feed = rssFeedFetcher.fetch(url);
            feed.stream().limit(limitPerTicker).forEach(article -> {
                NewsArticle copy = new NewsArticle();
                copy.setTitle(article.getTitle());
                copy.setLink(article.getLink());
                copy.setSource(article.getSource());
                copy.setPublishedAt(article.getPublishedAt());
                copy.setRelatedTicker(ticker);
                articles.add(copy);
            });
        }
        return articles;
    }

    public List<NewsArticle> getGeneralMarketNews(int limit) {
        String url = "https://news.google.com/rss/search?q=stock%20market&hl=en-IN&gl=IN&ceid=IN:en";
        List<NewsArticle> feed = rssFeedFetcher.fetch(url);
        return feed.size() > limit ? feed.subList(0, limit) : feed;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

package com.portiq.dto;

public class NewsArticle {

    private String title;
    private String link;
    private String source;
    private String publishedAt;
    private String relatedTicker;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getPublishedAt() { return publishedAt; }
    public void setPublishedAt(String publishedAt) { this.publishedAt = publishedAt; }
    public String getRelatedTicker() { return relatedTicker; }
    public void setRelatedTicker(String relatedTicker) { this.relatedTicker = relatedTicker; }
}

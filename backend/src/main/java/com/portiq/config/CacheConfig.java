package com.portiq.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;
//cache config file
/**
 * Short-lived caches for slow external calls (Yahoo Finance quotes/charts, RSS news feeds) so a
 * dashboard reload doesn't re-fetch every holding's price and every news feed on every request.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                buildCache("prices", 60, TimeUnit.SECONDS, 1000),
                buildCache("news", 10, TimeUnit.MINUTES, 200),
                buildCache("priceHistory", 5, TimeUnit.MINUTES, 500),
                // A year of daily closes barely moves intraday, and the risk/recommendation
                // engines pull one series per holding plus the whole idea universe - so this is
                // the cache that keeps those endpoints responsive. Only the external fetch is
                // cached; the reports themselves are recomputed per request so they never go
                // stale behind a holdings edit.
                buildCache("dailySeries", 30, TimeUnit.MINUTES, 500)
        ));
        return manager;
    }

    private CaffeineCache buildCache(String name, long duration, TimeUnit unit, int maxSize) {
        return new CaffeineCache(name, Caffeine.newBuilder()
                .expireAfterWrite(duration, unit)
                .maximumSize(maxSize)
                .build());
    }
}

package com.shashank.url_shortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Component
@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Cache cache = new Cache();
    private final ClickAggregation clickAggregation = new ClickAggregation();

    @Data
    public static class Cache {
        /** TTL in seconds for cached shortCode → originalURL entries. */
        private long urlTtlSeconds = 3600;

        /**
         * TTL in seconds for negative cache entries (i.e., short codes confirmed
         * not to exist in the database). Kept shorter than the positive TTL so
         * that a URL created later is picked up within this window.
         */
        private long negativeTtlSeconds = 300;
    }

    @Data
    public static class ClickAggregation {
        /** How often (ms) the background job flushes Redis click counters to the DB. */
        private long flushIntervalMs = 10000;
    }

}

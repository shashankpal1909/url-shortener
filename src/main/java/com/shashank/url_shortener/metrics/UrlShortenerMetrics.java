package com.shashank.url_shortener.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Central custom-metrics facade for the URL Shortener application.
 *
 * <p>All counters and timers are registered once at construction time so that
 * Micrometer can publish them to Prometheus / VictoriaMetrics even when their
 * count is still zero (important for alert rules that expect a metric series to
 * exist from the start).</p>
 */
@Slf4j
@Component
public class UrlShortenerMetrics {

    // -------------------------------------------------------------------------
    // URL creation
    // -------------------------------------------------------------------------
    private final MeterRegistry registry;
    private final Counter urlShortenedCounter;
    private final Counter urlShortenErrorCounter;
    private final Timer   urlShortenLatencyTimer;

    // -------------------------------------------------------------------------
    // URL redirect
    // -------------------------------------------------------------------------
    private final Counter urlRedirectFoundCounter;
    private final Counter urlRedirectNotFoundCounter;
    private final Timer   urlRedirectLatencyTimer;

    // -------------------------------------------------------------------------
    // Cache (Redis)
    // -------------------------------------------------------------------------
    private final Counter cacheHitPositiveCounter;
    private final Counter cacheHitNegativeCounter;
    private final Counter cacheMissCounter;
    private final Counter cacheErrorCounter;
    private final Counter cachePopulateCounter;
    private final Counter cacheNegativePopulateCounter;

    // -------------------------------------------------------------------------
    // Click aggregation (async flush)
    // -------------------------------------------------------------------------
    private final Counter clickFlushRunCounter;
    private final Counter clickFlushUrlsCounter;
    private final Counter clickFlushErrorCounter;

    public UrlShortenerMetrics(MeterRegistry registry) {
        this.registry = registry;

        // URL shortening
        urlShortenedCounter = Counter.builder("url.shortened.total")
                .description("Total number of URLs successfully shortened")
                .register(registry);

        urlShortenErrorCounter = Counter.builder("url.shorten.error.total")
                .description("Total number of URL shortening failures")
                .register(registry);

        urlShortenLatencyTimer = Timer.builder("url.shorten.latency")
                .description("Latency of the URL shortening operation")
                .register(registry);

        // Redirects
        urlRedirectFoundCounter = Counter.builder("url.redirect.total")
                .description("Total redirects that resolved a short code")
                .tag("result", "found")
                .register(registry);

        urlRedirectNotFoundCounter = Counter.builder("url.redirect.total")
                .description("Total redirects for an unknown short code")
                .tag("result", "not_found")
                .register(registry);

        urlRedirectLatencyTimer = Timer.builder("url.redirect.latency")
                .description("Latency of the URL redirect lookup (cache + DB)")
                .register(registry);

        // Cache operations
        cacheHitPositiveCounter = Counter.builder("url.cache.operations.total")
                .description("Positive cache hit: short code resolved from Redis without DB query")
                .tag("operation", "hit_positive")
                .register(registry);

        cacheHitNegativeCounter = Counter.builder("url.cache.operations.total")
                .description("Negative cache hit: short code was confirmed absent from Redis sentinel")
                .tag("operation", "hit_negative")
                .register(registry);

        cacheMissCounter = Counter.builder("url.cache.operations.total")
                .description("Cache miss: short code not in Redis, DB query required")
                .tag("operation", "miss")
                .register(registry);

        cacheErrorCounter = Counter.builder("url.cache.operations.total")
                .description("Cache error: Redis operation failed, fell back to DB")
                .tag("operation", "error")
                .register(registry);

        cachePopulateCounter = Counter.builder("url.cache.operations.total")
                .description("Positive cache populate: DB result stored in Redis")
                .tag("operation", "populate_positive")
                .register(registry);

        cacheNegativePopulateCounter = Counter.builder("url.cache.operations.total")
                .description("Negative cache populate: sentinel stored for absent short code")
                .tag("operation", "populate_negative")
                .register(registry);

        // Click aggregation
        clickFlushRunCounter = Counter.builder("url.click.flush.runs.total")
                .description("Total number of click-aggregation flush cycles executed")
                .register(registry);

        clickFlushUrlsCounter = Counter.builder("url.click.flush.urls.total")
                .description("Total number of short-code counters flushed to the database")
                .register(registry);

        clickFlushErrorCounter = Counter.builder("url.click.flush.errors.total")
                .description("Total number of click-aggregation flush failures")
                .register(registry);
    }

    // -------------------------------------------------------------------------
    // URL shortening
    // -------------------------------------------------------------------------

    public void recordUrlShortened() {
        urlShortenedCounter.increment();
    }

    public void recordUrlShortenError() {
        urlShortenErrorCounter.increment();
    }

    public Timer.Sample startShortenTimer() {
        return Timer.start(registry);
    }

    public void stopShortenTimer(Timer.Sample sample) {
        sample.stop(urlShortenLatencyTimer);
    }

    // -------------------------------------------------------------------------
    // Redirects
    // -------------------------------------------------------------------------

    public void recordRedirectFound() {
        urlRedirectFoundCounter.increment();
    }

    public void recordRedirectNotFound() {
        urlRedirectNotFoundCounter.increment();
    }

    public Timer.Sample startRedirectTimer() {
        return Timer.start(registry);
    }

    public void stopRedirectTimer(Timer.Sample sample) {
        sample.stop(urlRedirectLatencyTimer);
    }

    // -------------------------------------------------------------------------
    // Cache
    // -------------------------------------------------------------------------

    public void recordCacheHitPositive() {
        cacheHitPositiveCounter.increment();
    }

    public void recordCacheHitNegative() {
        cacheHitNegativeCounter.increment();
    }

    public void recordCacheMiss() {
        cacheMissCounter.increment();
    }

    public void recordCacheError() {
        cacheErrorCounter.increment();
    }

    public void recordCachePopulate() {
        cachePopulateCounter.increment();
    }

    public void recordCacheNegativePopulate() {
        cacheNegativePopulateCounter.increment();
    }

    // -------------------------------------------------------------------------
    // Click aggregation
    // -------------------------------------------------------------------------

    public void recordClickFlushRun() {
        clickFlushRunCounter.increment();
    }

    public void recordClickFlushUrls(int count) {
        clickFlushUrlsCounter.increment(count);
    }

    public void recordClickFlushError() {
        clickFlushErrorCounter.increment();
    }
}

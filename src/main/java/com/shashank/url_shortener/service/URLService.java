package com.shashank.url_shortener.service;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shashank.url_shortener.config.AppProperties;
import com.shashank.url_shortener.config.FeatureProperties;
import com.shashank.url_shortener.dto.ShortenRequest;
import com.shashank.url_shortener.dto.ShortenResponse;
import com.shashank.url_shortener.dto.StatsResponse;
import com.shashank.url_shortener.entity.URL;
import com.shashank.url_shortener.repository.URLRepository;
import com.shashank.url_shortener.util.ShortCodeGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class URLService {

    static final String CACHE_KEY_PREFIX = "url:";
    static final String CLICK_KEY_PREFIX = "clicks:";
    /** Sentinel stored in Redis to indicate that a short code does not exist (negative cache). */
    static final String NEGATIVE_CACHE_SENTINEL = "\u0000";

    private static final int MAX_GENERATION_ATTEMPTS = 10;

    private final URLRepository urlRepository;
    private final FeatureProperties featureProperties;
    private final AppProperties appProperties;

    /** Injected only when Redis auto-configuration is active (not in tests). */
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Transactional
    public ShortenResponse shortenURL(ShortenRequest request) {
        URL savedUrl = null;

        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String shortCode = ShortCodeGenerator.generateShortCode();
            if (urlRepository.existsByShortCode(shortCode)) {
                continue;
            }

            URL url = new URL();
            url.setOriginalURL(request.getUrl());
            url.setShortCode(shortCode);

            try {
                savedUrl = urlRepository.save(url);
                break;
            } catch (DataIntegrityViolationException exception) {
            }
        }

        if (savedUrl == null) {
            throw new IllegalStateException("Unable to generate unique short code after multiple attempts");
        }

        ShortenResponse response = new ShortenResponse();
        String baseURL = System.getenv("BASE_URL");
        if (baseURL == null) {
            baseURL = "http://localhost:8080";
        }

        response.setShortURL(baseURL + "/" + savedUrl.getShortCode());
        response.setShortCode(savedUrl.getShortCode());

        return response;
    }

    /**
     * Phase 1: Cache-first lookup with negative-cache support.
     *
     * <ul>
     *   <li>Positive cache hit — returns the cached original URL immediately.</li>
     *   <li>Negative cache hit — the sentinel {@value #NEGATIVE_CACHE_SENTINEL}
     *       is stored in Redis when a short code was previously confirmed absent;
     *       returns {@link Optional#empty()} without touching the DB.</li>
     *   <li>Cache miss — queries the DB. On a DB hit the original URL is cached
     *       with the positive TTL. On a DB miss the sentinel is cached with the
     *       (shorter) negative TTL so subsequent requests for the same absent
     *       code do not reach the database.</li>
     * </ul>
     */
    public Optional<String> getOriginalURL(String shortCode) {
        if (featureProperties.isCacheEnabled() && redisTemplate != null) {
            String cacheKey = CACHE_KEY_PREFIX + shortCode;
            try {
                String cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    if (NEGATIVE_CACHE_SENTINEL.equals(cached)) {
                        log.debug("Negative cache hit for shortCode={}", shortCode);
                        return Optional.empty();
                    }
                    return Optional.of(cached);
                }
                log.debug("Cache miss for shortCode={}", shortCode);
            } catch (DataAccessException ex) {
                log.warn("Redis unavailable during cache lookup for shortCode={}; falling back to DB", shortCode, ex);
            }
        }

        Optional<String> result = urlRepository.findByShortCode(shortCode)
                .map(URL::getOriginalURL);

        if (featureProperties.isCacheEnabled() && redisTemplate != null) {
            String cacheKey = CACHE_KEY_PREFIX + shortCode;
            if (result.isPresent()) {
                long ttl = appProperties.getCache().getUrlTtlSeconds();
                try {
                    redisTemplate.opsForValue().set(cacheKey, result.get(), Duration.ofSeconds(ttl));
                } catch (DataAccessException ex) {
                    log.warn("Redis unavailable when populating cache for shortCode={}", shortCode, ex);
                }
            } else {
                // Negative cache: store sentinel so repeated misses skip the DB.
                long negativeTtl = appProperties.getCache().getNegativeTtlSeconds();
                try {
                    redisTemplate.opsForValue().set(cacheKey, NEGATIVE_CACHE_SENTINEL, Duration.ofSeconds(negativeTtl));
                    log.debug("Stored negative cache entry for shortCode={} (TTL={}s)", shortCode, negativeTtl);
                } catch (DataAccessException ex) {
                    log.warn("Redis unavailable when storing negative cache for shortCode={}", shortCode, ex);
                }
            }
        }

        return result;
    }

    /**
     * Phase 0 / Phase 2: Atomic click increment.
     * <ul>
     *   <li>Phase 2 (async-clicks-enabled): Issues a Redis INCR so the redirect
     *       path never touches the DB. The background ClickAggregationService
     *       periodically flushes counters to PostgreSQL.</li>
     *   <li>Phase 0 (fallback): Executes a single atomic UPDATE statement instead
     *       of the previous read-modify-write, eliminating race conditions.</li>
     * </ul>
     */
    @Transactional
    public void incrementClickCount(String shortCode) {
        if (featureProperties.isAsyncClicksEnabled() && redisTemplate != null) {
            try {
                redisTemplate.opsForValue().increment(CLICK_KEY_PREFIX + shortCode);
                return;
            } catch (DataAccessException ex) {
                // Redis is optional on the redirect path; fall back to Phase 0 DB update.
                log.warn("Failed to increment Redis click counter for shortCode={}. Falling back to DB update.", shortCode, ex);
            }
        }

        // Phase 0: atomic single-statement DB update (no read-modify-write).
        int updated = urlRepository.incrementClickCount(shortCode);
        if (updated == 0) {
            throw new RuntimeException("URL not found for short code: " + shortCode);
        }
    }

    public Optional<StatsResponse> getURLStats(String shortCode) {
        return urlRepository.findByShortCode(shortCode)
                .map(url -> {
                    StatsResponse stats = new StatsResponse();
                    stats.setShortCode(url.getShortCode());
                    stats.setOriginalURL(url.getOriginalURL());
                    stats.setClickCount(url.getClickCount());
                    stats.setCreatedAt(url.getCreatedAt());
                    return stats;
                });
    }

}

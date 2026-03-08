package com.shashank.url_shortener.service;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
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
     * Phase 1: Cache-first lookup.
     * Checks Redis for a cached originalURL; on miss loads from DB and populates
     * the cache with a TTL so subsequent requests avoid DB reads.
     */
    public Optional<String> getOriginalURL(String shortCode) {
        if (featureProperties.isCacheEnabled() && redisTemplate != null) {
            String cacheKey = CACHE_KEY_PREFIX + shortCode;
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return Optional.of(cached);
            }
            log.debug("Cache miss for shortCode={}", shortCode);
        }

        Optional<String> result = urlRepository.findByShortCode(shortCode)
                .map(URL::getOriginalURL);

        if (featureProperties.isCacheEnabled() && redisTemplate != null) {
            result.ifPresent(url -> {
                String cacheKey = CACHE_KEY_PREFIX + shortCode;
                long ttl = appProperties.getCache().getUrlTtlSeconds();
                redisTemplate.opsForValue().set(cacheKey, url, Duration.ofSeconds(ttl));
            });
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
            redisTemplate.opsForValue().increment(CLICK_KEY_PREFIX + shortCode);
            return;
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

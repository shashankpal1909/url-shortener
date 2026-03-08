package com.shashank.url_shortener.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shashank.url_shortener.repository.URLRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Phase 2 — Async click aggregation.
 *
 * <p>When {@code feature.async-clicks-enabled=true} each redirect issues a cheap
 * Redis {@code INCR clicks:{shortCode}} instead of a synchronous DB write.
 * This service runs a background job at a configurable interval, atomically
 * drains each counter and applies batched increments to PostgreSQL, so click
 * counts converge within the configured flush window.</p>
 *
 * <p>The service is only instantiated when
 * {@code feature.async-clicks-enabled=true}, so it has no effect when the
 * feature flag is off.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "feature.async-clicks-enabled", havingValue = "true")
public class ClickAggregationService {

    private final StringRedisTemplate redisTemplate;
    private final URLRepository urlRepository;

    /**
     * Periodically drains Redis click counters and applies them to the database.
     *
     * <ol>
     *   <li>Uses a cursor-based {@code SCAN} (non-blocking) to find all
     *       {@code clicks:*} keys.</li>
     *   <li>Issues pipelined {@code GETDEL} commands to atomically drain all
     *       counters in a single round-trip.</li>
     *   <li>Applies the retrieved increments to PostgreSQL via batch atomic
     *       {@code UPDATE} statements.</li>
     * </ol>
     *
     * <p>Any new increments that arrive after the {@code GETDEL} are captured
     * in subsequent flush cycles — no clicks are lost across cycle boundaries.</p>
     */
    @Scheduled(fixedDelayString = "${app.click-aggregation.flush-interval-ms:10000}")
    @Transactional
    public void flushClickCounts() {
        // Step 1: Cursor-based SCAN — does not block the Redis server.
        List<String> keys = scanClickKeys();
        if (keys.isEmpty()) {
            return;
        }

        log.debug("Flushing click counts for {} short codes", keys.size());

        // Step 2: Pipelined GETDEL — all keys drained in a single round-trip.
        List<Object> rawValues = redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                for (String key : keys) {
                    connection.stringCommands().getDel(key.getBytes(StandardCharsets.UTF_8));
                }
                return null;
            }
        });

        // Step 3: Apply increments to the database.
        for (int i = 0; i < keys.size(); i++) {
            Object raw = rawValues.get(i);
            if (raw == null) {
                continue;
            }
            long increment;
            try {
                increment = Long.parseLong(raw.toString());
            } catch (NumberFormatException e) {
                log.warn("Unexpected non-numeric click counter for key={}: {}", keys.get(i), raw);
                continue;
            }
            if (increment <= 0) {
                continue;
            }
            String shortCode = keys.get(i).substring(URLService.CLICK_KEY_PREFIX.length());
            int updated = urlRepository.incrementClickCountBy(shortCode, increment);
            if (updated == 0) {
                log.warn("No URL found when flushing click count for shortCode={}", shortCode);
            } else {
                log.debug("Flushed {} clicks for shortCode={}", increment, shortCode);
            }
        }
    }

    private List<String> scanClickKeys() {
        Set<String> result = redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
            Set<String> keys = new LinkedHashSet<>();
            ScanOptions options = ScanOptions.scanOptions()
                    .match(URLService.CLICK_KEY_PREFIX + "*")
                    .count(100)
                    .build();
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                cursor.forEachRemaining(
                        key -> keys.add(new String(key, StandardCharsets.UTF_8)));
            }
            return keys;
        });
        return result != null ? new ArrayList<>(result) : new ArrayList<>();
    }

}

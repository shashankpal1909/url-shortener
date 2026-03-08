package com.shashank.url_shortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import com.shashank.url_shortener.config.AppProperties;
import com.shashank.url_shortener.config.FeatureProperties;
import com.shashank.url_shortener.entity.URL;
import com.shashank.url_shortener.repository.URLRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class URLServiceTest {

    @Mock
    private URLRepository urlRepository;

    @Mock
    private FeatureProperties featureProperties;

    @Mock
    private AppProperties appProperties;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private URLService urlService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(urlService, "redisTemplate", redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        AppProperties.Cache cacheProps = new AppProperties.Cache();
        when(appProperties.getCache()).thenReturn(cacheProps);
    }

    // --- Phase 0: atomic click increment ---

    @Test
    void incrementClickCount_phase0_atomicDbUpdate() {
        when(featureProperties.isAsyncClicksEnabled()).thenReturn(false);
        when(urlRepository.incrementClickCount("abc123")).thenReturn(1);

        urlService.incrementClickCount("abc123");

        verify(urlRepository).incrementClickCount("abc123");
        verify(valueOps, never()).increment(anyString());
    }

    @Test
    void incrementClickCount_phase0_throwsWhenShortCodeNotFound() {
        when(featureProperties.isAsyncClicksEnabled()).thenReturn(false);
        when(urlRepository.incrementClickCount("missing")).thenReturn(0);

        assertThatThrownBy(() -> urlService.incrementClickCount("missing"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("missing");
    }

    // --- Phase 2: async Redis click tracking ---

    @Test
    void incrementClickCount_phase2_usesRedisIncr() {
        when(featureProperties.isAsyncClicksEnabled()).thenReturn(true);

        urlService.incrementClickCount("abc123");

        verify(valueOps).increment(URLService.CLICK_KEY_PREFIX + "abc123");
        verify(urlRepository, never()).incrementClickCount(anyString());
    }

    // --- Phase 1: cache-first URL lookup ---

    @Test
    void getOriginalURL_cacheEnabled_returnsCachedValueWithoutDbHit() {
        when(featureProperties.isCacheEnabled()).thenReturn(true);
        when(valueOps.get(URLService.CACHE_KEY_PREFIX + "abc123"))
                .thenReturn("https://example.com");

        Optional<String> result = urlService.getOriginalURL("abc123");

        assertThat(result).contains("https://example.com");
        verify(urlRepository, never()).findByShortCode(anyString());
    }

    @Test
    void getOriginalURL_cacheEnabled_onMissLoadsDbAndPopulatesCache() {
        when(featureProperties.isCacheEnabled()).thenReturn(true);
        when(valueOps.get(URLService.CACHE_KEY_PREFIX + "abc123")).thenReturn(null);

        URL entity = new URL();
        entity.setShortCode("abc123");
        entity.setOriginalURL("https://example.com");
        when(urlRepository.findByShortCode("abc123")).thenReturn(Optional.of(entity));

        Optional<String> result = urlService.getOriginalURL("abc123");

        assertThat(result).contains("https://example.com");
        verify(valueOps).set(eq(URLService.CACHE_KEY_PREFIX + "abc123"),
                eq("https://example.com"), any());
    }

    @Test
    void getOriginalURL_cacheDisabled_goesDirectlyToDb() {
        when(featureProperties.isCacheEnabled()).thenReturn(false);

        URL entity = new URL();
        entity.setShortCode("abc123");
        entity.setOriginalURL("https://example.com");
        when(urlRepository.findByShortCode("abc123")).thenReturn(Optional.of(entity));

        Optional<String> result = urlService.getOriginalURL("abc123");

        assertThat(result).contains("https://example.com");
        verify(valueOps, never()).get(anyString());
        verify(valueOps, never()).set(anyString(), anyString(), any());
    }

    @Test
    void getOriginalURL_cacheEnabled_missingShortCodeReturnsEmpty() {
        when(featureProperties.isCacheEnabled()).thenReturn(true);
        when(valueOps.get(URLService.CACHE_KEY_PREFIX + "nope")).thenReturn(null);
        when(urlRepository.findByShortCode("nope")).thenReturn(Optional.empty());

        Optional<String> result = urlService.getOriginalURL("nope");

        assertThat(result).isEmpty();
        verify(valueOps, never()).set(anyString(), anyString(), any());
    }

    // --- ClickAggregationService flush ---

    @Test
    void clickAggregationService_flushesCountersToDb() {
        ClickAggregationService aggregationService =
                new ClickAggregationService(redisTemplate, urlRepository);

        when(redisTemplate.execute(ArgumentMatchers.<RedisCallback<Set<String>>>any()))
                .thenReturn(java.util.Set.of(URLService.CLICK_KEY_PREFIX + "abc123"));
        when(redisTemplate.executePipelined(any(org.springframework.data.redis.core.RedisCallback.class)))
                .thenReturn(java.util.List.of("42"));
        when(urlRepository.incrementClickCountBy("abc123", 42L)).thenReturn(1);

        aggregationService.flushClickCounts();

        verify(urlRepository).incrementClickCountBy("abc123", 42L);
    }

    @Test
    void clickAggregationService_skipsWhenNoKeysPresent() {
        ClickAggregationService aggregationService =
                new ClickAggregationService(redisTemplate, urlRepository);

        when(redisTemplate.execute(ArgumentMatchers.<RedisCallback<Set<String>>>any()))
                .thenReturn(java.util.Set.of());

        aggregationService.flushClickCounts();

        verify(urlRepository, never()).incrementClickCountBy(anyString(), anyLong());
    }
}

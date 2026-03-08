package com.shashank.url_shortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shashank.url_shortener.config.AppProperties;
import com.shashank.url_shortener.config.FeatureProperties;
import com.shashank.url_shortener.dto.ShortenRequest;
import com.shashank.url_shortener.dto.ShortenResponse;
import com.shashank.url_shortener.dto.StatsResponse;
import com.shashank.url_shortener.entity.URL;
import com.shashank.url_shortener.repository.URLRepository;
import com.shashank.url_shortener.service.URLService;

@ExtendWith(MockitoExtension.class)
class URLServiceTests {

    @Mock
    private URLRepository urlRepository;

    @Mock
    private FeatureProperties featureProperties;

    @Mock
    private AppProperties appProperties;

    @InjectMocks
    private URLService urlService;

    @Test
    void shortenURLReturnsShortCodeAndPersists() {
        ShortenRequest request = new ShortenRequest();
        request.setUrl("https://example.com/path");

        when(urlRepository.existsByShortCode(any())).thenReturn(false);
        when(urlRepository.save(any(URL.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShortenResponse response = urlService.shortenURL(request);

        assertThat(response.getShortCode()).isNotBlank();
        assertThat(response.getShortCode().length()).isBetween(5, 7);
        assertThat(response.getShortURL()).isEqualTo("http://localhost:8080/" + response.getShortCode());

        ArgumentCaptor<URL> urlCaptor = ArgumentCaptor.forClass(URL.class);
        verify(urlRepository).save(urlCaptor.capture());
        assertThat(urlCaptor.getValue().getOriginalURL()).isEqualTo("https://example.com/path");
    }

    @Test
    void shortenURLThrowsWhenNoUniqueCodeFound() {
        ShortenRequest request = new ShortenRequest();
        request.setUrl("https://example.com/path");

        when(urlRepository.existsByShortCode(any())).thenReturn(true);

        assertThatThrownBy(() -> urlService.shortenURL(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to generate unique short code");

        verify(urlRepository, never()).save(any(URL.class));
    }

    @Test
    void getOriginalURLReturnsMappedValue() {
        URL url = new URL();
        url.setOriginalURL("https://example.org");

        when(urlRepository.findByShortCode("code1")).thenReturn(Optional.of(url));

        Optional<String> originalURL = urlService.getOriginalURL("code1");

        assertThat(originalURL).contains("https://example.org");
    }

    @Test
    void incrementClickCountUpdatesAndSaves() {
        when(urlRepository.incrementClickCount("aZ91k")).thenReturn(1);

        urlService.incrementClickCount("aZ91k");

        verify(urlRepository).incrementClickCount("aZ91k");
    }

    @Test
    void getURLStatsMapsFields() {
        URL url = new URL();
        url.setShortCode("B7xD2");
        url.setOriginalURL("https://example.net");
        url.setClickCount(42L);
        url.setCreatedAt(LocalDateTime.of(2026, 3, 8, 10, 15, 30));

        when(urlRepository.findByShortCode("B7xD2")).thenReturn(Optional.of(url));

        Optional<StatsResponse> statsResponse = urlService.getURLStats("B7xD2");

        assertThat(statsResponse).isPresent();
        assertThat(statsResponse.get().getShortCode()).isEqualTo("B7xD2");
        assertThat(statsResponse.get().getOriginalURL()).isEqualTo("https://example.net");
        assertThat(statsResponse.get().getClickCount()).isEqualTo(42L);
        assertThat(statsResponse.get().getCreatedAt()).isEqualTo(LocalDateTime.of(2026, 3, 8, 10, 15, 30));
    }
}

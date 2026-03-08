package com.shashank.url_shortener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.shashank.url_shortener.controller.URLController;
import com.shashank.url_shortener.dto.ShortenRequest;
import com.shashank.url_shortener.dto.ShortenResponse;
import com.shashank.url_shortener.dto.StatsResponse;
import com.shashank.url_shortener.service.URLService;

@ExtendWith(MockitoExtension.class)
class URLControllerTests {

    @Mock
    private URLService urlService;

    @InjectMocks
    private URLController urlController;

    @Test
    void getOriginalURLReturns302AndIncrementsCountWhenFound() {
        when(urlService.getOriginalURL("aZ91k")).thenReturn(Optional.of("https://example.com"));

        ResponseEntity<Void> response = urlController.getOriginalURL("aZ91k");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation()).hasToString("https://example.com");
        verify(urlService).incrementClickCount("aZ91k");
    }

    @Test
    void getOriginalURLReturns404WhenNotFound() {
        when(urlService.getOriginalURL("missing")).thenReturn(Optional.empty());

        ResponseEntity<Void> response = urlController.getOriginalURL("missing");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(urlService, never()).incrementClickCount("missing");
    }

    @Test
    void shortenURLDelegatesToService() {
        ShortenRequest request = new ShortenRequest();
        request.setUrl("https://example.com");

        ShortenResponse serviceResponse = new ShortenResponse();
        serviceResponse.setShortCode("abc12");
        serviceResponse.setShortURL("http://localhost:8080/abc12");
        when(urlService.shortenURL(request)).thenReturn(serviceResponse);

        ResponseEntity<ShortenResponse> response = urlController.shortenURL(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(serviceResponse);
    }

    @Test
    void getURLStatsReturns200WhenFound() {
        StatsResponse stats = new StatsResponse();
        stats.setShortCode("B7xD2");
        when(urlService.getURLStats("B7xD2")).thenReturn(Optional.of(stats));

        ResponseEntity<StatsResponse> response = urlController.getURLStats("B7xD2");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(stats);
    }

    @Test
    void getURLStatsReturns404WhenMissing() {
        when(urlService.getURLStats("missing")).thenReturn(Optional.empty());

        ResponseEntity<StatsResponse> response = urlController.getURLStats("missing");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}

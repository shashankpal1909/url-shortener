package com.shashank.url_shortener.controller;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.shashank.url_shortener.dto.ShortenRequest;
import com.shashank.url_shortener.dto.ShortenResponse;
import com.shashank.url_shortener.dto.StatsResponse;
import com.shashank.url_shortener.metrics.UrlShortenerMetrics;
import com.shashank.url_shortener.service.URLService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@RestController
public class URLController {

    private final URLService urlService;
    private final UrlShortenerMetrics metrics;

    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> getOriginalURL(@PathVariable String shortCode) {
        return urlService.getOriginalURL(shortCode)
                .<ResponseEntity<Void>>map(
                        url -> {
                            urlService.incrementClickCount(shortCode);
                            metrics.recordRedirectFound();
                            return ResponseEntity.status(
                                    HttpStatus.FOUND).location(URI.create(url)).build();
                        })
                .orElseGet(() -> {
                    metrics.recordRedirectNotFound();
                    return ResponseEntity.notFound().build();
                });
    }

    @PostMapping("/shorten")
    public ResponseEntity<ShortenResponse> shortenURL(@RequestBody @Valid ShortenRequest request) {
        ShortenResponse response = urlService.shortenURL(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats/{shortCode}")
    public ResponseEntity<StatsResponse> getURLStats(@PathVariable String shortCode) {
        return urlService.getURLStats(shortCode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

}

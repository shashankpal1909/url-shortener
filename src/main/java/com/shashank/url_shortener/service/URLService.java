package com.shashank.url_shortener.service;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shashank.url_shortener.dto.ShortenRequest;
import com.shashank.url_shortener.dto.ShortenResponse;
import com.shashank.url_shortener.dto.StatsResponse;
import com.shashank.url_shortener.entity.URL;
import com.shashank.url_shortener.repository.URLRepository;
import com.shashank.url_shortener.util.ShortCodeGenerator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class URLService {

    private static final int MAX_GENERATION_ATTEMPTS = 10;

    private final URLRepository urlRepository;
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

    public Optional<String> getOriginalURL(String shortCode) {
        return urlRepository.findByShortCode(shortCode)
                .map(URL::getOriginalURL);
    }

    public void incrementClickCount(String shortCode) {
        URL url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new RuntimeException("URL not found for short code: " + shortCode));
        url.setClickCount(url.getClickCount() + 1);
        urlRepository.save(url);
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

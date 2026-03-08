package com.shashank.url_shortener.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class StatsResponse {

    private String shortCode;
    private String originalURL;
    private Long clickCount;
    private LocalDateTime createdAt;

}

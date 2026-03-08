package com.shashank.url_shortener.dto;

import lombok.Data;

@Data
public class ShortenResponse {

    private String shortCode;
    private String shortURL;

}

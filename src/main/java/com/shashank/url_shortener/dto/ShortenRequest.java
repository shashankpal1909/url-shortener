package com.shashank.url_shortener.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Data
public class ShortenRequest {

    @NotBlank(message = "URL cannot be blank")
    @Pattern(regexp = "^https?://.*", message = "URL must start with http:// or https://")
    private String url;

}

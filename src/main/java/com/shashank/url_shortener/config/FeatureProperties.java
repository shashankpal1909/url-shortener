package com.shashank.url_shortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Component
@Data
@ConfigurationProperties(prefix = "feature")
public class FeatureProperties {

    /** Enable Redis read-through cache for shortCode → originalURL lookups. */
    private boolean cacheEnabled = false;

    /** Enable async Redis-based click tracking with periodic DB flush. */
    private boolean asyncClicksEnabled = false;

}

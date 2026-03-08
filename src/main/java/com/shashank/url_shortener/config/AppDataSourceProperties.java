package com.shashank.url_shortener.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Component
@Data
@ConfigurationProperties(prefix = "app.datasource")
public class AppDataSourceProperties {

    private final Pool pool = new Pool();
    private final PreparedStatements preparedStatements = new PreparedStatements();

    @Data
    public static class Pool {
        private int maximumSize = 20;
        private int minimumIdle = 5;
        private long connectionTimeoutMs = 30000;
        private long idleTimeoutMs = 600000;
        private long maxLifetimeMs = 1800000;
        private boolean autoCommit = true;
        private String connectionTestQuery = "SELECT 1";
        private String name = "URLShortenerHikariPool";
    }

    @Data
    public static class PreparedStatements {
        private boolean cache = true;
        private int cacheSize = 250;
        private int sqlLimit = 2048;
        private boolean useServerPreparedStatements = true;
    }
}

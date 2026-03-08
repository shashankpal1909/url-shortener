package com.shashank.url_shortener.config;

import javax.sql.DataSource;

import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import lombok.RequiredArgsConstructor;

@Configuration
@Profile("!test")
@EnableTransactionManagement
@RequiredArgsConstructor
public class DatabaseConfig {

    private final DataSourceProperties dataSourceProperties;
    private final AppDataSourceProperties appDataSourceProperties;

    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();

        AppDataSourceProperties.Pool pool = appDataSourceProperties.getPool();
        AppDataSourceProperties.PreparedStatements preparedStatements = appDataSourceProperties.getPreparedStatements();

        config.setJdbcUrl(dataSourceProperties.getUrl());
        config.setUsername(dataSourceProperties.getUsername());
        config.setPassword(dataSourceProperties.getPassword());
        config.setDriverClassName("org.postgresql.Driver");

        config.setMaximumPoolSize(pool.getMaximumSize());
        config.setMinimumIdle(pool.getMinimumIdle());
        config.setConnectionTimeout(pool.getConnectionTimeoutMs());
        config.setIdleTimeout(pool.getIdleTimeoutMs());
        config.setMaxLifetime(pool.getMaxLifetimeMs());

        config.setAutoCommit(pool.isAutoCommit());
        config.setConnectionTestQuery(pool.getConnectionTestQuery());
        config.setPoolName(pool.getName());

        config.addDataSourceProperty("cachePrepStmts", String.valueOf(preparedStatements.isCache()));
        config.addDataSourceProperty("prepStmtCacheSize", String.valueOf(preparedStatements.getCacheSize()));
        config.addDataSourceProperty("prepStmtCacheSqlLimit", String.valueOf(preparedStatements.getSqlLimit()));
        config.addDataSourceProperty(
                "useServerPrepStmts",
                String.valueOf(preparedStatements.isUseServerPreparedStatements()));

        return new HikariDataSource(config);
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}

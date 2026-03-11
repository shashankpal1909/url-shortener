package com.shashank.url_shortener.simulation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configurable parameters for traffic simulation scenarios.
 *
 * <p>Use {@link TrafficSimulationConfig#builder()} to create custom configurations
 * for different load testing scenarios.</p>
 *
 * <p>Example:</p>
 * <pre>{@code
 * TrafficSimulationConfig config = TrafficSimulationConfig.builder()
 *     .threadCount(50)
 *     .durationSeconds(60)
 *     .requestsPerSecond(1000)
 *     .createRatio(0.1)
 *     .redirectRatio(0.8)
 *     .statsRatio(0.1)
 *     .enableMetrics(true)
 *     .build();
 * }</pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrafficSimulationConfig {

    /** Number of concurrent worker threads. Default: 10 */
    @Builder.Default
    private int threadCount = 10;

    /** Duration of simulation in seconds. Default: 30 */
    @Builder.Default
    private int durationSeconds = 30;

    /** Target requests per second (approximate). Default: 100 */
    @Builder.Default
    private int requestsPerSecond = 100;

    /** Percentage of requests that create new URLs (0.0 - 1.0). Default: 0.1 (10%) */
    @Builder.Default
    private double createRatio = 0.1;

    /** Percentage of requests that follow redirects (0.0 - 1.0). Default: 0.8 (80%) */
    @Builder.Default
    private double redirectRatio = 0.8;

    /** Percentage of requests that fetch stats (0.0 - 1.0). Default: 0.1 (10%) */
    @Builder.Default
    private double statsRatio = 0.1;

    /** Base URL for API calls. Default: localhost:8080 */
    @Builder.Default
    private String baseUrl = "http://localhost:8080";

    /** Enable detailed metrics collection. Default: true */
    @Builder.Default
    private boolean enableMetrics = true;

    /** Enable verbose console output. Default: false */
    @Builder.Default
    private boolean verbose = false;

    /** Percentage of requests using cached URLs (for redirects). Default: 0.7 (70%) */
    @Builder.Default
    private double cacheHitRatio = 0.7;

    /** Ramp-up time in seconds (gradually increase load). Default: 0 (no ramp-up) */
    @Builder.Default
    private int rampUpSeconds = 0;

    /** Think time between requests per thread in milliseconds. Default: 10 */
    @Builder.Default
    private int thinkTimeMs = 10;

    /** Enable connection pooling. Default: true */
    @Builder.Default
    private boolean useConnectionPool = true;

    /** Connection pool size. Default: 50 */
    @Builder.Default
    private int poolSize = 50;

    /**
     * Validation method to ensure ratios sum to approximately 1.0
     *
     * @throws IllegalArgumentException if ratios don't sum to ~1.0 (±0.01)
     */
    public void validate() {
        double total = createRatio + redirectRatio + statsRatio;
        if (Math.abs(total - 1.0) > 0.01) {
            throw new IllegalArgumentException(
                    "Traffic ratios must sum to 1.0 (create=" + createRatio +
                    ", redirect=" + redirectRatio +
                    ", stats=" + statsRatio +
                    ", total=" + total + ")");
        }

        if (threadCount <= 0) {
            throw new IllegalArgumentException("threadCount must be > 0");
        }

        if (durationSeconds <= 0) {
            throw new IllegalArgumentException("durationSeconds must be > 0");
        }

        if (requestsPerSecond <= 0) {
            throw new IllegalArgumentException("requestsPerSecond must be > 0");
        }

        if (cacheHitRatio < 0 || cacheHitRatio > 1.0) {
            throw new IllegalArgumentException("cacheHitRatio must be between 0.0 and 1.0");
        }
    }

    /**
     * Get the delay between requests in milliseconds to achieve target RPS.
     *
     * @return delay in ms
     */
    public long getRequestDelayMs() {
        return 1000L / ((long) requestsPerSecond / threadCount);
    }

    @Override
    public String toString() {
        return "TrafficSimulationConfig{" +
                "threads=" + threadCount +
                ", duration=" + durationSeconds + "s" +
                ", rps=" + requestsPerSecond +
                ", create=" + (int)(createRatio * 100) + "%" +
                ", redirect=" + (int)(redirectRatio * 100) + "%" +
                ", stats=" + (int)(statsRatio * 100) + "%" +
                ", cacheHit=" + (int)(cacheHitRatio * 100) + "%" +
                ", rampUp=" + rampUpSeconds + "s" +
                ", pool=" + (useConnectionPool ? poolSize : "disabled") +
                "}";
    }
}

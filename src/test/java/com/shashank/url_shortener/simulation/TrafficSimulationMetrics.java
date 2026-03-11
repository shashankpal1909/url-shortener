package com.shashank.url_shortener.simulation;

import lombok.Getter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metrics collector for traffic simulation runs.
 *
 * <p>Tracks request counts, latencies, errors, and generates performance reports.</p>
 */
@Getter
public class TrafficSimulationMetrics {

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    private final AtomicLong totalLatencyMs = new AtomicLong(0);
    private final AtomicLong minLatencyMs = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxLatencyMs = new AtomicLong(0);

    private final Map<String, AtomicLong> requestTypeCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> errorCounts = new ConcurrentHashMap<>();
    private final List<Long> allLatencies = Collections.synchronizedList(new ArrayList<>());

    private long durationMs;
    private int totalCreatedUrls;
    private int cacheSize;

    /**
     * Record a successful request of a given type.
     *
     * @param type request type (create, redirect, stats, notfound)
     */
    public void recordSuccess(String type) {
        totalRequests.incrementAndGet();
        successfulRequests.incrementAndGet();
        requestTypeCounts.computeIfAbsent(type, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Record a failed request.
     *
     * @param errorType error type
     */
    public void recordError(String errorType) {
        totalRequests.incrementAndGet();
        failedRequests.incrementAndGet();
        errorCounts.computeIfAbsent(errorType, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Record latency of a request.
     *
     * @param latencyMs latency in milliseconds
     */
    public void recordLatency(long latencyMs) {
        totalLatencyMs.addAndGet(latencyMs);
        allLatencies.add(latencyMs);

        long current;
        while ((current = minLatencyMs.get()) > latencyMs) {
            minLatencyMs.compareAndSet(current, latencyMs);
        }

        while ((current = maxLatencyMs.get()) < latencyMs) {
            maxLatencyMs.compareAndSet(current, latencyMs);
        }
    }

    /**
     * Get average latency in milliseconds.
     *
     * @return average latency
     */
    public long getAverageLatencyMs() {
        if (totalRequests.get() == 0) return 0;
        return totalLatencyMs.get() / totalRequests.get();
    }

    /**
     * Get minimum latency in milliseconds.
     *
     * @return minimum latency
     */
    public long getMinLatencyMs() {
        long min = minLatencyMs.get();
        return min == Long.MAX_VALUE ? 0 : min;
    }

    /**
     * Get maximum latency in milliseconds.
     *
     * @return maximum latency
     */
    public long getMaxLatencyMs() {
        return maxLatencyMs.get();
    }

    /**
     * Get percentile latency.
     *
     * @param percentile percentile (0-100)
     * @return latency at percentile
     */
    public long getPercentileLatency(double percentile) {
        if (allLatencies.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(allLatencies);
        Collections.sort(sorted);
        int index = (int) (sorted.size() * percentile / 100.0);
        return sorted.get(Math.min(index, sorted.size() - 1));
    }

    /**
     * Get requests per second.
     *
     * @return RPS
     */
    public double getRequestsPerSecond() {
        if (durationMs == 0) return 0;
        return (totalRequests.get() * 1000.0) / durationMs;
    }

    /**
     * Get success rate percentage.
     *
     * @return success rate 0-100
     */
    public double getSuccessRatePercent() {
        if (totalRequests.get() == 0) return 0;
        return (successfulRequests.get() * 100.0) / totalRequests.get();
    }
    /**
     * Set the duration of the simulation in milliseconds.
     *
     * @param durationMs duration in milliseconds
     */
    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    /**
     * Set the total number of URLs created during the simulation.
     *
     * @param totalCreatedUrls total created URLs
     */
    public void setTotalCreatedUrls(int totalCreatedUrls) {
        this.totalCreatedUrls = totalCreatedUrls;
    }

    /**
     * Set the cache size.
     *
     * @param cacheSize cache size
     */
    public void setCacheSize(int cacheSize) {
        this.cacheSize = cacheSize;
    }
    /**
     * Print a formatted performance report to console and logs.
     */
    public void printReport() {
        StringBuilder report = new StringBuilder();
        report.append("\n");
        report.append("╔════════════════════════════════════════════════════════════════╗\n");
        report.append("║                    TRAFFIC SIMULATION REPORT                    ║\n");
        report.append("╚════════════════════════════════════════════════════════════════╝\n");

        // Summary
        report.append(String.format("Duration:              %.2f seconds\n", durationMs / 1000.0));
        report.append(String.format("Total Requests:        %d\n", totalRequests.get()));
        report.append(String.format("Successful:            %d (%.2f%%)\n",
                successfulRequests.get(), getSuccessRatePercent()));
        report.append(String.format("Failed:                %d\n", failedRequests.get()));
        report.append(String.format("Request Rate:          %.2f req/sec\n", getRequestsPerSecond()));

        // Latency
        report.append("\nLatency (ms):\n");
        report.append(String.format("  Min:                 %d\n", getMinLatencyMs() == Long.MAX_VALUE ? 0 : getMinLatencyMs()));
        report.append(String.format("  Avg:                 %d\n", getAverageLatencyMs()));
        report.append(String.format("  p50:                 %d\n", getPercentileLatency(50)));
        report.append(String.format("  p95:                 %d\n", getPercentileLatency(95)));
        report.append(String.format("  p99:                 %d\n", getPercentileLatency(99)));
        report.append(String.format("  Max:                 %d\n", getMaxLatencyMs()));

        // Request types
        if (!requestTypeCounts.isEmpty()) {
            report.append("\nRequest Breakdown:\n");
            requestTypeCounts.forEach((type, count) ->
                    report.append(String.format("  %s: %d (%.2f%%)\n",
                            type,
                            count.get(),
                            (count.get() * 100.0) / totalRequests.get()))
            );
        }

        // Errors
        if (!errorCounts.isEmpty()) {
            report.append("\nErrors:\n");
            errorCounts.forEach((type, count) ->
                    report.append(String.format("  %s: %d\n", type, count.get()))
            );
        }

        // Cache stats
        report.append(String.format("\nCache:\n"));
        report.append(String.format("  URLs Created:        %d\n", totalCreatedUrls));
        report.append(String.format("  Cache Size:          %d\n", cacheSize));

        report.append("\n");

        String reportStr = report.toString();
        System.out.println(reportStr);
    }

    @Override
    public String toString() {
        return String.format("TrafficMetrics{total=%d, success=%.1f%%, rps=%.1f, avg_latency=%dms, p95=%dms}",
                totalRequests.get(),
                getSuccessRatePercent(),
                getRequestsPerSecond(),
                getAverageLatencyMs(),
                getPercentileLatency(95));
    }
}

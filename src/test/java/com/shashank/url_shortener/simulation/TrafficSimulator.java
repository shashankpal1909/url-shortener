package com.shashank.url_shortener.simulation;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Live traffic simulator for URL shortener application.
 *
 * <p>Generates configurable traffic patterns (create, redirect, stats) with
 * concurrent requests, latency tracking, and comprehensive metrics reporting.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * TrafficSimulationConfig config = TrafficSimulationConfig.builder()
 *     .threadCount(20)
 *     .durationSeconds(60)
 *     .requestsPerSecond(500)
 *     .baseUrl("http://localhost:8080")
 *     .build();
 *
 * TrafficSimulator simulator = new TrafficSimulator(config);
 * TrafficSimulationMetrics metrics = simulator.run();
 * metrics.printReport();
 * }</pre>
 */
public class TrafficSimulator {

    private final TrafficSimulationConfig config;
    private final HttpClient httpClient;
    private final List<String> createdShortCodes;
    private volatile boolean running;
    private static final Pattern SHORT_CODE_PATTERN = Pattern.compile("\"shortCode\"\\s*:\\s*\"([^\"]+)\"");

    /**
     * Create a new traffic simulator with the given configuration.
     *
     * @param config simulation configuration
     */
    public TrafficSimulator(TrafficSimulationConfig config) {
        config.validate();
        this.config = config;
        this.createdShortCodes = Collections.synchronizedList(new ArrayList<>());
        this.httpClient = createHttpClient();
    }

    /**
     * Run the traffic simulation.
     *
     * @return metrics collected during the run
     */
    public TrafficSimulationMetrics run() {
        System.out.println("Starting traffic simulation: " + config);
        running = true;

        TrafficSimulationMetrics metrics = new TrafficSimulationMetrics();

        ExecutorService executor = Executors.newFixedThreadPool(config.getThreadCount());
        long startTime = System.currentTimeMillis();
        long rampUpEndTime = startTime + (config.getRampUpSeconds() * 1000L);

        try {
            // Start all worker threads
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < config.getThreadCount(); i++) {
                futures.add(executor.submit(() ->
                        workerThread(startTime, rampUpEndTime, metrics)
                ));
            }

            // Wait for all threads to complete or timeout
            long timeoutMs = (config.getDurationSeconds() + 10) * 1000L;
            executor.shutdown();
            if (!executor.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)) {
                System.err.println("WARNING: Executor did not terminate gracefully");
                executor.shutdownNow();
            }

            // Wait for threads to finish
            for (Future<?> future : futures) {
                try {
                    future.get(5, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    future.cancel(true);
                } catch (ExecutionException e) {
                    System.err.println("Worker thread execution failed: " + e.getMessage());
                }
            }

        } catch (InterruptedException e) {
            System.err.println("Simulation interrupted: " + e.getMessage());
            e.printStackTrace();
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        metrics.setDurationMs(System.currentTimeMillis() - startTime);
        metrics.setTotalCreatedUrls(createdShortCodes.size());
        metrics.setCacheSize(createdShortCodes.size());

        if (config.isEnableMetrics()) {
            metrics.printReport();
        }

        return metrics;
    }

    private void workerThread(long startTime, long rampUpEndTime, TrafficSimulationMetrics metrics) {
        try {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            long endTime = startTime + (config.getDurationSeconds() * 1000L);

            while (running && System.currentTimeMillis() < endTime) {
                // Ramp-up: gradually increase load
                if (System.currentTimeMillis() < rampUpEndTime) {
                    double elapsedRampUpMs = System.currentTimeMillis() - startTime;
                    double rampUpProgressPercent = elapsedRampUpMs / (config.getRampUpSeconds() * 1000L);
                    double sleepChance = 1.0 - rampUpProgressPercent;
                    if (random.nextDouble() < sleepChance) {
                        Thread.sleep(50);
                        continue;
                    }
                }

                // Determine which type of request to perform
                double requestType = random.nextDouble();
                long requestStartTime = System.currentTimeMillis();

                try {
                    if (requestType < config.getCreateRatio()) {
                        // Create new URL
                        performCreateRequest(metrics);
                    } else if (requestType < config.getCreateRatio() + config.getRedirectRatio()) {
                        // Redirect request
                        performRedirectRequest(metrics, random);
                    } else {
                        // Stats request
                        performStatsRequest(metrics, random);
                    }
                } catch (Exception e) {
                    metrics.recordError(e.getClass().getSimpleName());
                    if (config.isVerbose()) {
                        System.err.println("Request failed: " + e.getMessage());
                    }
                }

                long latencyMs = System.currentTimeMillis() - requestStartTime;
                metrics.recordLatency(latencyMs);

                // Think time
                Thread.sleep(config.getThinkTimeMs());

                // Rate limiting
                long targetDelayMs = config.getRequestDelayMs();
                long actualDelayMs = System.currentTimeMillis() - requestStartTime;
                if (actualDelayMs < targetDelayMs) {
                    Thread.sleep(targetDelayMs - actualDelayMs);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void performCreateRequest(TrafficSimulationMetrics metrics) throws Exception {
        String requestBody = String.format(
                "{\"url\": \"https://example.com/%s\"}",
                UUID.randomUUID()
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(config.getBaseUrl() + "/shorten"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            // Extract shortCode from JSON response using regex
            Matcher matcher = SHORT_CODE_PATTERN.matcher(response.body());
            if (matcher.find()) {
                String shortCode = matcher.group(1);
                createdShortCodes.add(shortCode);
            }
            metrics.recordSuccess("create");
        } else {
            metrics.recordError("HttpError" + response.statusCode());
        }
    }

    private void performRedirectRequest(TrafficSimulationMetrics metrics, ThreadLocalRandom random) throws Exception {
        String shortCode;
        if (!createdShortCodes.isEmpty() && random.nextDouble() < config.getCacheHitRatio()) {
            // Use cached short code
            shortCode = createdShortCodes.get(random.nextInt(createdShortCodes.size()));
        } else {
            // Use random short code (likely 404)
            shortCode = generateRandomShortCode();
        }

        HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(config.getBaseUrl() + "/" + shortCode))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 302 || response.statusCode() == 200) {
            metrics.recordSuccess("redirect");
        } else if (response.statusCode() == 404) {
            metrics.recordSuccess("notfound");  // Expected for random codes
        } else {
            metrics.recordError("HttpError" + response.statusCode());
        }
    }

    private void performStatsRequest(TrafficSimulationMetrics metrics, ThreadLocalRandom random) throws Exception {
        String shortCode;
        if (!createdShortCodes.isEmpty() && random.nextDouble() < config.getCacheHitRatio()) {
            shortCode = createdShortCodes.get(random.nextInt(createdShortCodes.size()));
        } else {
            shortCode = generateRandomShortCode();
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(config.getBaseUrl() + "/stats/" + shortCode))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            metrics.recordSuccess("stats");
        } else if (response.statusCode() == 404) {
            metrics.recordSuccess("notfound");
        } else {
            metrics.recordError("HttpError" + response.statusCode());
        }
    }

    private String generateRandomShortCode() {
        return UUID.randomUUID().toString().substring(0, 6);
    }

    private HttpClient createHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_2);

        if (config.isUseConnectionPool()) {
            builder.executor(createConnectionPool());
        }

        return builder.build();
    }

    private ExecutorService createConnectionPool() {
        return Executors.newFixedThreadPool(
                config.getPoolSize(),
                r -> {
                    Thread t = new Thread(r, "HttpPool-" + UUID.randomUUID());
                    t.setDaemon(true);
                    return t;
                }
        );
    }

    /**
     * Stop the simulation gracefully.
     */
    public void stop() {
        System.out.println("Stopping traffic simulation");
        running = false;
    }
}

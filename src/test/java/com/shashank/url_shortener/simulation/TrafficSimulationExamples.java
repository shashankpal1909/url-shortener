package com.shashank.url_shortener.simulation;

/**
 * Examples of traffic simulation scenarios with different configurations.
 */
public class TrafficSimulationExamples {

    /**
     * Run a light load test (suitable for local development).
     * - 10 concurrent threads
     * - 100 RPS total
     * - 30 second duration
     * - Mix: 10% create, 80% redirect, 10% stats
     */
    public static void lightLoadTest() {
        TrafficSimulationConfig config = TrafficSimulationConfig.builder()
                .threadCount(10)
                .durationSeconds(30)
                .requestsPerSecond(100)
                .createRatio(0.1)
                .redirectRatio(0.8)
                .statsRatio(0.1)
                .baseUrl("http://localhost:8080")
                .enableMetrics(true)
                .verbose(false)
                .build();

        TrafficSimulator simulator = new TrafficSimulator(config);
        TrafficSimulationMetrics metrics = simulator.run();
        System.out.println("\n✓ Light load test complete: " + metrics);
    }

    /**
     * Run a standard load test.
     * - 30 concurrent threads
     * - 500 RPS total
     * - 60 second duration
     * - Mix: 5% create, 85% redirect, 10% stats
     */
    public static void standardLoadTest() {
        TrafficSimulationConfig config = TrafficSimulationConfig.builder()
                .threadCount(30)
                .durationSeconds(60)
                .requestsPerSecond(500)
                .createRatio(0.05)
                .redirectRatio(0.85)
                .statsRatio(0.10)
                .baseUrl("http://localhost:8080")
                .enableMetrics(true)
                .rampUpSeconds(10)
                .cacheHitRatio(0.75)
                .build();

        TrafficSimulator simulator = new TrafficSimulator(config);
        TrafficSimulationMetrics metrics = simulator.run();
        System.out.println("\n✓ Standard load test complete: " + metrics);
    }

    /**
     * Run a high-load stress test.
     * - 100 concurrent threads
     * - 2000 RPS total
     * - 120 second duration
     * - Heavy read load: 2% create, 90% redirect, 8% stats
     */
    public static void stressTest() {
        TrafficSimulationConfig config = TrafficSimulationConfig.builder()
                .threadCount(100)
                .durationSeconds(120)
                .requestsPerSecond(2000)
                .createRatio(0.02)
                .redirectRatio(0.90)
                .statsRatio(0.08)
                .baseUrl("http://localhost:8080")
                .enableMetrics(true)
                .rampUpSeconds(30)
                .cacheHitRatio(0.80)
                .poolSize(200)
                .build();

        TrafficSimulator simulator = new TrafficSimulator(config);
        TrafficSimulationMetrics metrics = simulator.run();
        System.out.println("\n✓ Stress test complete: " + metrics);
    }

    /**
     * Run a spike test (sudden traffic spike).
     * - 200 concurrent threads for 10 seconds
     * - 5000 RPS
     * - Shallow ramp-up to simulate traffic spike
     */
    public static void spikeTest() {
        TrafficSimulationConfig config = TrafficSimulationConfig.builder()
                .threadCount(200)
                .durationSeconds(10)
                .requestsPerSecond(5000)
                .createRatio(0.02)
                .redirectRatio(0.90)
                .statsRatio(0.08)
                .baseUrl("http://localhost:8080")
                .enableMetrics(true)
                .rampUpSeconds(2)
                .cacheHitRatio(0.85)
                .poolSize(300)
                .thinkTimeMs(5)
                .build();

        TrafficSimulator simulator = new TrafficSimulator(config);
        TrafficSimulationMetrics metrics = simulator.run();
        System.out.println("\n✓ Spike test complete: " + metrics);
    }

    /**
     * Run a long-duration extreme traffic test.
     * - 300 concurrent threads
     * - 8000 RPS
     * - 15 minute duration
     * - Read-heavy: 1% create, 96% redirect, 3% stats
     */
    public static void longExtremeTest() {
        TrafficSimulationConfig config = TrafficSimulationConfig.builder()
                .threadCount(300)
                .durationSeconds(900)
                .requestsPerSecond(8000)
                .createRatio(0.01)
                .redirectRatio(0.96)
                .statsRatio(0.03)
                .baseUrl("http://localhost:8080")
                .enableMetrics(true)
                .rampUpSeconds(60)
                .cacheHitRatio(0.90)
                .poolSize(500)
                .thinkTimeMs(3)
                .build();

        TrafficSimulator simulator = new TrafficSimulator(config);
        TrafficSimulationMetrics metrics = simulator.run();
        System.out.println("\n✓ Long extreme test complete: " + metrics);
    }

    /**
     * Run a read-heavy scenario (typical production load).
     * - 50 concurrent threads
     * - 1000 RPS
     * - 90 second duration
     * - Read-heavy: 1% create, 96% redirect, 3% stats
     */
    public static void readHeavyScenario() {
        TrafficSimulationConfig config = TrafficSimulationConfig.builder()
                .threadCount(50)
                .durationSeconds(90)
                .requestsPerSecond(1000)
                .createRatio(0.01)
                .redirectRatio(0.96)
                .statsRatio(0.03)
                .baseUrl("http://localhost:8080")
                .enableMetrics(true)
                .rampUpSeconds(15)
                .cacheHitRatio(0.85)
                .thinkTimeMs(15)
                .build();

        TrafficSimulator simulator = new TrafficSimulator(config);
        TrafficSimulationMetrics metrics = simulator.run();
        System.out.println("\n✓ Read-heavy scenario complete: " + metrics);
    }

    /**
     * Run a write-heavy scenario (bulk URL creation).
     * - 40 concurrent threads
     * - 400 RPS
     * - 60 second duration
     * - Write-heavy: 40% create, 50% redirect, 10% stats
     */
    public static void writeHeavyScenario() {
        TrafficSimulationConfig config = TrafficSimulationConfig.builder()
                .threadCount(40)
                .durationSeconds(60)
                .requestsPerSecond(400)
                .createRatio(0.40)
                .redirectRatio(0.50)
                .statsRatio(0.10)
                .baseUrl("http://localhost:8080")
                .enableMetrics(true)
                .rampUpSeconds(10)
                .cacheHitRatio(0.60)
                .thinkTimeMs(25)
                .build();

        TrafficSimulator simulator = new TrafficSimulator(config);
        TrafficSimulationMetrics metrics = simulator.run();
        System.out.println("\n✓ Write-heavy scenario complete: " + metrics);
    }

    /**
     * Run a custom scenario (template for your own tests).
     */
    public static void customScenario() {
        TrafficSimulationConfig config = TrafficSimulationConfig.builder()
                .threadCount(20)
                .durationSeconds(45)
                .requestsPerSecond(200)
                .createRatio(0.15)
                .redirectRatio(0.75)
                .statsRatio(0.10)
                .baseUrl("http://localhost:8080")
                .enableMetrics(true)
                .verbose(true)
                .rampUpSeconds(5)
                .cacheHitRatio(0.70)
                .thinkTimeMs(20)
                .build();

        System.out.println("Running custom scenario: " + config);
        TrafficSimulator simulator = new TrafficSimulator(config);
        TrafficSimulationMetrics metrics = simulator.run();
        System.out.println("\n✓ Custom scenario complete: " + metrics);
    }

    /**
     * Main entry point - run all examples.
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java TrafficSimulationExamples [scenario]");
            System.out.println("\nAvailable scenarios:");
            System.out.println("  light      - Light load test (10 threads, 100 RPS)");
            System.out.println("  standard   - Standard load test (30 threads, 500 RPS)");
            System.out.println("  stress     - Stress test (100 threads, 2000 RPS)");
            System.out.println("  spike      - Spike test (200 threads, 5000 RPS, 10s)");
            System.out.println("  long-extreme - Long extreme test (300 threads, 8000 RPS, 15m)");
            System.out.println("  read       - Read-heavy scenario (50 threads, 1000 RPS)");
            System.out.println("  write      - Write-heavy scenario (40 threads, 400 RPS)");
            System.out.println("  custom     - Custom scenario (20 threads, 200 RPS)");
            System.out.println("\nExample: java TrafficSimulationExamples standard");
            return;
        }

        String scenario = args[0].toLowerCase();
        System.out.println("🚀 Starting traffic simulation scenario: " + scenario);
        System.out.println();

        switch (scenario) {
            case "light":
                lightLoadTest();
                break;
            case "standard":
                standardLoadTest();
                break;
            case "stress":
                stressTest();
                break;
            case "spike":
                spikeTest();
                break;
            case "long-extreme":
                longExtremeTest();
                break;
            case "read":
                readHeavyScenario();
                break;
            case "write":
                writeHeavyScenario();
                break;
            case "custom":
                customScenario();
                break;
            default:
                System.out.println("❌ Unknown scenario: " + scenario);
                System.out.println("Available: light, standard, stress, spike, long-extreme, read, write, custom");
        }
    }
}

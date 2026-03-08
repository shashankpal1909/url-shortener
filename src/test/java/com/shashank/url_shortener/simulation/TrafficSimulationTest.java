package com.shashank.url_shortener.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.ObjectMapper;
import com.shashank.url_shortener.dto.ShortenRequest;
import com.shashank.url_shortener.dto.ShortenResponse;
import com.shashank.url_shortener.dto.StatsResponse;

/**
 * Traffic simulation integration test suite.
 *
 * <p>Starts a full Spring application context (no Redis in test profile) and
 * exercises the URL-shortener API with concurrent load patterns that approximate
 * real-world traffic scenarios:</p>
 * <ol>
 *   <li><b>Unknown short code → 404</b> — baseline correctness check.</li>
 *   <li><b>Concurrent URL creation</b> — verifies unique short codes are
 *       produced correctly under parallel write pressure.</li>
 *   <li><b>Hot-URL redirect storm</b> — many threads hit the same short code
 *       simultaneously; the click count must exactly equal the number of
 *       successful redirects, validating the atomic DB increment (Phase 0).</li>
 *   <li><b>Stats endpoint correctness</b> — verifies returned metadata and
 *       that an unknown code returns 404.</li>
 *   <li><b>Mixed traffic</b> — creation, redirect, and stats requests run
 *       concurrently to simulate a realistic mixed workload.</li>
 * </ol>
 *
 * <p>Each scenario prints a human-readable throughput/latency summary so
 * results are immediately visible in the test output.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TrafficSimulationTest {

    /** Number of worker threads for concurrent scenarios. */
    private static final int THREAD_COUNT = 20;

    /** Number of redirect requests in the hot-URL scenario. */
    private static final int REDIRECT_REQUESTS = 100;

    /** Number of URLs created in the parallel-creation scenario. */
    private static final int CONCURRENT_CREATES = 30;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeAll
    void buildMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    // -------------------------------------------------------------------------
    // Low-level helpers
    // -------------------------------------------------------------------------

    private ShortenResponse createShortUrl(String originalUrl) throws Exception {
        ShortenRequest req = new ShortenRequest();
        req.setUrl(originalUrl);

        MvcResult result = mockMvc.perform(
                post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).as("POST /shorten should return 200")
                .isEqualTo(200);
        return objectMapper.readValue(
                result.getResponse().getContentAsString(), ShortenResponse.class);
    }

    private int redirect(String shortCode) throws Exception {
        return mockMvc.perform(get("/" + shortCode))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private StatsResponse stats(String shortCode) throws Exception {
        MvcResult result = mockMvc.perform(get("/stats/" + shortCode)).andReturn();
        assertThat(result.getResponse().getStatus()).as("GET /stats should return 200").isEqualTo(200);
        return objectMapper.readValue(
                result.getResponse().getContentAsString(), StatsResponse.class);
    }

    private static void printSummary(String label, int ops, long durationMs, long avgLatencyMs) {
        double rps = durationMs > 0 ? ops / (durationMs / 1000.0) : Double.POSITIVE_INFINITY;
        System.out.printf("  [TRAFFIC SIM] %-40s  ops=%-4d  duration=%-6dms  " +
                "throughput=%-8.1f req/s  avg-latency=%dms%n",
                label, ops, durationMs, rps, avgLatencyMs);
    }

    // -------------------------------------------------------------------------
    // Scenario 1 — Unknown short code → 404
    // -------------------------------------------------------------------------

    @Test
    @Order(1)
    void scenario1_unknownShortCode_returns404() throws Exception {
        System.out.println("\n[Scenario 1] Unknown short code → 404");

        int status = mockMvc.perform(get("/nonexistent_code_xyz_99"))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status).as("Unknown short code should return 404").isEqualTo(404);
        System.out.println("  PASS — 404 returned for unknown short code");
    }

    // -------------------------------------------------------------------------
    // Scenario 2 — Concurrent URL creation
    // -------------------------------------------------------------------------

    @Test
    @Order(2)
    void scenario2_concurrentUrlCreation_allSucceedWithUniqueShortCodes() throws Exception {
        System.out.println("\n[Scenario 2] Concurrent URL creation — " + CONCURRENT_CREATES + " threads");

        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        List<Future<ShortenResponse>> futures = new ArrayList<>();
        AtomicLong totalLatencyMs = new AtomicLong();

        long start = System.currentTimeMillis();
        for (int i = 0; i < CONCURRENT_CREATES; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                long t = System.currentTimeMillis();
                ShortenResponse r = createShortUrl("https://example.com/page/" + idx);
                totalLatencyMs.addAndGet(System.currentTimeMillis() - t);
                return r;
            }));
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS))
                .as("Thread pool should finish within 30 seconds").isTrue();
        long durationMs = System.currentTimeMillis() - start;

        List<String> shortCodes = new ArrayList<>();
        for (Future<ShortenResponse> f : futures) {
            ShortenResponse r = f.get();
            assertThat(r.getShortCode()).isNotBlank();
            assertThat(r.getShortURL()).contains(r.getShortCode());
            shortCodes.add(r.getShortCode());
        }

        long distinct = shortCodes.stream().distinct().count();
        assertThat(distinct)
                .as("All %d created URLs must have unique short codes", CONCURRENT_CREATES)
                .isEqualTo(CONCURRENT_CREATES);

        printSummary("Concurrent URL creation", CONCURRENT_CREATES, durationMs,
                totalLatencyMs.get() / CONCURRENT_CREATES);
    }

    // -------------------------------------------------------------------------
    // Scenario 3 — Hot-URL redirect storm (atomic click count validation)
    // -------------------------------------------------------------------------

    @Test
    @Order(3)
    void scenario3_hotUrlRedirectStorm_clickCountMatchesSuccessfulRedirects() throws Exception {
        System.out.println("\n[Scenario 3] Hot-URL redirect storm — "
                + REDIRECT_REQUESTS + " concurrent redirects to same URL");

        ShortenResponse created = createShortUrl("https://example.com/hot-page");
        String shortCode = created.getShortCode();

        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        AtomicInteger successCount = new AtomicInteger();
        AtomicLong totalLatencyMs = new AtomicLong();
        List<Future<Void>> futures = new ArrayList<>();

        long start = System.currentTimeMillis();
        for (int i = 0; i < REDIRECT_REQUESTS; i++) {
            futures.add(pool.submit(() -> {
                long t = System.currentTimeMillis();
                int status = redirect(shortCode);
                totalLatencyMs.addAndGet(System.currentTimeMillis() - t);
                if (status == 302) {
                    successCount.incrementAndGet();
                }
                return null;
            }));
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS))
                .as("Thread pool should finish within 30 seconds").isTrue();
        long durationMs = System.currentTimeMillis() - start;

        for (Future<Void> f : futures) {
            f.get(); // rethrow any assertion errors from worker threads
        }

        assertThat(successCount.get())
                .as("All %d redirect requests must return HTTP 302", REDIRECT_REQUESTS)
                .isEqualTo(REDIRECT_REQUESTS);

        StatsResponse statsResponse = stats(shortCode);
        assertThat(statsResponse.getClickCount())
                .as("Click count must exactly match redirects (atomic increment check)")
                .isEqualTo((long) REDIRECT_REQUESTS);

        printSummary("Hot-URL redirect storm", REDIRECT_REQUESTS, durationMs,
                totalLatencyMs.get() / REDIRECT_REQUESTS);
        System.out.printf("  Click count in DB after storm: %d (expected %d) ✓%n",
                statsResponse.getClickCount(), REDIRECT_REQUESTS);
    }

    // -------------------------------------------------------------------------
    // Scenario 4 — Stats endpoint correctness
    // -------------------------------------------------------------------------

    @Test
    @Order(4)
    void scenario4_statsEndpoint_returnsCorrectMetadata() throws Exception {
        System.out.println("\n[Scenario 4] Stats endpoint — metadata correctness");

        String originalUrl = "https://example.com/stats-check-page";
        ShortenResponse created = createShortUrl(originalUrl);
        String shortCode = created.getShortCode();

        // Single redirect so click count starts at 1
        assertThat(redirect(shortCode)).as("Redirect should return 302").isEqualTo(302);

        StatsResponse s = stats(shortCode);
        assertThat(s.getShortCode()).isEqualTo(shortCode);
        assertThat(s.getOriginalURL()).isEqualTo(originalUrl);
        assertThat(s.getClickCount()).isEqualTo(1L);
        assertThat(s.getCreatedAt()).isNotNull();

        // Stats for unknown code → 404
        int notFoundStatus = mockMvc.perform(get("/stats/no_such_code_xyz"))
                .andReturn().getResponse().getStatus();
        assertThat(notFoundStatus).as("Stats for unknown short code should return 404").isEqualTo(404);

        System.out.println("  PASS — stats metadata correct, unknown code → 404");
    }

    // -------------------------------------------------------------------------
    // Scenario 5 — Mixed traffic (create + redirect + stats concurrently)
    // -------------------------------------------------------------------------

    @Test
    @Order(5)
    void scenario5_mixedTraffic_allOperationsSucceedConcurrently() throws Exception {
        System.out.println("\n[Scenario 5] Mixed traffic — creates, redirects, and stats concurrently");

        int totalOps = 60;
        ConcurrentHashMap<String, AtomicInteger> redirectsSent = new ConcurrentHashMap<>();

        // Pre-create a URL we'll redirect to during the mixed run
        ShortenResponse preCreated = createShortUrl("https://example.com/mixed-preexisting");
        redirectsSent.put(preCreated.getShortCode(), new AtomicInteger(0));

        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        AtomicLong totalLatencyMs = new AtomicLong();
        List<Future<Void>> futures = new ArrayList<>();

        long start = System.currentTimeMillis();
        for (int i = 0; i < totalOps; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                long t = System.currentTimeMillis();
                if (idx % 3 == 0) {
                    // create
                    ShortenResponse r = createShortUrl("https://example.com/mixed/" + idx);
                    redirectsSent.putIfAbsent(r.getShortCode(), new AtomicInteger(0));
                } else if (idx % 3 == 1) {
                    // redirect the pre-created URL
                    int status = redirect(preCreated.getShortCode());
                    if (status == 302) {
                        redirectsSent.get(preCreated.getShortCode()).incrementAndGet();
                    }
                } else {
                    // stats for the pre-created URL
                    StatsResponse s = stats(preCreated.getShortCode());
                    assertThat(s).isNotNull();
                }
                totalLatencyMs.addAndGet(System.currentTimeMillis() - t);
                return null;
            }));
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS))
                .as("Thread pool should finish within 30 seconds").isTrue();
        long durationMs = System.currentTimeMillis() - start;

        for (Future<Void> f : futures) {
            f.get();
        }

        int expectedClicks = redirectsSent.get(preCreated.getShortCode()).get();
        StatsResponse finalStats = stats(preCreated.getShortCode());
        assertThat(finalStats.getClickCount())
                .as("Click count must equal redirects issued for pre-created URL (atomicity check)")
                .isEqualTo((long) expectedClicks);

        printSummary("Mixed traffic", totalOps, durationMs, totalLatencyMs.get() / totalOps);
        System.out.printf("  Pre-created URL: %d redirects sent, %d clicks recorded ✓%n",
                expectedClicks, finalStats.getClickCount());
    }
}

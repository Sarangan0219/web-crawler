package com.web.crawler.manager;

import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SingleDomainCrawlManagerTest {

    @Test
    void testConstructorRejectsEmptyStartUrls() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new SingleDomainCrawlManager(List.of(), 10, 2, 10));
        assertEquals("At least one start URL must be provided.", ex.getMessage());
    }

    @Test
    void testConstructorRejectsInvalidDomains() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new SingleDomainCrawlManager(List.of("invalid-url"), 10, 2, 10));
        assertEquals("No valid domains found in start URLs.", ex.getMessage());
    }

    @Test
    void testEnqueueUrlRejectsNullOrOutOfScopeUrls() {
        var manager = new SingleDomainCrawlManager(List.of("https://monzo.com"), 10, 2, 10 );
        manager.enqueueUrl(null, 0);
        manager.enqueueUrl("https://monzo.com/page", 100);
        manager.enqueueUrl("https://otherdomain.com", 0);
        Map<String, Object> status = manager.getStatus();
        assertEquals(0, status.get("resultsCount"));
    }

    @Test
    void testEnqueueUrlAcceptsValidUrl() {
        var manager = new SingleDomainCrawlManager(List.of("https://monzo.com"), 10, 2, 10);

        manager.enqueueUrl("https://monzo.com/page1", 1);
        Map<String, Object> status = manager.getStatus();

        assertTrue((Integer) status.get("queueSize") > 0 || (Integer) status.get("visitedUrlsCount") > 0);
    }

    @Test
    void testStartAndStopCrawl() throws Exception {
        var manager = new SingleDomainCrawlManager(List.of("https://monzo.com"), 5, 1,10);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(manager::start);
        Thread.sleep(500);
        manager.stop();

        future.get(5, TimeUnit.SECONDS);

        assertFalse(manager.isRunning());
        executor.shutdown();
    }

    @Test
    void testRecordCrawlResultIncrementsProcessedPages() {
        var manager = new SingleDomainCrawlManager(List.of("https://monzo.com"), 5, 1, 10);

        manager.recordCrawlResult("https://monzo.com", List.of("https://monzo.com/business-banking",
                "https://monzo.com/sign-up"));

        Map<String, Object> status = manager.getStatus();
        assertEquals(1, status.get("processedPages"));
        assertEquals(1, status.get("resultsCount"));
    }

    @Test
    void testTaskCompletedDecrementsPendingTasks() {
        var manager = new SingleDomainCrawlManager(List.of("https://monzo.com"), 5, 1, 10);
        try {
            var field = SingleDomainCrawlManager.class.getDeclaredField("pendingTasks");
            field.setAccessible(true);
            var atomicInt = (AtomicInteger) field.get(manager);
            atomicInt.set(1);
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }

        manager.taskCompleted();

        try {
            var field = SingleDomainCrawlManager.class.getDeclaredField("pendingTasks");
            field.setAccessible(true);
            var atomicInt = (AtomicInteger) field.get(manager);
            assertEquals(0, atomicInt.get());
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }
    }

    @Test
    void testGetStatusReturnsExpectedKeys() {
        var manager = new SingleDomainCrawlManager(List.of("https://monzo.com"), 10, 2, 10);
        Map<String, Object> status = manager.getStatus();

        assertTrue(status.containsKey("running"));
        assertTrue(status.containsKey("processedPages"));
        assertTrue(status.containsKey("pendingTasks"));
        assertTrue(status.containsKey("queueSize"));
        assertTrue(status.containsKey("visitedUrlsCount"));
        assertTrue(status.containsKey("maxPages"));
        assertTrue(status.containsKey("maxDepth"));
        assertTrue(status.containsKey("domains"));
        assertTrue(status.containsKey("resultsCount"));
    }
}

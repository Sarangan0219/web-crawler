package com.web.crawler.manager;

import com.web.crawler.service.CrawlWorker;
import com.web.crawler.util.UrlUtils;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class SingleDomainCrawlManager implements CrawlManager {

    private static final ExecutorService SHARED_EXECUTOR = createSharedExecutor();
    private final Set<String> visitedUrls;
    private final BlockingQueue<UrlDepthPair> urlQueue;
    private final AtomicInteger pendingTasks = new AtomicInteger(0);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean shouldStop = new AtomicBoolean(false);
    private final CountDownLatch completionLatch = new CountDownLatch(1);

    private final Set<String> allowedDomains;
    private final int maxPages;
    private final int maxDepth;
    private final int crawlTimeoutMinutes;
    private final AtomicInteger processedPages = new AtomicInteger(0);
    private final Map<String, List<String>> crawlResults = new ConcurrentHashMap<>();
    private final AtomicBoolean crawlCompleted = new AtomicBoolean(false);

    private volatile LocalDateTime startTime;
    private volatile LocalDateTime endTime;


    public SingleDomainCrawlManager(List<String> startUrls, int maxPages, int maxDepth, int crawlTimeoutMinutes) {
        if (startUrls == null || startUrls.isEmpty()) {
            throw new IllegalArgumentException("At least one start URL must be provided.");
        }
        this.crawlTimeoutMinutes = crawlTimeoutMinutes;

        this.allowedDomains = ConcurrentHashMap.newKeySet();
        for (String url : startUrls) {
            String domain = extractDomain(url);
            if (domain != null) {
                this.allowedDomains.add(domain);
            }
        }

        if (allowedDomains.isEmpty()) {
            throw new IllegalArgumentException("No valid domains found in start URLs.");
        }
        this.visitedUrls = ConcurrentHashMap.newKeySet();

        this.maxPages = Math.min(maxPages, 1000);
        this.maxDepth = Math.min(maxDepth, 10);

        this.urlQueue = new ArrayBlockingQueue<>(maxPages * 2);

        for (String url : startUrls) {
            if (isSameDomain(url)) {
                enqueueUrl(url, 0);
            } else {
                log.warn("Start URL {} is not in allowed domains {}, skipping", url, allowedDomains);
            }
        }

        log.info("Initialized crawler for domains: {}, maxPages: {}, maxDepth: {}",
                allowedDomains, this.maxPages, this.maxDepth);
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Crawl already running for domains: {}, skipping", allowedDomains);
            return;
        }

        log.info("Starting crawl for domains: {}, maxPages: {}, maxDepth: {}",
                allowedDomains, maxPages, maxDepth);

        try {
            this.startTime = LocalDateTime.now();
            CompletableFuture<Void> crawlFuture = CompletableFuture.runAsync(this::executeCrawl, SHARED_EXECUTOR);
            crawlFuture.get(crawlTimeoutMinutes, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            log.warn("Crawl timed out after {} minutes", crawlTimeoutMinutes);
            shouldStop.set(true);
        } catch (Exception e) {
            log.error("Crawl execution failed", e);
            throw new RuntimeException("Crawl execution failed", e);
        } finally {
            this.endTime = LocalDateTime.now();
            crawlCompleted.set(true);
            shutdown();
        }
    }

    private void executeCrawl() {
        try {
            while (running.get() && !shouldStop.get() && processedPages.get() < maxPages) {
                UrlDepthPair urlPair = urlQueue.poll(2, TimeUnit.SECONDS);

                if (urlPair != null && urlPair.depth <= maxDepth) {
                    pendingTasks.incrementAndGet();
                    SHARED_EXECUTOR.submit(() -> {
                        try {
                            new CrawlWorker(urlPair.url, urlPair.depth, this).run();
                        } finally {
                            taskCompleted();
                        }
                    });

                } else if (urlPair == null && pendingTasks.get() == 0) {
                    log.info("No more URLs to process and no pending tasks. Crawl complete.");
                    break;
                }

                // Check for completion more frequently
                if (processedPages.get() >= maxPages) {
                    log.info("Reached maximum pages limit: {}", maxPages);
                    break;
                }
            }

            log.info("Main crawl loop finished. Waiting for {} pending tasks to complete...", pendingTasks.get());

            long waitStart = System.currentTimeMillis();
            int lastPendingCount = pendingTasks.get();

            while (pendingTasks.get() > 0 && !shouldStop.get()) {
                int currentPending = pendingTasks.get();
                if (currentPending != lastPendingCount) {
                    log.info("Pending tasks: {}, Processed pages: {}", currentPending, processedPages.get());
                    lastPendingCount = currentPending;
                }

                if (System.currentTimeMillis() - waitStart > 30000) {
                    log.warn("Timeout waiting for {} pending tasks to complete", pendingTasks.get());
                    break;
                }
                Thread.sleep(500);
            }

            log.info("All tasks completed. Final processed pages: {}, Final results count: {}",
                    processedPages.get(), crawlResults.size());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Crawl interrupted");
        } finally {
            completionLatch.countDown();
        }
    }

    @Override
    public void stop() {
        shouldStop.set(true);
        try {
            completionLatch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        crawlCompleted.set(true);
        shutdown();
    }

    @Override
    public boolean isRunning() {
        return running.get() && !crawlCompleted.get();
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("running", running.get());
        status.put("completed", crawlCompleted.get());
        status.put("processedPages", processedPages.get());
        status.put("pendingTasks", pendingTasks.get());
        status.put("queueSize", urlQueue.size());
        status.put("visitedUrlsCount", visitedUrls.size());
        status.put("visitedUrls", new ArrayList<>(visitedUrls));
        status.put("maxPages", maxPages);
        status.put("maxDepth", maxDepth);
        status.put("domains", new ArrayList<>(allowedDomains));
        status.put("resultsCount", crawlResults.size());
        status.put("results", new HashMap<>(crawlResults));
        status.put("hasResults", !crawlResults.isEmpty());
        if (log.isDebugEnabled()) {
            log.debug("Status check - Results: {}, Processed: {}, Running: {}",
                    crawlResults.size(), processedPages.get(), running.get());
        }
        status.put("startTime", startTime);
        status.put("endTime", endTime);

        return status;
    }

    @Override
    public void enqueueUrl(String url, int depth) {
        if (url == null || shouldStop.get() || processedPages.get() >= maxPages || depth > maxDepth) {
            return;
        }

        String normalizedUrl = UrlUtils.normalizeUrl(url);
        if (normalizedUrl != null && isSameDomain(normalizedUrl) && visitedUrls.add(normalizedUrl)) {
            if (!urlQueue.offer(new UrlDepthPair(normalizedUrl, depth))) {
                log.debug("URL queue full, skipping: {}", normalizedUrl);
            }
        }
    }

    public void recordCrawlResult(String url, List<String> links) {
        if (url == null || links == null) {
            log.warn("Null URL or links provided to recordCrawlResult");
            return;
        }

        List<String> limitedLinks = links.size() > 100 ? links.subList(0, 100) : new ArrayList<>(links);
        crawlResults.put(url, limitedLinks);

        int processed = processedPages.incrementAndGet();

        if (log.isInfoEnabled()) {
            String domain = extractDomain(url);
            log.info("🔍 Visited [{}]: {} (Found {} links) - Progress: {}/{} - Total Results: {}",
                    domain, url, links.size(), processed, maxPages, crawlResults.size());

            if (log.isDebugEnabled()) {
                log.debug("Links found:");
                for (String link : limitedLinks) {
                    log.debug("  → {}", link);
                }
            }
        }

        for (String link : links) {
            if (link != null && !link.trim().isEmpty()) {
                enqueueUrl(link, extractDepthFromUrl(url) + 1);
            }
        }
    }

    private int extractDepthFromUrl(String url) {
        // This is a simplified approach - you might need to track depth differently
        // For now, we'll use a basic heuristic based on path segments
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            if (path == null || path.equals("/")) return 0;
            return path.split("/").length - 1;
        } catch (Exception e) {
            return 0;
        }
    }

    public void taskCompleted() {
        int remaining = pendingTasks.decrementAndGet();
        if (log.isDebugEnabled()) {
            log.debug("Task completed. Remaining tasks: {}, Processed pages: {}", remaining, processedPages.get());
        }
    }

    private boolean isSameDomain(String url) {
        String urlDomain = extractDomain(url);
        return urlDomain != null && allowedDomains.contains(urlDomain);
    }

    private String extractDomain(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) return null;

            // Remove www. prefix if present
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            return host.toLowerCase();
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Failed to extract domain from URL: {}", url);
            }
            return null;
        }
    }

    private void shutdown() {
        running.set(false);

        log.info("\n✅ Crawling finished for domains: {}", allowedDomains);
        log.info("Total pages crawled: {}", processedPages.get());
        log.info("Total results collected: {}", crawlResults.size());
        log.info("Pending tasks at shutdown: {}", pendingTasks.get());

        if (crawlResults.isEmpty()) {
            log.warn("⚠️  No results were collected during crawling. Check CrawlWorker implementation.");
        }

        if (visitedUrls.size() <= 50) {
            logDetailedResults();
        } else {
            log.info("Crawled {} URLs across {} domains", visitedUrls.size(), allowedDomains.size());
        }
    }

    private void logDetailedResults() {
        log.info("Visited URLs:");
        Map<String, List<String>> urlsByDomain = new HashMap<>();
        for (String url : visitedUrls) {
            String domain = extractDomain(url);
            urlsByDomain.computeIfAbsent(domain, k -> new ArrayList<>()).add(url);
        }

        for (Map.Entry<String, List<String>> entry : urlsByDomain.entrySet()) {
            log.info("Domain: {}", entry.getKey());
            for (String url : entry.getValue()) {
                List<String> links = crawlResults.get(url);
                log.info("  • {} (Found {} links)", url, links != null ? links.size() : 0);
            }
        }
    }

    private static ExecutorService createSharedExecutor() {
        int corePoolSize = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        int maxPoolSize = Math.min(20, Runtime.getRuntime().availableProcessors() * 2);

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                new ThreadFactory() {
                    private final AtomicInteger threadNumber = new AtomicInteger(1);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "crawler-pool-" + threadNumber.getAndIncrement());
                        t.setDaemon(true);
                        t.setPriority(Thread.NORM_PRIORITY);
                        return t;
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        executor.allowCoreThreadTimeOut(true);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down shared crawler executor");
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }));

        return executor;
    }

    private record UrlDepthPair(String url, int depth) {
    }
}
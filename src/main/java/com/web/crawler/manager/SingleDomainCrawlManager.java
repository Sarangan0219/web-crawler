package com.web.crawler.manager;

import com.web.crawler.service.CrawlWorker;
import com.web.crawler.util.UrlUtils;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class SingleDomainCrawlManager implements CrawlManager {

    private final ExecutorService executor;
    private final Set<String> visitedUrls;
    private final BlockingQueue<UrlDepthPair> urlQueue;
    private final AtomicInteger pendingTasks = new AtomicInteger(0);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean shouldStop = new AtomicBoolean(false);

    private final Set<String> allowedDomains;
    private final int maxPages;
    private final int maxDepth;
    private final AtomicInteger processedPages = new AtomicInteger(0);

    private static final int RATE_LIMIT_MS = 1000;
    private final Map<String, List<String>> crawlResults = new ConcurrentHashMap<>();

    public SingleDomainCrawlManager(List<String> startUrls, int maxPages, int maxDepth) {
        if (startUrls == null || startUrls.isEmpty()) {
            throw new IllegalArgumentException("At least one start URL must be provided.");
        }

        this.allowedDomains = new HashSet<>();
        for (String url : startUrls) {
            String domain = extractDomain(url);
            if (domain != null) {
                this.allowedDomains.add(domain);
            }
        }

        if (allowedDomains.isEmpty()) {
            throw new IllegalArgumentException("No valid domains found in start URLs.");
        }

        this.maxPages = maxPages;
        this.maxDepth = maxDepth;

        int cores = Math.min(Runtime.getRuntime().availableProcessors(), 4); // Limit threads
        this.executor = Executors.newFixedThreadPool(cores);
        this.visitedUrls = ConcurrentHashMap.newKeySet();
        this.urlQueue = new LinkedBlockingQueue<>();

        for (String url : startUrls) {
            if (isSameDomain(url)) {
                enqueueUrl(url, 0);
            } else {
                log.warn("Start URL {} is not in allowed domains {}, skipping", url, allowedDomains);
            }
        }

        log.info("Initialized crawler for domains: {}", allowedDomains);
    }

    @Override
    public void start() {
        running.set(true);
        log.info("Starting crawl for domains: {}, maxPages: {}, maxDepth: {}",
                allowedDomains, maxPages, maxDepth);

        try {
            while (running.get() && !shouldStop.get() && processedPages.get() < maxPages) {
                UrlDepthPair urlPair = urlQueue.poll(5, TimeUnit.SECONDS);

                if (urlPair != null) {
                    if (urlPair.depth <= maxDepth) {
                        pendingTasks.incrementAndGet();
                        executor.submit(new CrawlWorker(urlPair.url, urlPair.depth, this));
                        Thread.sleep(RATE_LIMIT_MS);
                    }
                } else {
                    if (pendingTasks.get() == 0) {
                        log.info("No more URLs to process and no pending tasks. Crawl complete.");
                        break;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Crawl interrupted");
        } finally {
            shutdown();
        }
    }

    @Override
    public void stop() {
        shouldStop.set(true);
        shutdown();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("running", running.get());
        status.put("processedPages", processedPages.get());
        status.put("pendingTasks", pendingTasks.get());
        status.put("queueSize", urlQueue.size());
        status.put("visitedUrls", new ArrayList<>(visitedUrls));
        status.put("maxPages", maxPages);
        status.put("maxDepth", maxDepth);
        status.put("domains", new ArrayList<>(allowedDomains)); // Changed to domains (plural)
        status.put("results", new HashMap<>(crawlResults));
        return status;
    }

    @Override
    public void enqueueUrl(String url, int depth) {
        if (url == null || shouldStop.get() || processedPages.get() >= maxPages || depth > maxDepth) {
            return;
        }

        String normalizedUrl = UrlUtils.normalizeUrl(url);
        if (normalizedUrl != null && isSameDomain(normalizedUrl) && visitedUrls.add(normalizedUrl)) {
            urlQueue.offer(new UrlDepthPair(normalizedUrl, depth));
        }
    }

    public void recordCrawlResult(String url, List<String> links) {
        crawlResults.put(url, links);
        processedPages.incrementAndGet();

        String domain = extractDomain(url);
        log.info("🔍 Visited [{}]: {} (Found {} links)", domain, url, links.size());
        for (String link : links) {
            log.info("  → {}", link);
        }
    }

    public void taskCompleted() {
        pendingTasks.decrementAndGet();
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
            log.warn("Failed to extract domain from URL: {}", url);
            return null;
        }
    }

    private void shutdown() {
        running.set(false);
        executor.shutdown();

        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("\n✅ Crawling finished for domains: {}", allowedDomains);
        log.info("Total pages crawled: {}", processedPages.get());
        log.info("Visited URLs:");

        // Group URLs by domain for better readability
        Map<String, List<String>> urlsByDomain = new HashMap<>();
        for (String url : visitedUrls) {
            String domain = extractDomain(url);
            urlsByDomain.computeIfAbsent(domain, k -> new ArrayList<>()).add(url);
        }

        for (Map.Entry<String, List<String>> entry : urlsByDomain.entrySet()) {
            log.info("Domain: {}", entry.getKey());
            for (String url : entry.getValue()) {
                log.info("  • {}", url);
            }
        }
    }

    private record UrlDepthPair(String url, int depth) {
    }
}

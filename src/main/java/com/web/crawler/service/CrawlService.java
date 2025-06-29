package com.web.crawler.service;

import com.web.crawler.manager.CrawlManager;
import com.web.crawler.manager.CrawlManagerFactory;
import com.web.crawler.model.CrawlResult;
import com.web.crawler.model.CrawlStatus;
import com.web.crawler.model.CrawlType;
import com.web.crawler.repository.CrawlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlService {

    private final CrawlManagerFactory crawlManagerFactory;
    private final CrawlRepository repository;
    private final Map<String, CrawlManager> activeCrawls = new ConcurrentHashMap<>();

    public String startCrawlAsync(List<String> urls, CrawlType type, int maxPages, int maxDepth) {
        String crawlId = UUID.randomUUID().toString();

        try {
            CrawlManager manager = crawlManagerFactory.create(urls, type, maxPages, maxDepth);
            activeCrawls.put(crawlId, manager);

            CrawlResult result = new CrawlResult(
                    crawlId, type, "RUNNING", LocalDateTime.now(), null,
                    0, maxPages, maxDepth, null, new ArrayList<>(),
                    new HashMap<>(), null
            );
            repository.save(result);

            CompletableFuture.runAsync(() -> {
                try {
                    manager.start();
                    updateCrawlResult(crawlId, "COMPLETED", null);
                } catch (Exception e) {
                    log.error("Crawl {} failed: {}", crawlId, e.getMessage(), e);
                    updateCrawlResult(crawlId, "FAILED", e.getMessage());
                } finally {
                    activeCrawls.remove(crawlId);
                }
            });

            log.info("Started crawl {} with {} URLs, maxPages: {}, maxDepth: {}",
                    crawlId, urls.size(), maxPages, maxDepth);

        } catch (Exception e) {
            log.error("Failed to start crawl {}: {}", crawlId, e.getMessage(), e);
            updateCrawlResult(crawlId, "FAILED", e.getMessage());
            throw new RuntimeException("Failed to start crawling: " + e.getMessage());
        }

        return crawlId;
    }

    public Map<String, Object> getCrawlStatus(String crawlId) {
        CrawlManager manager = activeCrawls.get(crawlId);

        if (manager != null) {
            Map<String, Object> status = new HashMap<>(manager.getStatus());
            status.put("crawlId", crawlId);
            status.put("status", manager.isRunning() ? "RUNNING" : "COMPLETED");
            updateCrawlResultFromManager(crawlId, manager);
            return status;
        }

        return repository.findById(crawlId)
                .map(result -> {
                    Map<String, Object> status = new HashMap<>();
                    status.put("crawlId", result.getCrawlId());
                    status.put("status", result.getStatus());
                    status.put("processedPages", result.getProcessedPages());
                    status.put("maxPages", result.getMaxPages());
                    status.put("maxDepth", result.getMaxDepth());
                    status.put("domain", result.getDomain());
                    status.put("visitedUrls", result.getVisitedUrls());
                    status.put("results", result.getCrawlResults());
                    status.put("startTime", result.getStartTime());
                    status.put("endTime", result.getEndTime());
                    status.put("running", false);
                    status.put("pendingTasks", 0);
                    status.put("queueSize", 0);
                    if (result.getErrorMessage() != null) {
                        status.put("errorMessage", result.getErrorMessage());
                    }
                    return status;
                })
                .orElse(Map.of("status", "NOT_FOUND", "crawlId", crawlId));
    }

    public boolean stopCrawl(String crawlId) {
        CrawlManager manager = activeCrawls.get(crawlId);
        if (manager != null && manager.isRunning()) {
            manager.stop();
            updateCrawlResult(crawlId, "STOPPED", null);
            activeCrawls.remove(crawlId);
            return true;
        }
        return false;
    }

    public List<CrawlResult> getCrawlHistory(int page, int size, Optional<CrawlStatus> status) {
        return repository.findAll(page, size, status);
    }

    public List<CrawlResult> getCrawlHistory() {
        return repository.findAll();
    }

    public void cleanupHistory() {
        repository.cleanup();
    }

    private void updateCrawlResult(String crawlId, String status, String errorMessage) {
        repository.findById(crawlId).ifPresent(result -> {
            result.setStatus(status);
            result.setEndTime(LocalDateTime.now());
            if (errorMessage != null) {
                result.setErrorMessage(errorMessage);
            }

            CrawlManager manager = activeCrawls.get(crawlId);
            if (manager != null) {
                updateCrawlResultFromManager(crawlId, manager);
            } else {
                repository.save(result);
            }
        });
    }

    private void updateCrawlResultFromManager(String crawlId, CrawlManager manager) {
        repository.findById(crawlId).ifPresent(result -> {
            Map<String, Object> managerStatus = manager.getStatus();
            result.setProcessedPages((Integer) managerStatus.get("processedPages"));

            Object domainObj = managerStatus.get("domain");
            Object domainsObj = managerStatus.get("domains");

            if (domainsObj instanceof List) {
                result.setDomain(String.join(", ", (List<String>) domainsObj));
            } else if (domainObj instanceof String) {
                result.setDomain((String) domainObj);
            }

            result.setVisitedUrls((List<String>) managerStatus.get("visitedUrls"));
            result.setCrawlResults((Map<String, List<String>>) managerStatus.get("results"));

            repository.save(result);
        });
    }

    public String handleFileUrls(MultipartFile file, CrawlType strategy, int maxPages, int maxDepth) throws IOException {
        List<String> urls = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))
                .lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toList());
        return startCrawlAsync(urls, strategy, maxPages, maxDepth);

    }
}

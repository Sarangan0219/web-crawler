package com.web.crawler.service;

import com.web.crawler.manager.CrawlManager;
import com.web.crawler.manager.CrawlManagerFactory;
import com.web.crawler.model.CrawlResult;
import com.web.crawler.model.CrawlStatus;
import com.web.crawler.model.CrawlStatusDto;
import com.web.crawler.model.CrawlType;
import com.web.crawler.repository.CrawlRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    @Getter
    private final Map<String, CrawlManager> activeCrawls = new ConcurrentHashMap<>();
    @Value("${crawler.timeout.minutes}")
    private int crawlTimeoutMinutes;

    public String startCrawlAsync(List<String> urls, CrawlType type, int maxPages, int maxDepth) {
        String crawlId = UUID.randomUUID().toString();

        CrawlManager manager;
        try {
            manager = crawlManagerFactory.create(urls, type, maxPages, maxDepth, crawlTimeoutMinutes);
        } catch (Exception e) {
            log.error("Failed to create crawl manager for {}: {}", crawlId, e.getMessage(), e);
            throw new IllegalArgumentException("Invalid crawl configuration: " + e.getMessage(), e);
        }

        activeCrawls.put(crawlId, manager);

        CrawlResult result = new CrawlResult(
                crawlId, type, CrawlStatus.RUNNING, LocalDateTime.now(), null,
                0, maxPages, maxDepth, null, new ArrayList<>(),
                new HashMap<>(), null
        );
        repository.save(result);

        CompletableFuture.runAsync(() -> {
            try {
                manager.start();
                updateCrawlResult(crawlId, CrawlStatus.COMPLETED, null);
            } catch (Exception e) {
                log.error("Crawl {} failed: {}", crawlId, e.getMessage(), e);
                updateCrawlResult(crawlId, CrawlStatus.FAILED, e.getMessage());
            } finally {
                activeCrawls.remove(crawlId);
            }
        });

        log.info("Started crawl {} with {} URLs, maxPages: {}, maxDepth: {}",
                crawlId, urls.size(), maxPages, maxDepth);

        return crawlId;
    }

    public CrawlStatusDto getCrawlStatusDto(String crawlId) {
        CrawlManager manager = activeCrawls.get(crawlId);

        if (manager != null) {
            Map<String, Object> status = manager.getStatus();

            return CrawlStatusDto.builder()
                    .crawlId(crawlId)
                    .status(manager.isRunning() ? CrawlStatus.RUNNING : CrawlStatus.COMPLETED)
                    .processedPages((Integer) status.getOrDefault("processedPages", 0))
                    .maxPages((Integer) status.getOrDefault("maxPages", 0))
                    .maxDepth((Integer) status.getOrDefault("maxDepth", 0))
                    .domain(status.getOrDefault("domains", List.of()).toString())
                    .results((Map<String, List<String>>) status.getOrDefault("results", Map.of()))
                    .startTime((LocalDateTime) status.get("startTime"))
                    .endTime((LocalDateTime) status.get("endTime"))
                    .running(manager.isRunning())
                    .pendingTasks((Integer) status.getOrDefault("pendingTasks", 0))
                    .queueSize((Integer) status.getOrDefault("queueSize", 0))
                    .errorMessage((String) status.get("errorMessage"))
                    .build();
        }

        return repository.findById(crawlId)
                .map(result -> CrawlStatusDto.builder()
                        .crawlId(result.getCrawlId())
                        .status(result.getStatus())
                        .processedPages(result.getProcessedPages())
                        .maxPages(result.getMaxPages())
                        .maxDepth(result.getMaxDepth())
                        .domain(result.getDomain())
                        .results(result.getCrawlResults())
                        .startTime(result.getStartTime())
                        .endTime(result.getEndTime())
                        .running(false)
                        .pendingTasks(0)
                        .queueSize(0)
                        .errorMessage(result.getErrorMessage())
                        .build())
                .orElseThrow(() -> new IllegalArgumentException("Crawl ID not found: " + crawlId));
    }


    public boolean stopCrawl(String crawlId) {
        CrawlManager manager = activeCrawls.get(crawlId);
        if (manager != null && manager.isRunning()) {
            manager.stop();
            updateCrawlResult(crawlId, CrawlStatus.STOPPED, null);
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

    private void updateCrawlResult(String crawlId, CrawlStatus status, String errorMessage) {
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

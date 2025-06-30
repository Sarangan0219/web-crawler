package com.web.crawler.repository;

import com.web.crawler.model.CrawlResult;
import com.web.crawler.model.CrawlStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Slf4j
public class InMemoryCrawlRepository implements CrawlRepository {

    private final Map<String, CrawlResult> crawlHistory = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY_SIZE = 100;

    @Override
    public void save(CrawlResult crawlResult) {
        crawlHistory.put(crawlResult.getCrawlId(), crawlResult);
        log.debug("Saved crawl result for ID: {}", crawlResult.getCrawlId());
    }

    @Override
    public Optional<CrawlResult> findById(String crawlId) {
        return Optional.ofNullable(crawlHistory.get(crawlId));
    }

    @Override
    public List<CrawlResult> findAll() {
        return List.of();
    }

    @Override
    public void deleteById(String crawlId) {
        CrawlResult removed = crawlHistory.remove(crawlId);
        if (removed != null) {
            log.debug("Deleted crawl result for ID: {}", crawlId);
        }
    }

    @Override
    public void cleanup() {
        if (crawlHistory.size() > MAX_HISTORY_SIZE) {
            List<CrawlResult> sorted = crawlHistory.values().stream()
                    .sorted((a, b) -> b.getStartTime().compareTo(a.getStartTime()))
                    .toList();

            crawlHistory.clear();
            sorted.stream().limit(MAX_HISTORY_SIZE).forEach(result ->
                    crawlHistory.put(result.getCrawlId(), result));

            log.info("Cleaned up crawl history, keeping {} most recent entries", MAX_HISTORY_SIZE);
        }
    }

    @Override
    public boolean existsById(String crawlId) {
        return crawlHistory.containsKey(crawlId);
    }

    @Override
    public List<CrawlResult> findAll(int page, int size, Optional<CrawlStatus> statusFilter) {
        return crawlHistory.values().stream()
                .filter(result -> statusFilter.isEmpty()
                        || statusFilter.get().name().equalsIgnoreCase(result.getStatus().toString()))
                .sorted(Comparator.comparing(CrawlResult::getStartTime).reversed())
                .skip((long) page * size)
                .limit(size)
                .toList();
    }


}
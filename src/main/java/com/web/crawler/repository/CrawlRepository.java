package com.web.crawler.repository;

import com.web.crawler.model.CrawlResult;
import com.web.crawler.model.CrawlStatus;

import java.util.List;
import java.util.Optional;

public interface CrawlRepository {
    void save(CrawlResult crawlResult);

    Optional<CrawlResult> findById(String crawlId);

    List<CrawlResult> findAll();

    void deleteById(String crawlId);

    void cleanup();

    boolean existsById(String crawlId);

    List<CrawlResult> findAll(int page, int size, Optional<CrawlStatus> statusFilter);
}

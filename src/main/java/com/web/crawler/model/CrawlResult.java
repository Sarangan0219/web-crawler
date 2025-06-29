package com.web.crawler.model;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
public class CrawlResult {
    private String crawlId;
    private CrawlType strategy;
    private String status; // RUNNING, COMPLETED, FAILED, STOPPED
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int processedPages;
    private int maxPages;
    private int maxDepth;
    private String domain;
    private List<String> visitedUrls;
    private Map<String, List<String>> crawlResults;
    private String errorMessage;
}


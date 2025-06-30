package com.web.crawler.model;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CrawlResult {
    private String crawlId;
    private CrawlType strategy;
    private CrawlStatus status;
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


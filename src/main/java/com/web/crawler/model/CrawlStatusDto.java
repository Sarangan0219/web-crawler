package com.web.crawler.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlStatusDto {
    private String crawlId;
    private CrawlStatus status;
    private int processedPages;
    private int maxPages;
    private int maxDepth;
    private String domain;
    private List<String> visitedUrls;
    private Map<String, List<String>> results;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean running;
    private int pendingTasks;
    private int queueSize;
    private String errorMessage;
}


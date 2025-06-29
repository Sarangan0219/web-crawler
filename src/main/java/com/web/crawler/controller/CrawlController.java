package com.web.crawler.controller;

import com.web.crawler.model.CrawlRequest;
import com.web.crawler.model.CrawlResult;
import com.web.crawler.model.CrawlStatus;
import com.web.crawler.model.CrawlType;
import com.web.crawler.service.CrawlService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/crawlers")
@AllArgsConstructor
public class CrawlController {

    private final CrawlService crawlService;

    @PostMapping(value = "/upload", consumes = {"multipart/form-data"})
    public ResponseEntity<Map<String, String>> initiateCrawlFromFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam("strategy") CrawlType strategy,
            @RequestParam(defaultValue = "100") int maxPages,
            @RequestParam(defaultValue = "5") int maxDepth) {

        try {
            String crawlId = crawlService.handleFileUrls(file,strategy, maxPages, maxDepth );

            return ResponseEntity.ok(Map.of(
                    "crawlId", crawlId,
                    "message", "Crawl started from file upload",
                    "status", "RUNNING"
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to process uploaded file: " + e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> initiateCrawling(@Valid @RequestBody CrawlRequest request) {
        String crawlId = crawlService.startCrawlAsync(request.getUrls(),  request.getStrategy(),
                request.getMaxPages(), request.getMaxDepth());

        return ResponseEntity.ok(Map.of(
                "crawlId", crawlId,
                "message", "Crawl started with strategy: " + request.getStrategy(),
                "status", "RUNNING"
        ));
    }

    @GetMapping("/{crawlId}/status")
    public ResponseEntity<Map<String, Object>> getCrawlStatus(@PathVariable String crawlId) {
        return ResponseEntity.ok(crawlService.getCrawlStatus(crawlId));
    }

    @PostMapping("/{crawlId}/stop")
    public ResponseEntity<Map<String, String>> stopCrawl(@PathVariable String crawlId) {
        boolean stopped = crawlService.stopCrawl(crawlId);
        return ResponseEntity.ok(Map.of(
                "crawlId", crawlId,
                "status", stopped ? "STOPPED" : "NOT_FOUND"
        ));
    }

    @GetMapping("/history")
    public ResponseEntity<List<CrawlResult>> getCrawlHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) CrawlStatus status
    ) {
        return ResponseEntity.ok(crawlService.getCrawlHistory(page, size, Optional.ofNullable(status)));
    }


    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, String>> cleanupHistory() {
        crawlService.cleanupHistory();
        return ResponseEntity.ok(Map.of("message", "History cleaned up"));
    }
}
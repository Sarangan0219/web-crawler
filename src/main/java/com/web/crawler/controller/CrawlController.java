package com.web.crawler.controller;

import com.web.crawler.model.*;
import com.web.crawler.service.CrawlService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/crawlers")
@AllArgsConstructor
public class CrawlController {

    private final CrawlService crawlService;

    @PostMapping(value = "/upload", consumes = {"multipart/form-data"})
    public ResponseEntity<CrawlResponseDto> initiateCrawlFromFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam("strategy") CrawlType strategy,
            @RequestParam(defaultValue = "100") int maxPages,
            @RequestParam(defaultValue = "5") int maxDepth) {

        try {
            String crawlId = crawlService.handleFileUrls(file, strategy, maxPages, maxDepth);
            return ResponseEntity.ok(CrawlResponseDto.builder()
                    .crawlId(crawlId)
                    .status(CrawlStatus.RUNNING)
                    .message("Crawl started from file upload")
                    .timestamp(LocalDateTime.now())
                    .build()
            );
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(CrawlResponseDto.builder()
                    .status(CrawlStatus.FAILED)
                    .message("Failed to process uploaded file: " + e.getMessage())
                    .timestamp(LocalDateTime.now())
                    .build()
            );
        }
    }

    @PostMapping
    public ResponseEntity<CrawlResponseDto> initiateCrawling(@Valid @RequestBody CrawlRequest request) {
        String crawlId = crawlService.startCrawlAsync(
                request.getUrls(),
                request.getStrategy(),
                request.getMaxPages(),
                request.getMaxDepth()
        );

        return ResponseEntity.ok(CrawlResponseDto.builder()
                .crawlId(crawlId)
                .status(CrawlStatus.RUNNING)
                .message("Crawl started with strategy: " + request.getStrategy())
                .timestamp(LocalDateTime.now())
                .build()
        );
    }

    @GetMapping("/{crawlId}/status")
    public ResponseEntity<CrawlStatusDto> getCrawlStatus(@PathVariable String crawlId) {
        return ResponseEntity.ok(crawlService.getCrawlStatusDto(crawlId));
    }

    @PostMapping("/{crawlId}/stop")
    public ResponseEntity<CrawlResponseDto> stopCrawl(@PathVariable String crawlId) {
        boolean stopped = crawlService.stopCrawl(crawlId);

        return ResponseEntity.ok(CrawlResponseDto.builder()
                .crawlId(crawlId)
                .status(stopped ? CrawlStatus.STOPPED : CrawlStatus.NOT_FOUND)
                .message(stopped ? "Crawl stopped successfully" : "Crawl not found or already completed")
                .timestamp(LocalDateTime.now())
                .build()
        );
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
    public ResponseEntity<CrawlResponseDto> cleanupHistory() {
        crawlService.cleanupHistory();
        return ResponseEntity.ok(CrawlResponseDto.builder()
                .status(CrawlStatus.COMPLETED)
                .message("History cleaned up")
                .timestamp(LocalDateTime.now())
                .build()
        );
    }
}

package com.web.crawler.service;

import com.web.crawler.manager.SingleDomainCrawlManager;
import com.web.crawler.util.HtmlParserUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class CrawlWorker implements Runnable {

    private final String url;
    private final int depth;
    private final SingleDomainCrawlManager manager;

    public CrawlWorker(String url, int depth, SingleDomainCrawlManager manager) {
        this.url = url;
        this.depth = depth;
        this.manager = manager;
    }

    @Override
    public void run() {
        try {
            List<String> extractedUrls = HtmlParserUtil.extractLinks(url);
            manager.recordCrawlResult(url, extractedUrls);

            // Enqueue found URLs for next depth level
            for (String foundUrl : extractedUrls) {
                manager.enqueueUrl(foundUrl, depth + 1);
            }

        } catch (Exception e) {
            log.error("❌ Error processing: {} — {}", url, e.getMessage());
        } finally {
            manager.taskCompleted();
        }
    }
}
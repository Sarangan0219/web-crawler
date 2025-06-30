package com.web.crawler.service;

import com.web.crawler.manager.SingleDomainCrawlManager;
import com.web.crawler.util.HtmlParserUtil;
import com.web.crawler.util.UrlUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

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

            // Filter to only same-domain links before processing
            String targetDomain = UrlUtils.extractDomain(url);
            List<String> sameDomainUrls = extractedUrls.stream()
                    .filter(link -> UrlUtils.isSameDomain(link, targetDomain))
                    .collect(Collectors.toList());

            manager.recordCrawlResult(url, sameDomainUrls);

            // Enqueue only same-domain URLs for next depth level
            for (String foundUrl : sameDomainUrls) {
                manager.enqueueUrl(foundUrl, depth + 1);
            }

        } catch (Exception e) {
            log.error("Error processing: {} — {}", url, e.getMessage());
        } finally {
            manager.taskCompleted();
        }
    }
}
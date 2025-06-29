
package com.web.crawler.manager;

import com.web.crawler.model.CrawlType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CrawlManagerFactory {

    public CrawlManager create(List<String> urls, CrawlType type, int maxPages, int maxDepth) {
        return switch (type) {
            case SINGLE_DOMAIN -> new SingleDomainCrawlManager(urls, maxPages, maxDepth);
            case MULTI_DOMAIN -> new MultiDomainCrawlManager(urls);
            default -> throw new IllegalArgumentException("Unsupported crawl type: " + type);
        };
    }
}



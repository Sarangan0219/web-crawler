
package com.web.crawler.manager;

import com.web.crawler.model.CrawlType;
import org.springframework.stereotype.Component;

import java.util.List;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class CrawlManagerFactory {

    public CrawlManager create(List<String> urls, CrawlType type, int maxPages, int maxDepth) {
        Objects.requireNonNull(type, "CrawlType must not be null");

        return switch (type) {
            case SINGLE_DOMAIN -> new SingleDomainCrawlManager(urls, maxPages, maxDepth);
            case MULTI_DOMAIN -> new MultiDomainCrawlManager(urls);
        };
    }
}



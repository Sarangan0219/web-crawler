package com.web.crawler.manager;

import java.util.List;
import java.util.Map;

public class MultiDomainCrawlManager implements CrawlManager {

    public MultiDomainCrawlManager(List<String> urls) {
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Override
    public Map<String, Object> getStatus() {
        return Map.of();
    }

    @Override
    public void enqueueUrl(String url, int depth) {

    }
}

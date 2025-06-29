package com.web.crawler.manager;

import java.util.Map;

public interface CrawlManager {
    void start();
    void stop();
    boolean isRunning();
    Map<String, Object> getStatus();
    void enqueueUrl(String url, int depth);
}

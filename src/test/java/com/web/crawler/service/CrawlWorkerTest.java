package com.web.crawler.service;

import com.web.crawler.manager.SingleDomainCrawlManager;
import com.web.crawler.util.HtmlParserUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.mockito.Mockito.*;

class CrawlWorkerTest {

    @Test
    void testRun_successfulCrawl() {
        String url = "https://monzo.com";
        int depth = 1;
        List<String> extractedLinks = List.of("https://monzo.com/business-banking",
                "https://monzo.com/sign-up");

        SingleDomainCrawlManager mockManager = mock(SingleDomainCrawlManager.class);

        try (MockedStatic<HtmlParserUtil> mockedStatic = mockStatic(HtmlParserUtil.class)) {
            mockedStatic.when(() -> HtmlParserUtil.extractLinks(url)).thenReturn(extractedLinks);
            CrawlWorker worker = new CrawlWorker(url, depth, mockManager);
            worker.run();
            verify(mockManager).recordCrawlResult(url, extractedLinks);
            verify(mockManager).enqueueUrl("https://monzo.com/business-banking", 2);
            verify(mockManager).enqueueUrl("https://monzo.com/sign-up", 2);
            verify(mockManager).taskCompleted();
        }
    }

    @Test
    void testRun_withExceptionInExtraction() {
        String url = "https://bad-url.com";
        int depth = 1;

        SingleDomainCrawlManager mockManager = mock(SingleDomainCrawlManager.class);
        try (MockedStatic<HtmlParserUtil> mockedStatic = mockStatic(HtmlParserUtil.class)) {
            mockedStatic.when(() -> HtmlParserUtil.extractLinks(url)).thenThrow(new RuntimeException("Parse failure"));
            CrawlWorker worker = new CrawlWorker(url, depth, mockManager);
            worker.run();
            verify(mockManager, never()).recordCrawlResult(any(), any());
            verify(mockManager, never()).enqueueUrl(any(), anyInt());
            verify(mockManager).taskCompleted();
        }
    }
}

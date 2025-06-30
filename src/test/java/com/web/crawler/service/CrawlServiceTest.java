package com.web.crawler.service;

import com.web.crawler.manager.CrawlManager;
import com.web.crawler.manager.CrawlManagerFactory;
import com.web.crawler.model.*;
import com.web.crawler.repository.CrawlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CrawlServiceTest {

    @Mock
    private CrawlManagerFactory crawlManagerFactory;

    @Mock
    private CrawlRepository crawlRepository;

    @Mock
    private CrawlManager crawlManager;

    @InjectMocks
    private CrawlService crawlService;

    List<String> urls;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        urls = List.of("https://monzo.com/");
    }

    @Test
    void testStartCrawlAsync() {
        when(crawlManagerFactory.create(any(), any(), anyInt(), anyInt(), anyInt())).thenReturn(crawlManager);
        String crawlId = crawlService.startCrawlAsync(urls, CrawlType.SINGLE_DOMAIN, 10, 2);
        assertNotNull(crawlId);
        verify(crawlRepository).save(any(CrawlResult.class));
    }


    @Test
    void testGetCrawlStatusDto_fromManager() {
        String crawlId = "crawl-id";

        Map<String, Object> status = new HashMap<>();
        status.put("processedPages", 5);
        status.put("maxPages", 10);
        status.put("maxDepth", 2);
        status.put("domain", "example.com");
        status.put("visitedUrls", urls);
        status.put("results", Map.of("http://monzo.com", List.of("link")));
        status.put("startTime", LocalDateTime.now());
        status.put("endTime", LocalDateTime.now());

        when(crawlManager.getStatus()).thenReturn(status);
        when(crawlManager.isRunning()).thenReturn(true);

        crawlService.getActiveCrawls().put(crawlId, crawlManager);
        CrawlStatusDto dto = crawlService.getCrawlStatusDto(crawlId);

        assertEquals(CrawlStatus.RUNNING, dto.getStatus());
        assertEquals(5, dto.getProcessedPages());
    }

    @Test
    void testHandleFileUrls() throws Exception {
        String content = "http://monzo.com\nhttp://example.org";
        MockMultipartFile file = new MockMultipartFile(
                "file", "urls.txt", "text/plain",
                new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))
        );

        when(crawlManagerFactory.create(anyList(), any(), anyInt(), anyInt(), anyInt())).thenReturn(crawlManager);

        String crawlId = crawlService.handleFileUrls(file, CrawlType.SINGLE_DOMAIN, 5, 1);
        assertNotNull(crawlId);
    }

    @Test
    void testGetCrawlStatusDto_fromRepository() {
        String crawlId = "stored-id";

        CrawlResult result = new CrawlResult();
        result.setCrawlId(crawlId);
        result.setStatus(CrawlStatus.COMPLETED);
        result.setProcessedPages(5);
        result.setMaxPages(10);
        result.setMaxDepth(2);
        result.setVisitedUrls(urls);
        result.setCrawlResults(Map.of("http://monzo.com", List.of("link")));

        when(crawlRepository.findById(crawlId)).thenReturn(Optional.of(result));

        CrawlStatusDto dto = crawlService.getCrawlStatusDto(crawlId);

        assertEquals(CrawlStatus.COMPLETED, dto.getStatus());
        assertEquals(5, dto.getProcessedPages());
    }

    @Test
    void testGetCrawlStatusDto_notFound() {
        when(crawlRepository.findById("missing")).thenReturn(Optional.empty());
        Exception ex = assertThrows(IllegalArgumentException.class,
                () -> crawlService.getCrawlStatusDto("missing"));
        assertTrue(ex.getMessage().contains("Crawl ID not found"));
    }
}

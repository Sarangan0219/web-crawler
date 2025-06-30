package com.web.crawler.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.crawler.model.*;
import com.web.crawler.service.CrawlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
public class CrawlControllerITTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private CrawlService crawlService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String BASE_URL = "/api/v1/crawlers";
    private static final String TEST_CRAWL_ID = "test-crawl-123";

    @Test
    void initiateCrawl_returnsRunningStatus() {
        // Arrange
        CrawlRequest request = CrawlRequest.builder()
                .urls(List.of("https://example.com"))
                .strategy(CrawlType.SINGLE_DOMAIN)
                .maxDepth(2)
                .maxPages(10)
                .build();

        when(crawlService.startCrawlAsync(request.getUrls(), request.getStrategy(),
                request.getMaxPages(), request.getMaxDepth()))
                .thenReturn(TEST_CRAWL_ID);

        // Act & Assert
        webTestClient
                .post()
                .uri(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus().isOk()
                .expectBody(CrawlResponseDto.class)
                .value(res -> {
                    assertThat(res.getCrawlId()).isEqualTo(TEST_CRAWL_ID);
                    assertThat(res.getStatus()).isEqualTo(CrawlStatus.RUNNING);
                    assertThat(res.getMessage()).contains("Crawl started");
                    assertThat(res.getTimestamp()).isBeforeOrEqualTo(LocalDateTime.now());
                });

        verify(crawlService).startCrawlAsync(request.getUrls(), request.getStrategy(),
                request.getMaxPages(), request.getMaxDepth());
    }

    @Test
    void initiateCrawl_withInvalidRequest_returnsBadRequest() {
        // Arrange - Invalid request with null URLs
        CrawlRequest request = CrawlRequest.builder()
                .urls(null)
                .strategy(CrawlType.SINGLE_DOMAIN)
                .maxDepth(2)
                .maxPages(10)
                .build();

        // Act & Assert
        webTestClient
                .post()
                .uri(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus().isBadRequest();

        verify(crawlService, never()).startCrawlAsync(anyList(), any(CrawlType.class), anyInt(), anyInt());
    }

    @Test
    void getCrawlStatus_returnsStatusForValidId() {
        // Arrange
        CrawlStatusDto statusDto = CrawlStatusDto.builder()
                .crawlId(TEST_CRAWL_ID)
                .status(CrawlStatus.RUNNING)
                .processedPages(150)
                .startTime(LocalDateTime.now().minusMinutes(10))
                .build();

        when(crawlService.getCrawlStatusDto(TEST_CRAWL_ID)).thenReturn(statusDto);

        // Act & Assert
        webTestClient
                .get()
                .uri(BASE_URL + "/{crawlId}/status", TEST_CRAWL_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody(CrawlStatusDto.class)
                .value(res -> {
                    assertThat(res.getCrawlId()).isEqualTo(TEST_CRAWL_ID);
                    assertThat(res.getStatus()).isEqualTo(CrawlStatus.RUNNING);
                    assertThat(res.getProcessedPages()).isEqualTo(150);
                    assertThat(res.getStartTime()).isNotNull();
                });

        verify(crawlService).getCrawlStatusDto(TEST_CRAWL_ID);
    }

    @Test
    void getCrawlStatus_nonExistentId_returnsNotFound() {
        // Arrange
        String nonExistentId = "non-existent-123";
        CrawlStatusDto statusDto = CrawlStatusDto.builder()
                .crawlId(nonExistentId)
                .status(CrawlStatus.NOT_FOUND)
                .build();

        when(crawlService.getCrawlStatusDto(nonExistentId)).thenReturn(statusDto);

        // Act & Assert
        webTestClient
                .get()
                .uri(BASE_URL + "/{crawlId}/status", nonExistentId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(CrawlStatusDto.class)
                .value(res -> {
                    assertThat(res.getCrawlId()).isEqualTo(nonExistentId);
                    assertThat(res.getStatus()).isEqualTo(CrawlStatus.NOT_FOUND);
                });

        verify(crawlService).getCrawlStatusDto(nonExistentId);
    }

    @Test
    void stopCrawl_returnsStoppedStatus() {
        // Arrange
        when(crawlService.stopCrawl(TEST_CRAWL_ID)).thenReturn(true);

        // Act & Assert
        webTestClient
                .post()
                .uri(BASE_URL + "/{crawlId}/stop", TEST_CRAWL_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody(CrawlResponseDto.class)
                .value(res -> {
                    assertThat(res.getCrawlId()).isEqualTo(TEST_CRAWL_ID);
                    assertThat(res.getStatus()).isEqualTo(CrawlStatus.STOPPED);
                    assertThat(res.getMessage()).isEqualTo("Crawl stopped successfully");
                    assertThat(res.getTimestamp()).isBeforeOrEqualTo(LocalDateTime.now());
                });

        verify(crawlService).stopCrawl(TEST_CRAWL_ID);
    }

    @Test
    void stopCrawl_nonExistentId_returnsNotFound() {
        // Arrange
        String nonExistentId = "non-existent-123";
        when(crawlService.stopCrawl(nonExistentId)).thenReturn(false);

        // Act & Assert
        webTestClient
                .post()
                .uri(BASE_URL + "/{crawlId}/stop", nonExistentId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(CrawlResponseDto.class)
                .value(res -> {
                    assertThat(res.getCrawlId()).isEqualTo(nonExistentId);
                    assertThat(res.getStatus()).isEqualTo(CrawlStatus.NOT_FOUND);
                    assertThat(res.getMessage()).isEqualTo("Crawl not found or already completed");
                });

        verify(crawlService).stopCrawl(nonExistentId);
    }

    @Test
    void getCrawlHistory_withDefaultParams_returnsHistory() {
        // Arrange
        List<CrawlResult> history = Arrays.asList(
                createMockCrawlResult("crawl-1", CrawlStatus.COMPLETED),
                createMockCrawlResult("crawl-2", CrawlStatus.RUNNING),
                createMockCrawlResult("crawl-3", CrawlStatus.FAILED)
        );

        when(crawlService.getCrawlHistory(0, 10, Optional.empty())).thenReturn(history);

        // Act & Assert
        webTestClient
                .get()
                .uri(BASE_URL + "/history")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(CrawlResult.class)
                .value(results -> {
                    assertThat(results).hasSize(3);
                    assertThat(results.get(0).getCrawlId()).isEqualTo("crawl-1");
                    assertThat(results.get(0).getStatus()).isEqualTo(CrawlStatus.COMPLETED);
                    assertThat(results.get(1).getCrawlId()).isEqualTo("crawl-2");
                    assertThat(results.get(1).getStatus()).isEqualTo(CrawlStatus.RUNNING);
                    assertThat(results.get(2).getCrawlId()).isEqualTo("crawl-3");
                    assertThat(results.get(2).getStatus()).isEqualTo(CrawlStatus.FAILED);
                });

        verify(crawlService).getCrawlHistory(0, 10, Optional.empty());
    }

    @Test
    void getCrawlHistory_withCustomParams_returnsFilteredHistory() {
        // Arrange
        List<CrawlResult> history = Arrays.asList(
                createMockCrawlResult("crawl-1", CrawlStatus.COMPLETED)
        );

        when(crawlService.getCrawlHistory(1, 5, Optional.of(CrawlStatus.COMPLETED))).thenReturn(history);

        // Act & Assert
        webTestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path(BASE_URL + "/history")
                        .queryParam("page", "1")
                        .queryParam("size", "5")
                        .queryParam("status", "COMPLETED")
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(CrawlResult.class)
                .value(results -> {
                    assertThat(results).hasSize(1);
                    assertThat(results.get(0).getStatus()).isEqualTo(CrawlStatus.COMPLETED);
                });

        verify(crawlService).getCrawlHistory(1, 5, Optional.of(CrawlStatus.COMPLETED));
    }

    @Test
    void getCrawlHistory_emptyResult_returnsEmptyList() {
        // Arrange
        when(crawlService.getCrawlHistory(0, 10, Optional.empty())).thenReturn(Arrays.asList());

        // Act & Assert
        webTestClient
                .get()
                .uri(BASE_URL + "/history")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(CrawlResult.class)
                .value(results -> assertThat(results).isEmpty());

        verify(crawlService).getCrawlHistory(0, 10, Optional.empty());
    }

    @Test
    void cleanupHistory_returnsCompletedStatus() {
        // Arrange
        doNothing().when(crawlService).cleanupHistory();

        // Act & Assert
        webTestClient
                .post()
                .uri(BASE_URL + "/cleanup")
                .exchange()
                .expectStatus().isOk()
                .expectBody(CrawlResponseDto.class)
                .value(res -> {
                    assertThat(res.getStatus()).isEqualTo(CrawlStatus.COMPLETED);
                    assertThat(res.getMessage()).isEqualTo("History cleaned up");
                    assertThat(res.getTimestamp()).isBeforeOrEqualTo(LocalDateTime.now());
                    assertThat(res.getCrawlId()).isNull();
                });

        verify(crawlService).cleanupHistory();
    }

    @Test
    void endToEndCrawlWorkflow_completesSuccessfully() {
        // Arrange
        CrawlRequest request = CrawlRequest.builder()
                .urls(List.of("https://example.com"))
                .strategy(CrawlType.SINGLE_DOMAIN)
                .maxDepth(1)
                .maxPages(5)
                .build();

        when(crawlService.startCrawlAsync(anyList(), any(CrawlType.class), anyInt(), anyInt()))
                .thenReturn(TEST_CRAWL_ID);

        CrawlStatusDto runningStatus = CrawlStatusDto.builder()
                .crawlId(TEST_CRAWL_ID)
                .status(CrawlStatus.RUNNING)
                .build();

        CrawlStatusDto completedStatus = CrawlStatusDto.builder()
                .crawlId(TEST_CRAWL_ID)
                .status(CrawlStatus.COMPLETED)
                .build();

        when(crawlService.getCrawlStatusDto(TEST_CRAWL_ID))
                .thenReturn(runningStatus)
                .thenReturn(completedStatus);

        when(crawlService.stopCrawl(TEST_CRAWL_ID)).thenReturn(false); // Already completed

        // Act & Assert - Start crawl
        String crawlId = webTestClient
                .post()
                .uri(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus().isOk()
                .expectBody(CrawlResponseDto.class)
                .returnResult()
                .getResponseBody()
                .getCrawlId();

        assertThat(crawlId).isEqualTo(TEST_CRAWL_ID);

        // Check status while running
        webTestClient
                .get()
                .uri(BASE_URL + "/{crawlId}/status", crawlId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(CrawlStatusDto.class)
                .value(res -> {
                    assertThat(res.getStatus()).isEqualTo(CrawlStatus.RUNNING);
                });

        // Check status after completion
        webTestClient
                .get()
                .uri(BASE_URL + "/{crawlId}/status", crawlId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(CrawlStatusDto.class)
                .value(res -> {
                    assertThat(res.getStatus()).isEqualTo(CrawlStatus.COMPLETED);
                });

        // Try to stop already completed crawl
        webTestClient
                .post()
                .uri(BASE_URL + "/{crawlId}/stop", crawlId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(CrawlResponseDto.class)
                .value(res -> assertThat(res.getStatus()).isEqualTo(CrawlStatus.NOT_FOUND));
    }

    @Test
    void testInvalidPathParameters_handleGracefully() {
        // Test with invalid crawl ID
        CrawlStatusDto notFoundStatus = CrawlStatusDto.builder()
                .crawlId("invalid-id")
                .status(CrawlStatus.NOT_FOUND)
                .build();

        when(crawlService.getCrawlStatusDto("invalid-id")).thenReturn(notFoundStatus);

        webTestClient
                .get()
                .uri(BASE_URL + "/{crawlId}/status", "invalid-id")
                .exchange()
                .expectStatus().isOk()
                .expectBody(CrawlStatusDto.class)
                .value(res -> assertThat(res.getStatus()).isEqualTo(CrawlStatus.NOT_FOUND));
    }

    private CrawlResult createMockCrawlResult(String crawlId, CrawlStatus status) {
        return CrawlResult.builder()
                .crawlId(crawlId)
                .status(status)
                .startTime(LocalDateTime.now().minusHours(1))
                .endTime(status == CrawlStatus.COMPLETED ? LocalDateTime.now() : null)
                .processedPages(status == CrawlStatus.COMPLETED ? 100 : 50)
                .strategy(CrawlType.SINGLE_DOMAIN)
                .build();
    }
}
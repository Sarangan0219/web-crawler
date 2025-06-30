package com.web.crawler.integration;

import static org.assertj.core.api.Assertions.assertThat;

//import com.web.crawler.model.CrawlRequest;
//import com.web.crawler.model.CrawlResponseDto;
//import com.web.crawler.model.CrawlStatus;
//import com.web.crawler.model.CrawlStatusDto;
//import java.time.LocalDateTime;
//import java.util.List;
//import java.util.UUID;
//import org.junit.jupiter.api.Test;
//import org.springframework.core.io.ClassPathResource;
//import org.springframework.http.MediaType;
//import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;
//import org.springframework.web.reactive.function.BodyInserters;

/**
 * Integration tests for {@link com.web.crawler.controller.CrawlController}.
 */
public class CrawlControllerITTest {

//    @Test
//    void initiateCrawl_returnsRunningStatus() throws Exception {
//        // Arrange
//        CrawlRequest request =
//                CrawlRequest.builder()
//                        .urls(List.of("https://example.com"))
//                        .strategy("BFS")
//                        .maxDepth(2)
//                        .maxPages(10)
//                        .build();
//
//        // Act
//        ResponseSpec response =
//                httpClient
//                        .post()
//                        .uri("/api/v1/crawlers")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .body(BodyInserters.fromValue(request))
//                        .exchange();
//
//        // Assert
//        response
//                .expectStatus()
//                .isOk()
//                .expectBody(CrawlResponseDto.class)
//                .value(
//                        res -> {
//                            assertThat(res.getCrawlId()).isNotBlank();
//                            assertThat(res.getStatus()).isEqualTo(CrawlStatus.RUNNING);
//                            assertThat(res.getMessage()).contains("Crawl started");
//                            assertThat(res.getTimestamp()).isBeforeOrEqualTo(LocalDateTime.now());
//                        });
//    }
//
//    @Test
//    void getCrawlStatus_returnsStatusForValidId() throws Exception {
//        // Arrange
//        CrawlRequest request =
//                CrawlRequest.builder()
//                        .urls(List.of("https://example.com"))
//                        .strategy("BFS")
//                        .maxDepth(1)
//                        .maxPages(5)
//                        .build();
//
//        String crawlId =
//                httpClient
//                        .post()
//                        .uri("/api/v1/crawlers")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .body(BodyInserters.fromValue(request))
//                        .exchange()
//                        .expectBody(CrawlResponseDto.class)
//                        .returnResult()
//                        .getResponseBody()
//                        .getCrawlId();
//
//        // Act
//        ResponseSpec response =
//                httpClient.get().uri("/api/v1/crawlers/" + crawlId + "/status").exchange();
//
//        // Assert
//        response
//                .expectStatus()
//                .isOk()
//                .expectBody(CrawlStatusDto.class)
//                .value(res -> {
//                    assertThat(res.getCrawlId()).isEqualTo(crawlId);
//                    assertThat(res.getStatus()).isNotNull();
//                });
//    }
//
//    @Test
//    void stopCrawl_returnsStoppedStatus() throws Exception {
//        // Arrange
//        CrawlRequest request =
//                CrawlRequest.builder()
//                        .urls(List.of("https://example.com"))
//                        .strategy("BFS")
//                        .maxDepth(1)
//                        .maxPages(5)
//                        .build();
//
//        String crawlId =
//                httpClient
//                        .post()
//                        .uri("/api/v1/crawlers")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .body(BodyInserters.fromValue(request))
//                        .exchange()
//                        .expectBody(CrawlResponseDto.class)
//                        .returnResult()
//                        .getResponseBody()
//                        .getCrawlId();
//
//        // Act
//        ResponseSpec response =
//                httpClient.post().uri("/api/v1/crawlers/" + crawlId + "/stop").exchange();
//
//        // Assert
//        response
//                .expectStatus()
//                .isOk()
//                .expectBody(CrawlResponseDto.class)
//                .value(res -> {
//                    assertThat(res.getCrawlId()).isEqualTo(crawlId);
//                    assertThat(res.getStatus()).isIn(CrawlStatus.STOPPED, CrawlStatus.NOT_FOUND);
//                });
//    }
//
//    @Test
//    void cleanupHistory_returnsCompletedStatus() {
//        // Act
//        ResponseSpec response = httpClient.post().uri("/api/v1/crawlers/cleanup").exchange();
//
//        // Assert
//        response
//                .expectStatus()
//                .isOk()
//                .expectBody(CrawlResponseDto.class)
//                .value(res -> {
//                    assertThat(res.getStatus()).isEqualTo(CrawlStatus.COMPLETED);
//                    assertThat(res.getMessage()).isEqualTo("History cleaned up");
//                });
//    }
//
//    @Test
//    void getCrawlHistory_returnsPageOfResults() {
//        // Act
//        ResponseSpec response = httpClient.get().uri("/api/v1/crawlers/history").exchange();
//
//        // Assert
//        response.expectStatus().isOk();
//    }
}

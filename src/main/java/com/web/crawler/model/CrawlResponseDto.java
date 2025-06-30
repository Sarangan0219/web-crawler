package com.web.crawler.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlResponseDto {

    private String crawlId;

    private CrawlStatus status;

    private String message;

    private LocalDateTime timestamp;
}

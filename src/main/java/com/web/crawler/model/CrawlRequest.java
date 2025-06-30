package com.web.crawler.model;

import com.web.crawler.model.CrawlType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;


import java.util.List;

@Data
public class CrawlRequest {
    @NotNull(message = "URLs list cannot be null")
    @NotEmpty(message = "URLs list cannot be empty")
    private List<String> urls;

    private CrawlType strategy = CrawlType.SINGLE_DOMAIN;

    @Min(value = 1, message = "maxPages must be at least 1")
    @Max(value = 1000, message = "maxPages cannot exceed 1000")
    private Integer maxPages = 100;

    @Min(value = 1, message = "maxDepth must be at least 1")
    @Max(value = 50, message = "maxDepth cannot exceed 10")
    private Integer maxDepth = 5;
}
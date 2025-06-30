package com.web.crawler.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum CrawlType {
    SINGLE_DOMAIN("SINGLE_DOMAIN"),
    MULTI_DOMAIN("MULTI_DOMAIN");

    private final String value;

    CrawlType(String value) {
        this.value = value;
    }

    @JsonCreator
    public static CrawlType fromValue(String value) {
        for (CrawlType type : CrawlType.values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown CrawlType: " + value);
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}

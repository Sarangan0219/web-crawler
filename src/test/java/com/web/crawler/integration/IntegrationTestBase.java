package com.web.crawler.integration;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.springframework.test.web.reactive.server.WebTestClient;

public abstract class IntegrationTestBase {

    protected final WebTestClient httpClient;

    IntegrationTestBase() {
        httpClient =
                WebTestClient.bindToServer()
                        .baseUrl(
                                Optional.ofNullable(System.getProperty("baseUrl")).orElse("http://localhost:8081"))
                        .build();
    }
}
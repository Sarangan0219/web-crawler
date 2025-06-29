# 🕷️ Web Crawler Service

A concurrent, domain-restricted web crawler written in Java using Spring Boot. It accepts a list of starting URLs and crawls all internal links within the same domain. The service is designed to be scalable, extensible, and production-ready in structure.

---

## 📌 Features

- ✅ Supports **SINGLE_DOMAIN** and **MULTI_DOMAIN** crawl strategies
- ✅ Validates and normalizes incoming URLs before crawling
- ✅ Concurrent crawling using a **configurable thread pool**
- ✅ Crawl cancellation and timeout-resilience using task management
- ✅ Modular and testable service, manager, and worker design
- ✅ Clear separation of concerns (URL validation, task execution, crawl logic)

---

## 🚀 Getting Started

### Prerequisites

- Java 17+
- Maven 3.6+
- Internet connection (for live crawling)

### Build and Run

```bash
# Build
mvn clean install

# Run
mvn spring-boot:run
````

---

## 🧠 Architecture & Design

### Overview

This application is structured around the **Manager-Worker** concurrency pattern:

* **Controller**: Accepts URLs and crawl type
* **Service**: Validates input and manages thread pool
* **CrawlManager**: Manages the crawl queue, domain scope, and visited state
* **CrawlWorker**: Fetches a URL, extracts links, and reports completion

### Thread Pool Configuration

The pool size is configurable via application properties:

```properties
crawler.thread.pool.size=10
```

This allows dynamic tuning based on system resources and crawl load.

### Crawl Strategies

| Strategy        | Description                                             |
| --------------- | ------------------------------------------------------- |
| `SINGLE_DOMAIN` | Crawl only pages under the same domain                  |
| `MULTI_DOMAIN`  | (Extensible) Strategy to support multiple domains later |

---

## ✅ URL Validation & Safety

All incoming URLs are validated before being processed using:

```java
UrlUtils.parseAndValidateUrl(url);
```

Validation checks include:

* Scheme (`http` or `https`)
* Host format
* URI syntax
* Optional protocol injection (`https://` fallback)

---

## 🔁 Concurrency Model

* `ExecutorService` manages asynchronous task submissions
* Each crawl task runs in isolation
* Pending tasks are tracked via an `AtomicInteger`
* The crawler shuts down gracefully once all tasks complete

---

## 📁 Sample Request

### Endpoint

```http
POST /api/v1/crawlers?strategy=SINGLE_DOMAIN
Content-Type: application/json
```

### Request Body

```json
[
  "https://example.com",
  "https://another.com"
]
```

---

## 🔄 Trade-offs

| Area                        | Decision                                                                  |
| --------------------------- | ------------------------------------------------------------------------- |
| **Threading Model**         | Used fixed thread pool for predictability and bounded resource usage      |
| **Persistence**             | Omitted for simplicity; can be plugged in using a repository or Redis     |
| **Timeouts & Cancellation** | ExecutorService allows future enhancement using `Future.get(timeout)`     |
| **Link Extraction**         | Done using `HtmlParserUtil.extractLinks` (can be upgraded with JS engine) |
| **Sitemap/Output**          | Output is logged for now; can be persisted or exposed via API             |

---

## 🧪 Testing Strategy

Although not included in this snippet, the architecture supports:

* Unit tests for `UrlUtils`, `CrawlWorker`, `SingleDomainCrawlManager`
* Integration tests via `MockMvc` for the REST controller
* Concurrency tests for thread safety and queue consistency

Use JUnit 5 and Mockito for mocking dependencies and asserting behavior.

## 🧪 Design Decisons

* Thread Pool Size (10 threads): Chosen based on the I/O-bound nature of web crawling, 
where threads spend most time waiting on network responses rather than consuming CPU; unlike CPU-intensive tasks, 
I/O-heavy workloads benefit from more threads (typically 2–5× cores), so 10 provides a balanced default for concurrency without overwhelming system resources.

---

## 🔧 Extensibility Ideas

* [ ] Add **politeness delay** or robots.txt support
* [ ] Plug in a **persistence layer** (e.g., storing results in MongoDB)
* [ ] Introduce rate limiting or IP throttling
* [ ] Provide **Web UI** or dashboard for crawl progress
* [ ] Improve crawl scope (e.g., with regex filters or depth)

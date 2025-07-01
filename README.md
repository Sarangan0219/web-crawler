# Web Crawler Service

A concurrent, domain-restricted web crawler written in Java using Spring Boot. 
It accepts a list of starting URLs and crawls all internal links within the same domain. 
The service is designed to be scalable, extensible, and production-ready with proper layered architecture.

##  Features

* ✅ **Multiple Input Methods**: JSON API requests and file uploads (txt, csv)
* ✅ **Async Crawling**: Non-blocking REST API with real-time status monitoring

##  Getting Started

### Prerequisites
* Java 17+
* Maven 3.6+

### Build and Run

```bash
# Build
mvn clean install

# Run
mvn spring-boot:run
```

The service will start on `http://localhost:8080`

## 🔗 API Endpoints

### 1. Start Crawl (JSON)
```http
POST /api/v1/crawlers
Content-Type: application/json

{
  "urls": ["https://example.com", "https://monzo.com"],
  "strategy": "SINGLE_DOMAIN",
  "maxPages": 50,
  "maxDepth": 3
}
```

### 2. Start Crawl (File Upload)
```http
POST /api/v1/crawlers/upload
Content-Type: multipart/form-data

file: urls.txt (one URL per line)
strategy: SINGLE_DOMAIN
maxPages: 50
maxDepth: 3
```

### 3. Check Crawl Status
```http
GET /api/v1/crawlers/{crawlId}/status
```

### 4. Stop Crawl
```http
POST /api/v1/crawlers/{crawlId}/stop
```

### 5. Get Crawl History
```http
GET /api/v1/crawlers/history?page=0&size=10&status=COMPLETED
```

## 🧠 Architecture & Design

### Layered Architecture
```
┌─────────────────┐
│   Controller    │ ← REST API Layer
├─────────────────┤
│    Service      │ ← Business Logic
├─────────────────┤
│   Repository    │ ← Data Access Layer
├─────────────────┤
│    Manager      │ ← Crawl Coordination
├─────────────────┤
│    Worker       │ ← URL Processing
└─────────────────┘
```

### Core Components

#### **Repository Pattern**
- `CrawlRepository` interface for data abstraction
- `InMemoryCrawlRepository` implementation (easily replaceable with DB)
- Follows SOLID principles for future extensibility

#### **Manager-Worker Pattern**
- **CrawlManager**: Manages crawl queue, domain scope, and task coordination
- **CrawlWorker**: Fetches URLs, extracts links, and reports completion


##  Configuration

### File Upload Support
Supports multiple file formats:
- **Plain Text**: One URL per line
- **CSV**: URLs in first column
- **Maximum File Size**: Configurable via Spring Boot properties

### Rate Limiting
- **Default Delay**: 1000ms between requests
- **Respectful Crawling**: Prevents server overwhelming
- **Configurable**: Can be adjusted per crawl strategy

## Sample Files

### URLs Text File (urls.txt)
```
https://example.com
https://another-domain.com
https://third-site.org
```

### URLs CSV File (urls.csv)
```csv
url,description
https://example.com,Main site
https://blog.example.com,Blog
```

## Crawl Flexibility

To give users fine-grained control over the crawling behavior, the service supports several key parameters:

### Core Parameters

**`maxPages`**: Limits the total number of pages the crawler will visit for each job.
- This helps prevent runaway crawls and keeps resource usage predictable.

**`maxDepth`**: Controls the link traversal depth from the starting URLs (i.e., how many levels deep to follow links).
- This avoids excessive recursion into deep site structures.

**`strategy`**: Defines the crawling scope and domain restrictions.
- `"SINGLE_DOMAIN"` - Restricts crawling to the exact same domain as the starting URL
- When you start with `https://monzo.com/`, it crawls all pages on the `monzo.com` website only
- External links (e.g., to `facebook.com` or `community.monzo.com`) are filtered out and not followed

# Web Crawler Implementation: Detailed Analysis

## Overview

This is a sophisticated multi-threaded web crawler implementation designed to crawl websites within specified domains. The system uses concurrent programming techniques to efficiently crawl multiple pages simultaneously while avoiding race conditions and managing resources effectively.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         CrawlService                             │
│  • Entry point for crawl operations                              │
│  • Manages crawl lifecycle                                       │
│  • Stores active crawls in ConcurrentHashMap                     │
└─────────────────────┬───────────────────────────────────────────┘
                      │
                      │ Creates via Factory
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                  SingleDomainCrawlManager                        │
│  • Manages crawl for specific domain(s)                          │
│  • Controls worker threads                                       │
│  • Maintains crawl state                                         │
│  • Handles URL queue and visited URLs                           │
└─────────────────────┬───────────────────────────────────────────┘
                      │
                      │ Submits tasks to
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                    SHARED_EXECUTOR (ThreadPool)                  │
│  • Shared across all crawl managers                              │
│  • Dynamic sizing based on CPU cores                             │
│  • CallerRunsPolicy for backpressure                            │
└─────────────────────┬───────────────────────────────────────────┘
                      │
                      │ Executes
                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                        CrawlWorker                               │
│  • Fetches and parses individual pages                          │
│  • Extracts links                                                │
│  • Reports results back to manager                               │
└─────────────────────────────────────────────────────────────────┘
```

## Key Components Analysis

### 1. CrawlService (Orchestrator)

The `CrawlService` acts as the main orchestrator:

```
┌──────────────────────────────────────────────────────┐
│                   CrawlService                        │
├──────────────────────────────────────────────────────┤
│ Fields:                                               │
│ • activeCrawls: ConcurrentHashMap<String,CrawlManager>│
│ • crawlManagerFactory                                 │
│ • repository                                          │
├──────────────────────────────────────────────────────┤
│ Key Methods:                                          │
│ • startCrawlAsync() → Returns crawlId                 │
│ • getCrawlStatusDto() → Returns current status        │
│ • stopCrawl() → Stops active crawl                    │
└──────────────────────────────────────────────────────┘
```

**Asynchronous Execution Flow:**
```
User Request
    │
    ▼
startCrawlAsync()
    │
    ├─► Generate UUID crawlId
    │
    ├─► Create CrawlManager
    │
    ├─► Store in activeCrawls Map
    │
    ├─► Save initial CrawlResult to DB
    │
    └─► CompletableFuture.runAsync()
            │
            ├─► manager.start() [Blocking call]
            │
            ├─► Update status to COMPLETED/FAILED
            │
            └─► Remove from activeCrawls
```

### 2. SingleDomainCrawlManager (Core Engine)

This is the heart of the crawling logic:

```
┌─────────────────────────────────────────────────────────────┐
│              SingleDomainCrawlManager                        │
├─────────────────────────────────────────────────────────────┤
│ Thread-Safe Collections:                                     │
│ • visitedUrls: Set<String> (ConcurrentHashMap.newKeySet)    │
│ • urlQueue: BlockingQueue<UrlDepthPair>                     │
│ • crawlResults: ConcurrentHashMap<String, List<String>>     │
│                                                              │
│ Atomic Counters:                                             │
│ • pendingTasks: AtomicInteger                               │
│ • processedPages: AtomicInteger                             │
│                                                              │
│ Control Flags:                                               │
│ • running: AtomicBoolean                                     │
│ • shouldStop: AtomicBoolean                                  │
│ • crawlCompleted: AtomicBoolean                              │
│                                                              │
│ Synchronization:                                             │
│ • completionLatch: CountDownLatch(1)                        │
└─────────────────────────────────────────────────────────────┘
```

### 3. Thread Pool Architecture

```
┌────────────────────────────────────────────────────────────────┐
│                    SHARED_EXECUTOR Configuration                │
├────────────────────────────────────────────────────────────────┤
│ • Core Pool Size: max(2, availableProcessors/2)                │
│ • Max Pool Size: min(20, availableProcessors*2)                │
│ • Keep Alive: 60 seconds                                       │
│ • Queue: LinkedBlockingQueue(1000)                              │
│ • Rejection Policy: CallerRunsPolicy                           │
│ • Daemon Threads: true                                          │
└────────────────────────────────────────────────────────────────┘

Example on 8-core machine:
• Core threads: 4
• Max threads: 16
• Can handle bursts up to 16 concurrent crawls
```

## Crawl Execution Flow

### Main Crawl Loop

```
┌─────────────────┐
│   start()       │
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│ Set running = true                       │
│ Record startTime                         │
└────────┬────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│ CompletableFuture.runAsync(executeCrawl)│
│ with timeout (crawlTimeoutMinutes)       │
└────────┬────────────────────────────────┘
         │
         ▼
    ┌────────────────────────┐
    │   executeCrawl()       │
    └───────┬────────────────┘
            │
            ▼
    ┌───────────────────────────────────┐
    │  Main Loop (while conditions)      │
    │  • running == true                 │
    │  • shouldStop == false             │
    │  • processedPages < maxPages       │
    └───────┬───────────────────────────┘
            │
            ├─► Poll URL from queue (2s timeout)
            │       │
            │       ├─► URL found & depth OK
            │       │      │
            │       │      ├─► Increment pendingTasks
            │       │      │
            │       │      └─► Submit CrawlWorker to executor
            │       │
            │       └─► No URL & no pending tasks
            │              │
            │              └─► Break (crawl complete)
            │
            └─► Check if maxPages reached
                   │
                   └─► Break if limit hit
```

### Worker Task Execution

```
┌──────────────────┐
│   CrawlWorker    │
└────────┬─────────┘
         │
         ▼
┌─────────────────────────────────┐
│ Extract links from URL          │
│ (HtmlParserUtil.extractLinks)   │
└────────┬────────────────────────┘
         │
         ▼
┌─────────────────────────────────┐
│ Filter same-domain links        │
└────────┬────────────────────────┘
         │
         ▼
┌─────────────────────────────────┐
│ manager.recordCrawlResult()     │
│ • Store results                 │
│ • Increment processedPages      │
│ • Log progress                  │
└────────┬────────────────────────┘
         │
         ▼
┌─────────────────────────────────┐
│ Enqueue new URLs for crawling   │
│ (with depth + 1)                │
└────────┬────────────────────────┘
         │
         ▼
┌─────────────────────────────────┐
│ manager.taskCompleted()         │
│ (Decrement pendingTasks)        │
└─────────────────────────────────┘
```

## Concurrency Control & Race Condition Prevention

### 1. URL Deduplication

```
Thread 1                    Thread 2
    │                          │
    ▼                          ▼
visitedUrls.add(url1)    visitedUrls.add(url2)
    │                          │
    └──────────┬───────────────┘
               │
               ▼
    ConcurrentHashMap.newKeySet()
    (Thread-safe Set implementation)
    
• add() returns false if already present
• Atomic operation - no race condition
• Only one thread can add a URL successfully
```

### 2. Task Counter Management

```
┌─────────────────────────────────────────────┐
│           pendingTasks (AtomicInteger)       │
├─────────────────────────────────────────────┤
│                                             │
│  Thread 1: incrementAndGet() ───┐           │
│                                 ▼           │
│                            [4 → 5]          │
│                                 ▲           │
│  Thread 2: decrementAndGet() ───┘           │
│                                             │
│  • All operations are atomic                │
│  • No lost updates                          │
│  • Accurate pending count                   │
└─────────────────────────────────────────────┘
```

### 3. Queue Operations

```
┌──────────────────────────────────────────────┐
│         BlockingQueue<UrlDepthPair>          │
├──────────────────────────────────────────────┤
│                                              │
│  Producer Threads          Consumer Thread   │
│  ================          ===============   │
│                                              │
│  T1: offer(url1) ──┐                         │
│                    ├──► [url1,url2,url3]     │
│  T2: offer(url2) ──┘            │            │
│                                 ▼            │
│                            poll(2,SECONDS)   │
│                                              │
│  • Thread-safe by design                     │
│  • Blocks when empty (with timeout)          │
│  • Bounded capacity prevents memory issues   │
└──────────────────────────────────────────────┘
```

### 4. Results Storage

```
Multiple Workers Writing Results:

Worker 1: crawlResults.put(urlA, [...])
Worker 2: crawlResults.put(urlB, [...])
Worker 3: crawlResults.put(urlC, [...])
           │         │         │
           └────┬────┴─────────┘
                ▼
    ConcurrentHashMap<String, List<String>>
    
• Each URL maps to its own list
• No conflicts between different URLs
• Thread-safe put operations
```

## Synchronization Mechanisms

### 1. CompletableFuture with Timeout

```
┌─────────────────────────────────────────────┐
│         Main Thread                          │
│             │                                │
│             ▼                                │
│   CompletableFuture.runAsync()               │
│             │                                │
│             ├──► Timeout (N minutes)         │
│             │         │                      │
│             │         ▼                      │
│             │    shouldStop.set(true)        │
│             │                                │
│             └──► Normal completion           │
│                      │                       │
│                      ▼                       │
│                 All tasks done               │
└─────────────────────────────────────────────┘
```

### 2. Completion Latch Pattern

```
stop() method               executeCrawl()
     │                           │
     ▼                           ▼
shouldStop.set(true)        Main loop
     │                           │
     ▼                           ▼
await(completionLatch)      completionLatch.countDown()
     │                           │
     └───────── Waits ───────────┘
     
• Ensures graceful shutdown
• stop() waits for crawl to finish
• Prevents resource leaks
```

## Resource Management

### 1. Bounded Resources

```
┌─────────────────────────────────────────────┐
│           Resource Limits                    │
├─────────────────────────────────────────────┤
│ • URL Queue: maxPages * 2                   │
│ • Thread Pool Queue: 1000 tasks              │
│ • Max Pages: min(user_input, 1000)          │
│ • Max Depth: min(user_input, 10)            │
│ • Link Storage: max 100 per page            │
└─────────────────────────────────────────────┘
```

### 2. Memory Management

```
Page Processing:
    │
    ├─► Extract links
    │
    ├─► Filter (same domain only)
    │
    ├─► Limit to 100 links ───────► Prevents memory bloat
    │
    └─► Store in results map
```

### 3. Thread Pool Lifecycle

```
Application Start
    │
    ▼
Create SHARED_EXECUTOR (static)
    │
    ├─► Register shutdown hook
    │
    ├─► Multiple crawls share pool
    │
    └─► Application Shutdown
            │
            ├─► executor.shutdown()
            │
            ├─► Wait 30 seconds
            │
            └─► Force shutdown if needed
```

## Error Handling & Recovery

### 1. Worker Error Handling

```
CrawlWorker.run()
    │
    ├─► try {
    │      • Extract links
    │      • Record results
    │   }
    │
    ├─► catch (Exception e) {
    │      • Log error
    │      • Continue (don't crash)
    │   }
    │
    └─► finally {
           • Always call taskCompleted()
           • Ensures accurate pending count
       }
```

### 2. Timeout Handling

```
Crawl exceeds time limit
    │
    ▼
TimeoutException caught
    │
    ├─► shouldStop.set(true)
    │
    ├─► Wait for pending tasks
    │
    └─► Force completion
```

## Performance Optimizations

### 1. Domain Filtering

```
Before optimization:          After optimization:
Extract all links            Extract all links
    │                            │
    ▼                            ▼
Enqueue all links            Filter same-domain only
    │                            │
    ▼                            ▼
Check domain in queue        Enqueue filtered links

• Reduces queue pressure
• Avoids unnecessary checks
• Improves throughput
```

### 2. Batch Logging

```
Instead of:                  Optimized:
Log each link               Log first 15 links
(Could be 100s)             Show count of remaining

• Reduces I/O overhead
• Maintains visibility
• Improves performance
```

## Summary

This crawler implementation demonstrates sophisticated concurrent programming:

1. **Thread Safety**: Uses concurrent collections and atomic operations throughout
2. **Resource Management**: Bounded queues, limited threads, memory caps
3. **Graceful Shutdown**: Proper lifecycle management with latches and timeouts
4. **Error Recovery**: Continues operation despite individual failures
5. **Performance**: Shared thread pool, efficient filtering, batch operations

The design successfully handles multiple concurrent crawls while preventing race conditions and resource exhaustion.